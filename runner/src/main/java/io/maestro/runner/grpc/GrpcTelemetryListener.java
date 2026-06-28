package io.maestro.runner.grpc;

import io.maestro.protocol.v1.CompileDiagnostics;
import io.maestro.protocol.v1.Diagnostic;
import io.maestro.protocol.v1.LifecycleState;
import io.maestro.protocol.v1.LogRecord;
import io.maestro.protocol.v1.RunnerMessage;
import io.maestro.protocol.v1.StatusReport;
import io.maestro.runner.compile.Diag;
import io.maestro.runner.engine.LifecycleListener;

import java.util.List;

/** 엔진 이벤트를 gRPC 텔레메트리(StatusReport/LogRecord)로 변환·전송하고 통계를 갱신한다. */
public final class GrpcTelemetryListener implements LifecycleListener {

    private final StreamSender sender;
    private final RunnerStats stats;

    public GrpcTelemetryListener(StreamSender sender, RunnerStats stats) {
        this.sender = sender;
        this.stats = stats;
    }

    @Override
    public void onStateChange(io.maestro.runner.engine.LifecycleState from,
                              io.maestro.runner.engine.LifecycleState to, String detail) {
        sender.send(RunnerMessage.newBuilder()
                .setStatus(StatusReport.newBuilder()
                        .setState(map(to))
                        .setDetail(detail == null ? "" : detail))
                .build());
    }

    @Override
    public void onCompileDiagnostics(boolean success, List<Diag> diagnostics) {
        CompileDiagnostics.Builder cd = CompileDiagnostics.newBuilder().setSuccess(success);
        for (Diag d : diagnostics) {
            cd.addItems(Diagnostic.newBuilder()
                    .setKind(switch (d.kind()) {
                        case ERROR -> Diagnostic.Kind.ERROR;
                        case WARNING -> Diagnostic.Kind.WARNING;
                        case NOTE -> Diagnostic.Kind.NOTE;
                    })
                    .setLine(d.line())
                    .setColumn(d.column())
                    .setCode(d.code() == null ? "" : d.code())
                    .setMessage(d.message() == null ? "" : d.message()));
        }
        sender.send(RunnerMessage.newBuilder()
                .setStatus(StatusReport.newBuilder()
                        .setState(LifecycleState.COMPILING)
                        .setDiags(cd))
                .build());
    }

    @Override
    public void onTickComplete(long tickNumber, long durationMs) {
        stats.tickCount.set(tickNumber);
        stats.lastTickMs = durationMs;
    }

    @Override
    public void onTickError(long tickNumber, Throwable error) {
        stats.tickCount.set(tickNumber);
        stats.errorCount.incrementAndGet();
        log(LogRecord.Level.ERROR, "tick #" + tickNumber + " 예외(격리): " + error);
    }

    @Override
    public void onTickTimeout(long tickNumber, long timeoutMs) {
        stats.errorCount.incrementAndGet();
        log(LogRecord.Level.ERROR, "tick #" + tickNumber + " 타임아웃(" + timeoutMs + "ms) — 행 감지");
    }

    @Override
    public void onLog(String message) {
        log(LogRecord.Level.INFO, message);
    }

    private void log(LogRecord.Level level, String message) {
        sender.send(RunnerMessage.newBuilder()
                .setLog(LogRecord.newBuilder().setLevel(level).setMessage(message))
                .build());
    }

    private static LifecycleState map(io.maestro.runner.engine.LifecycleState s) {
        return switch (s) {
            case NEW -> LifecycleState.LIFECYCLE_STATE_UNSPECIFIED;
            case COMPILING -> LifecycleState.COMPILING;
            case STARTING -> LifecycleState.STARTING;
            case RUNNING -> LifecycleState.RUNNING;
            case STOPPING -> LifecycleState.STOPPING;
            case STOPPED -> LifecycleState.STOPPED;
            case ERROR -> LifecycleState.ERROR;
        };
    }
}
