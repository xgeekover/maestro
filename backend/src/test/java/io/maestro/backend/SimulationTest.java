package io.maestro.backend;

import io.maestro.backend.domain.ScriptEntity;
import io.maestro.backend.domain.ScriptService;
import io.maestro.backend.flow.FlowDeployment;
import io.maestro.backend.flow.FlowEntity;
import io.maestro.backend.flow.FlowModel.FlowEdge;
import io.maestro.backend.flow.FlowModel.FlowGraph;
import io.maestro.backend.flow.FlowModel.FlowNode;
import io.maestro.backend.flow.FlowModel.NodeKind;
import io.maestro.backend.flow.FlowRuntime;
import io.maestro.backend.flow.FlowService;
import io.maestro.backend.process.RunConfig;
import io.maestro.backend.process.RunInfo;
import io.maestro.backend.process.RunStatus;
import io.maestro.backend.process.Supervisor;
import io.maestro.backend.telemetry.LogEntry;
import io.maestro.backend.telemetry.MetricSnapshot;
import io.maestro.backend.telemetry.TelemetryStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Phase 9 시뮬레이션 하니스 — 결함 주입 후 시스템의 실제 보장을 검증:
 * <ul>
 *   <li>tick 예외 → 격리(타 프로세스 무영향) + 대시보드(메트릭) 정확성</li>
 *   <li>kill -9(돌연사) → 감시자 재시작 + 격리</li>
 *   <li>무한루프(행) → tick 워치독이 바운드 종료(영원히 멈추지 않음) + 격리</li>
 *   <li>OOM → tick 에러로 봉쇄(catch) + 격리</li>
 *   <li>플로우 처리량 측정</li>
 * </ul>
 * 결과는 {@code SIM:} 라인으로 출력되어 리포트(docs/09)에 수치로 반영된다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "maestro.grpc.port=0",
                "maestro.restart.base-delay-ms=300",
                "maestro.restart.max-delay-ms=2000",
                "maestro.restart.watchdog-period-ms=200",
                "maestro.restart.max-attempts=5",
                "maestro.runner.stop-grace-ms=1500"
        })
class SimulationTest {

    private static final String HEALTHY = """
            import io.maestro.sdk.Script;
            public class Healthy extends Script {
                @Override public void onTick() {
                    ctx.state().put("n", ctx.state().get("n", Integer.class).orElse(0) + 1);
                }
            }
            """;

    private static final String TICK_FAULT = """
            import io.maestro.sdk.Script;
            public class Faulty extends Script {
                @Override public void onTick() { throw new RuntimeException("주입된 tick 결함"); }
            }
            """;

    private static final String HANG = """
            import io.maestro.sdk.Script;
            public class Hang extends Script {
                @Override public void onTick() { while (true) { Thread.onSpinWait(); } }
            }
            """;

    private static final String OOM = """
            import io.maestro.sdk.Script;
            public class Oom extends Script {
                @Override public void onTick() {
                    int[] huge = new int[Integer.MAX_VALUE / 2]; // -Xmx 초과 → OOM(catch됨)
                    if (huge.length < 0) ctx.log().info("unreachable");
                }
            }
            """;

    private static final String PRODUCER = """
            import io.maestro.sdk.Script;
            public class Producer extends Script {
                private int n = 0;
                @Override public void onTick() { ctx.emit("out", ++n); }
            }
            """;

    private static final String CONSUMER = """
            import io.maestro.sdk.Script;
            public class Consumer extends Script {
                @Override public void onStart() { ctx.onMessage("in", m -> ctx.log().info("got " + m)); }
            }
            """;

    @Autowired private Supervisor supervisor;
    @Autowired private TelemetryStore telemetry;
    @Autowired private ScriptService scripts;
    @Autowired private FlowService flows;
    @Autowired private FlowRuntime runtime;

    /** 무제한 tick, 타임아웃 없음. */
    private static RunConfig basic() {
        return new RunConfig(100, Map.of(), false, 0, 0, 0, 1000, 0);
    }

