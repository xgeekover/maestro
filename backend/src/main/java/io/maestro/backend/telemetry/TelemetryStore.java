package io.maestro.backend.telemetry;

import io.maestro.backend.config.MaestroProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 프로세스별 메트릭/로그 인메모리 링버퍼(ADR-0001 D3). REST 폴링 조회 + WS 실시간 푸시(이벤트 발행).
 */
@Component
public class TelemetryStore {

    private final MaestroProperties props;
    private final ApplicationEventPublisher events;
    private final ConcurrentHashMap<String, RingBuffer<MetricSnapshot>> metrics = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RingBuffer<LogEntry>> logs = new ConcurrentHashMap<>();

    public TelemetryStore(MaestroProperties props, ApplicationEventPublisher events) {
        this.props = props;
        this.events = events;
    }

    public void addMetric(String runId, MetricSnapshot sample) {
        metrics.computeIfAbsent(runId, k -> new RingBuffer<>(props.getBuffer().getMetricCapacity())).add(sample);
        events.publishEvent(new TelemetryEvents.MetricEvent(runId, sample));
    }

    public void addLog(String runId, LogEntry entry) {
        logs.computeIfAbsent(runId, k -> new RingBuffer<>(props.getBuffer().getLogCapacity())).add(entry);
        events.publishEvent(new TelemetryEvents.LogEvent(runId, entry));
    }

    public List<MetricSnapshot> metrics(String runId) {
        RingBuffer<MetricSnapshot> b = metrics.get(runId);
        return b == null ? List.of() : b.snapshot();
    }

    public List<LogEntry> logs(String runId) {
        RingBuffer<LogEntry> b = logs.get(runId);
        return b == null ? List.of() : b.snapshot();
    }
}
