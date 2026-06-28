package io.maestro.backend.telemetry;

/** WS 스트리밍을 위한 텔레메트리 이벤트(Spring ApplicationEvent로 발행). */
public final class TelemetryEvents {

    private TelemetryEvents() {
    }

    public record MetricEvent(String runId, MetricSnapshot sample) {}

    public record LogEvent(String runId, LogEntry entry) {}
}