    @Test
    void tickFaultIsolatedAndDashboardAccurate() throws Exception {
        List<RunInfo> healthy = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            healthy.add(supervisor.startRunWithSource("healthy-" + i, HEALTHY, basic()));
        }
        RunInfo faulty = supervisor.startRunWithSource("faulty", TICK_FAULT, basic());

        awaitUntil(Duration.ofSeconds(40), "전원 RUNNING",
                () -> healthy.stream().allMatch(r -> r.status() == RunStatus.RUNNING)
                        && faulty.status() == RunStatus.RUNNING);
        awaitUntil(Duration.ofSeconds(20), "결함 errorCount>0 + 건강 메트릭",
                () -> latest(faulty).map(m -> m.errorCount() > 0).orElse(false)
                        && healthy.stream().allMatch(r -> latest(r).isPresent()));

        // 격리: 건강한 프로세스의 tick이 계속 진행
        awaitTickAdvance(healthy.get(0), 3, Duration.ofSeconds(15));
        assertTrue(healthy.stream().allMatch(r -> r.status() == RunStatus.RUNNING), "건강 RUNNING 유지");
        // 대시보드(메트릭) 정확성
        assertTrue(latest(faulty).get().errorCount() > 0, "결함 errorCount>0");
        assertEquals(0, latest(healthy.get(0)).get().errorCount(), "건강 errorCount==0");

