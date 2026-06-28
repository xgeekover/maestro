package io.maestro.backend.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.maestro.backend.process.RunInfo;
import io.maestro.backend.process.RunRegistry;
import io.maestro.backend.process.RunStatus;
import io.maestro.backend.process.Supervisor;
import io.maestro.backend.telemetry.LogEntry;
import io.maestro.backend.telemetry.MetricSnapshot;
import io.maestro.backend.telemetry.TelemetryStore;
import io.maestro.protocol.v1.BackendMessage;
import io.maestro.protocol.v1.Hello;
import io.maestro.protocol.v1.LifecycleState;
import io.maestro.protocol.v1.LogRecord;
import io.maestro.protocol.v1.MetricSample;
import io.maestro.protocol.v1.RunnerGatewayGrpc;
import io.maestro.protocol.v1.RunnerMessage;
import io.maestro.protocol.v1.StatusReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 러너↔백엔드 양방향 스트림 처리. 러너 1개당 하나의 Session.
 * 첫 메시지(Hello) 토큰 검증 후 텔레메트리를 RunRegistry/TelemetryStore로 반영한다.
 */
@Component
public class RunnerGatewayService extends RunnerGatewayGrpc.RunnerGatewayImplBase {

    private static final Logger log = LoggerFactory.getLogger(RunnerGatewayService.class);

    private final RunRegistry registry;
    private final TelemetryStore telemetry;
    private final Supervisor supervisor;

    public RunnerGatewayService(RunRegistry registry, TelemetryStore telemetry, Supervisor supervisor) {
        this.registry = registry;
        this.telemetry = telemetry;
        this.supervisor = supervisor;
    }

    @Override
    public StreamObserver<RunnerMessage> session(StreamObserver<BackendMessage> responseObserver) {
        return new StreamObserver<>() {
            private volatile RunInfo run;

            @Override
            public void onNext(RunnerMessage msg) {
                switch (msg.getPayloadCase()) {
                    case HELLO -> handleHello(msg.getHello());
                    case STATUS -> {
                        if (run != null) {
                            applyStatus(run, msg.getStatus());
                        }
                    }
                    case LOG -> {
                        if (run != null) {
                            run.touchTelemetry();
                            LogRecord r = msg.getLog();
                            telemetry.addLog(run.runId(),
                                    new LogEntry(now(), r.getLevel().name(), r.getMessage(), r.getThrown()));
                        }
                    }
                    case METRIC -> {
                        if (run != null) {
                            run.touchTelemetry();
                            MetricSample m = msg.getMetric();
                            telemetry.addMetric(run.runId(), new MetricSnapshot(
                                    now(), m.getHeapUsedBytes(), m.getHeapMaxBytes(), m.getProcessCpuLoad(),
                                    m.getTickCount(), m.getErrorCount(), m.getUptimeMs(), m.getLastTickMs()));
                        }
                    }
                    case EMIT, STATE_OP, ACK, PAYLOAD_NOT_SET -> {
                        // EMIT: Phase 6 플로우 라우팅. STATE_OP/ACK: Phase 4 미사용.
                    }
                }
            }

            private void handleHello(Hello hello) {
                RunInfo r = registry.byRunnerId(hello.getRunnerId());
                if (r == null || r.token() == null || !r.token().equals(hello.getAuthToken())) {
                    log.warn("Hello 인증 실패 runnerId={}", hello.getRunnerId());
                    responseObserver.onError(Status.PERMISSION_DENIED
                            .withDescription("invalid runner id or token").asRuntimeException());
                    return;
                }
                this.run = r;
                log.info("러너 접속 runId={} runnerId={}", r.runId(), hello.getRunnerId());
                supervisor.onRunnerConnected(r, responseObserver);
            }

            @Override
            public void onError(Throwable t) {
                // 연결 단절(예: kill). 프로세스 사망은 Supervisor 워치독이 재시작 처리.
                if (run != null) {
                    log.debug("러너 스트림 오류 runId={}: {}", run.runId(), t.toString());
                }
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }

    private void applyStatus(RunInfo run, StatusReport status) {
        run.touchTelemetry();
        RunStatus mapped = map(status.getState());
        if (mapped != null) {
            run.setStatus(mapped);
        }
        if (status.hasDiags() && !status.getDiags().getSuccess()) {
            run.setLastError("컴파일 실패: " + status.getDiags().getItemsCount() + "건 진단");
            status.getDiags().getItemsList().forEach(d ->
                    telemetry.addLog(run.runId(),
                            new LogEntry(now(), "ERROR", "compile " + d.getMessage(), "")));
        }
        if (!status.getDetail().isEmpty()) {
            telemetry.addLog(run.runId(), new LogEntry(now(), "INFO", "[state] " + status.getDetail(), ""));
        }
    }

    private static RunStatus map(LifecycleState s) {
        return switch (s) {
            case COMPILING -> RunStatus.COMPILING;
            case STARTING -> RunStatus.STARTING;
            case RUNNING -> RunStatus.RUNNING;
            case STOPPING -> RunStatus.STOPPING;
            case STOPPED -> RunStatus.STOPPED;
            case ERROR -> RunStatus.ERROR;
            case LIFECYCLE_STATE_UNSPECIFIED, UNRECOGNIZED -> null;
        };
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
