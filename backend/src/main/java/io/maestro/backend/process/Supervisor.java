package io.maestro.backend.process;

import io.grpc.stub.StreamObserver;
import io.maestro.backend.config.MaestroProperties;
import io.maestro.backend.domain.ScriptEntity;
import io.maestro.backend.domain.ScriptRepository;
import io.maestro.protocol.v1.BackendMessage;
import io.maestro.protocol.v1.ResourceLimits;
import io.maestro.protocol.v1.StartCommand;
import io.maestro.protocol.v1.StopCommand;
import io.maestro.protocol.v1.TickExceptionPolicy;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 프로세스 기동/감시/재시작 오케스트레이션 (FR-4/FR-5, NFR-4).
 *
 * <ul>
 *   <li>startRun: RunInfo 생성 → 러너 프로세스 기동.</li>
 *   <li>onRunnerConnected: Hello 수신 후 StartCommand 송신.</li>
 *   <li>watchdog: 프로세스 사망 감지 → exponential backoff 재시작(한도 초과 시 ERROR 격리).</li>
 *   <li>stopRun: graceful StopCommand → grace 후 강제 종료.</li>
 * </ul>
 */
@Component
public class Supervisor {

    private static final Logger log = LoggerFactory.getLogger(Supervisor.class);

    private final RunRegistry registry;
    private final ProcessManager processManager;
    private final ScriptRepository scripts;
    private final MaestroProperties props;
    private final io.maestro.backend.telemetry.TelemetryStore telemetry;
    private ScheduledExecutorService scheduler;

    public Supervisor(RunRegistry registry, ProcessManager processManager,
                      ScriptRepository scripts, MaestroProperties props,
                      io.maestro.backend.telemetry.TelemetryStore telemetry) {
        this.registry = registry;
        this.processManager = processManager;
        this.scripts = scripts;
        this.props = props;
        this.telemetry = telemetry;
    }

