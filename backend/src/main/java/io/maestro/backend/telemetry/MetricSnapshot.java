package io.maestro.backend.telemetry;

/** 프로세스별 메트릭 샘플(러너 보고). */
public record MetricSnapshot(
        long epochMs,
        long heapUsedBytes,
        long heapMaxBytes,
        double processCpuLoad,
        long tickCount,
        long errorCount,
        long uptimeMs,
        double lastTickMs
) {}