        System.out.printf("SIM tickFault: healthy=%d faultyErrors=%d isolated=true%n",
                healthy.size(), latest(faulty).get().errorCount());
        stopAll(faulty, healthy);
    }

    @Test
    void killNineRestartsAndIsolates() throws Exception {
        List<RunInfo> runs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            runs.add(supervisor.startRunWithSource("k-" + i, HEALTHY, basic()));
        }
        awaitUntil(Duration.ofSeconds(40), "전원 RUNNING + 메트릭",
                () -> runs.stream().allMatch(r -> r.status() == RunStatus.RUNNING && latest(r).isPresent()));

        RunInfo victim = runs.get(0);
        RunInfo survivor = runs.get(1);
        long before = tick(survivor);
        victim.process().destroyForcibly();
        victim.process().waitFor();

        // 격리: 생존 프로세스 tick 진행
        awaitTickAdvance(survivor, 3, Duration.ofSeconds(15));
        // 재시작: 감시자가 돌연사 감지 → 재시작
        awaitUntil(Duration.ofSeconds(40), "victim 재시작",
                () -> victim.restartCount() >= 1 && victim.status() == RunStatus.RUNNING);

        System.out.printf("SIM killNine: victimRestart=%d survivorTickDelta=%d isolated=true%n",
                victim.restartCount(), tick(survivor) - before);
        runs.forEach(r -> supervisor.stopRun(r.runId()));
    }

    @Test
    void infiniteLoopHangIsBoundedAndIsolated() throws Exception {
        // tickTimeout 800ms → 행 감지 후 엔진 자가 종료(바운드). 영원히 멈추지 않음.
        RunConfig hangCfg = new RunConfig(200, Map.of(), false, 0, 800, 0, 800, 0);
        RunInfo hang = supervisor.startRunWithSource("hang", HANG, hangCfg);
        RunInfo healthy = supervisor.startRunWithSource("healthy", HEALTHY, basic());

        awaitUntil(Duration.ofSeconds(40), "healthy RUNNING + 메트릭",
                () -> healthy.status() == RunStatus.RUNNING && latest(healthy).isPresent());

        // 행은 바운드 종료: ERROR로 전이하거나(자가 종료) 돌연사로 재시작됨 — 무한 RUNNING 금지
        awaitUntil(Duration.ofSeconds(30), "행 바운드 종료/처리",
                () -> hang.status() == RunStatus.ERROR || hang.restartCount() >= 1
                        || hang.status() == RunStatus.STOPPED);
        // 격리: 건강한 프로세스 영향 없음
        awaitTickAdvance(healthy, 3, Duration.ofSeconds(15));
        assertEquals(RunStatus.RUNNING, healthy.status(), "healthy 격리");

        System.out.printf("SIM hang: status=%s restart=%d healthyIsolated=true%n",
                hang.status(), hang.restartCount());
        stopAll(hang, List.of(healthy));
    }

    @Test
    void oomContainedAndIsolated() throws Exception {
        // -Xmx 32MB + CONTINUE → OOM이 tick 에러로 catch되어 봉쇄. 프로세스 생존, 타 프로세스 격리.
        RunConfig oomCfg = new RunConfig(200, Map.of(), false, 32L * 1024 * 1024, 0, 0, 1000, 0);
        RunInfo oom = supervisor.startRunWithSource("oom", OOM, oomCfg);
        RunInfo healthy = supervisor.startRunWithSource("healthy", HEALTHY, basic());

        awaitUntil(Duration.ofSeconds(40), "양쪽 RUNNING + healthy 메트릭",
                () -> oom.status() == RunStatus.RUNNING
                        && healthy.status() == RunStatus.RUNNING && latest(healthy).isPresent());

        // OOM이 tick 에러로 봉쇄됨(errorCount 증가)
        awaitUntil(Duration.ofSeconds(20), "OOM errorCount>0",
                () -> latest(oom).map(m -> m.errorCount() > 0).orElse(false));
        // 격리: 건강한 프로세스 정상 진행
        awaitTickAdvance(healthy, 3, Duration.ofSeconds(15));
        assertEquals(RunStatus.RUNNING, healthy.status(), "healthy 격리");

        System.out.printf("SIM oom: oomErrors=%d healthyIsolated=true%n",
                latest(oom).map(MetricSnapshot::errorCount).orElse(0L));
        stopAll(oom, List.of(healthy));
    }

    @Test
    void flowThroughputMeasured() throws Exception {
        ScriptEntity producer = scripts.create("sim-producer", PRODUCER);
        ScriptEntity consumer = scripts.create("sim-consumer", CONSUMER);
        FlowGraph graph = new FlowGraph(
                List.of(
                        new FlowNode("p", NodeKind.SCRIPT, producer.getId(), Map.of(), 20L),
                        new FlowNode("c", NodeKind.SCRIPT, consumer.getId(), Map.of(), 1000L)),
                List.of(new FlowEdge("p", "out", "c", "in")));
        FlowEntity flow = flows.create("sim-throughput", graph);
        FlowDeployment dep = runtime.deploy(flow.getId());
        RunInfo consumerRun = dep.nodeRuns().get("c");

        awaitUntil(Duration.ofSeconds(40), "consumer 수신 시작", () -> delivered(consumerRun) >= 1);

        long start = System.nanoTime();
        long base = delivered(consumerRun);
        Thread.sleep(2500);
        long delta = delivered(consumerRun) - base;
        double seconds = (System.nanoTime() - start) / 1e9;

        assertTrue(delta > 0, "메시지가 라우팅되어야 함");
        System.out.printf("SIM throughput: delivered=%d over %.1fs = %.0f msg/s (drop=%d)%n",
                delta, seconds, delta / seconds, dep.droppedCount());
        runtime.stop(flow.getId());
    }

    // ---- helpers ----

    private long tick(RunInfo run) {
        return latest(run).map(MetricSnapshot::tickCount).orElse(0L);
    }

    private void awaitTickAdvance(RunInfo run, long by, Duration timeout) throws InterruptedException {
        long base = tick(run);
        awaitUntil(timeout, "tick 진행(+" + by + ")", () -> tick(run) >= base + by);
    }

    private long delivered(RunInfo run) {
        return telemetry.logs(run.runId()).stream()
                .map(LogEntry::message).filter(m -> m.contains("got ")).count();
    }

    private Optional<MetricSnapshot> latest(RunInfo run) {
        return telemetry.latest(run.runId());
    }

    private void stopAll(RunInfo first, List<RunInfo> rest) {
        supervisor.stopRun(first.runId());
        rest.forEach(r -> supervisor.stopRun(r.runId()));
    }

    private static void awaitUntil(Duration timeout, String desc, BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                if (condition.getAsBoolean()) {
                    return;
                }
            } catch (RuntimeException ignored) {
                // 폴링 재시도
            }
            Thread.sleep(150);
        }
        fail("타임아웃 — 조건 미충족: " + desc);
    }
}