    @PostConstruct
    public void start() {
        scheduler = Executors.newScheduledThreadPool(3, r -> {
            Thread t = new Thread(r, "maestro-supervisor");
            t.setDaemon(true);
            return t;
        });
        long period = props.getRestart().getWatchdogPeriodMs();
        scheduler.scheduleAtFixedRate(this::watchdogCheck, period, period, TimeUnit.MILLISECONDS);
        long sweep = props.getLimits().getEvictionSweepMs();
        scheduler.scheduleAtFixedRate(this::evictStale, sweep, sweep, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        for (RunInfo run : registry.all()) {
            processManager.kill(run.process());
        }
    }

    /** 영속 스크립트로 실행 시작. */
    public RunInfo startRun(String scriptId, RunConfig config) {
        ScriptEntity script = scripts.findById(scriptId)
                .orElseThrow(() -> new io.maestro.backend.support.NotFoundException("스크립트 없음: " + scriptId));
        return launch(scriptId, script.getName(), script.getSource(), config, null, null);
    }

    /** 원시 소스로 실행 시작(테스트/임시). */
    public RunInfo startRunWithSource(String name, String source, RunConfig config) {
        return launch("inline:" + name, name, source, config, null, null);
    }

    /** 플로우 노드로 실행 시작(flowId/nodeId 태깅 → emit 라우팅 대상). */
    public RunInfo startNode(String scriptId, String name, String source, RunConfig config,
                             String flowId, String nodeId) {
        return launch(scriptId, name, source, config, flowId, nodeId);
    }

    private RunInfo launch(String scriptId, String name, String source, RunConfig config,
                           String flowId, String nodeId) {
        String runId = UUID.randomUUID().toString();
        RunInfo run = new RunInfo(runId, scriptId, name, source, config);
        run.setRunnerId(UUID.randomUUID().toString());
        run.setToken(UUID.randomUUID().toString());
        run.setStartedAt(Instant.now());
        run.setFlowId(flowId);
        run.setNodeId(nodeId);
        registry.register(run);
        spawn(run);
        return run;
    }

    private void spawn(RunInfo run) {
        try {
            Process p = processManager.spawn(run);
            run.setProcess(p);
            run.setStatus(RunStatus.PENDING);
            run.touchTelemetry();
        } catch (Exception e) {
            run.setStatus(RunStatus.ERROR);
            run.setLastError("기동 실패: " + e.getMessage());
            log.error("러너 기동 실패 runId={}", run.runId(), e);
        }
    }

    /** 게이트웨이가 Hello 검증 후 호출. StartCommand를 보낸다. */
    public void onRunnerConnected(RunInfo run, StreamObserver<BackendMessage> stream) {
        run.setCommandStream(stream);
        run.touchTelemetry();
        run.setStatus(RunStatus.STARTING);
        sendStart(run);
    }

    private void sendStart(RunInfo run) {
        RunConfig c = run.config();
        StartCommand start = StartCommand.newBuilder()
                .setCommandId(UUID.randomUUID().toString())
                .setSource(run.source())
                .setSourceHash(Integer.toHexString(run.source().hashCode()))
                .setTickPeriodMs(c.tickPeriodMs())
                .putAllParams(c.params())
                .setTickPolicy(c.stopOnError() ? TickExceptionPolicy.STOP : TickExceptionPolicy.CONTINUE)
                .setLimits(ResourceLimits.newBuilder()
                        .setMaxHeapBytes(c.maxHeapBytes())
                        .setTickTimeoutMs(c.tickTimeoutMs())
                        .setOnstartTimeoutMs(c.onStartTimeoutMs())
                        .setOnendTimeoutMs(c.onEndTimeoutMs())
                        .setErrorThreshold(c.errorThreshold()))
                .build();
        try {
            run.sendCommand(BackendMessage.newBuilder().setStart(start).build());
        } catch (RuntimeException e) {
            log.warn("StartCommand 송신 실패 runId={}: {}", run.runId(), e.toString());
        }
    }

    public void stopRun(String runId) {
        RunInfo run = registry.byRunId(runId);
        if (run == null || run.status().isTerminal()) {
            return;
        }
        run.setUserStopped(true);
        run.setStatus(RunStatus.STOPPING);
        try {
            run.sendCommand(BackendMessage.newBuilder()
                    .setStop(StopCommand.newBuilder()
                            .setCommandId(UUID.randomUUID().toString())
                            .setGracePeriodMs(props.getRunner().getStopGraceMs()))
                    .build());
        } catch (RuntimeException ignored) {
            // 스트림 끊김 — 아래 강제 종료로 처리
        }
        scheduler.schedule(() -> forceKill(run), props.getRunner().getStopGraceMs(), TimeUnit.MILLISECONDS);
    }

    private void forceKill(RunInfo run) {
        Process p = run.process();
        if (p != null && p.isAlive()) {
            processManager.kill(p);
        }
        run.setStatus(RunStatus.STOPPED);
    }

    // ---- 워치독 ----

    private void watchdogCheck() {
        for (RunInfo run : registry.all()) {
            try {
                checkOne(run);
            } catch (RuntimeException e) {
                log.warn("워치독 점검 오류 runId={}: {}", run.runId(), e.toString());
            }
        }
    }

    private void checkOne(RunInfo run) {
        if (run.status().isTerminal()) {
            return;
        }
        Process p = run.process();
        if (p != null && !p.isAlive()) {
            handleProcessDeath(run, p.exitValue());
        }
    }

    private void handleProcessDeath(RunInfo run, int exitCode) {
        if (run.userStopped()) {
            run.setStatus(RunStatus.STOPPED);
            return;
        }
        int attempt = run.restartCount();
        if (attempt >= props.getRestart().getMaxAttempts()) {
            run.setStatus(RunStatus.ERROR);
            run.setLastError("재시작 한도 초과(crash loop), exit=" + exitCode);
            log.warn("재시작 한도 초과 — 격리 runId={}", run.runId());
            return;
        }
        long delay = backoffDelay(attempt);
        run.incrementRestart();
        run.setStatus(RunStatus.PENDING);
        run.setLastError("프로세스 사망(exit=" + exitCode + ") → 재시작 #" + (attempt + 1) + " (" + delay + "ms 후)");
        // 새 시도용 runnerId/token으로 교체
        String oldRunnerId = run.runnerId();
        registry.rebindRunner(run, oldRunnerId, UUID.randomUUID().toString());
        run.setToken(UUID.randomUUID().toString());
        run.setCommandStream(null);
        run.setProcess(null);
        log.info("프로세스 사망 감지 runId={} exit={} → {}ms 후 재시작 #{}", run.runId(), exitCode, delay, attempt + 1);
        scheduler.schedule(() -> {
            if (!run.userStopped()) {
                spawn(run);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private long backoffDelay(int attempt) {
        long base = props.getRestart().getBaseDelayMs();
        long max = props.getRestart().getMaxDelayMs();
        long delay = base * (1L << Math.min(attempt, 16));
        return Math.min(delay, max);
    }

    public Optional<RunInfo> find(String runId) {
        return Optional.ofNullable(registry.byRunId(runId));
    }

    // ---- 종료 런 회수 (QA H-4) ----

    private void evictStale() {
        try {
            long now = System.nanoTime();
            long ttlNanos = props.getLimits().getTerminalRetentionMs() * 1_000_000L;
            // 1) TTL 초과 종료 런 회수
            for (RunInfo run : registry.all()) {
                if (run.status().isTerminal()
                        && run.terminalSinceNanos() > 0
                        && now - run.terminalSinceNanos() > ttlNanos) {
                    evict(run);
                }
            }
            // 2) 상한 초과 시 오래된 종료 런부터 회수
            int cap = props.getLimits().getMaxRetainedRuns();
            List<RunInfo> terminal = registry.all().stream()
                    .filter(r -> r.status().isTerminal())
                    .sorted(Comparator.comparingLong(RunInfo::terminalSinceNanos))
                    .toList();
            for (int i = 0; i < terminal.size() - cap; i++) {
                evict(terminal.get(i));
            }
        } catch (RuntimeException e) {
            log.warn("회수 스윕 오류: {}", e.toString());
        }
    }

    private void evict(RunInfo run) {
        processManager.kill(run.process()); // 혹시 살아있으면 정리
        registry.remove(run);
        telemetry.evict(run.runId());
        log.debug("종료 런 회수 runId={}", run.runId());
    }
}
