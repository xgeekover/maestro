package io.maestro.backend.process;

import java.util.Map;

/** 실행 파라미터 + 리소스 한도. */
public record RunConfig(
        long tickPeriodMs,
        Map<String, String> params,
        boolean stopOnError,        // true면 TickPolicy.STOP
        long maxHeapBytes,          // <=0 이면 미설정(-Xmx 생략)
        long tickTimeoutMs,
        long onStartTimeoutMs,
        long onEndTimeoutMs,
        long errorThreshold
) {
    public static RunConfig defaults(long tickPeriodMs, Map<String, String> params) {
        return new RunConfig(
                tickPeriodMs > 0 ? tickPeriodMs : 1000,
                params == null ? Map.of() : Map.copyOf(params),
                false, 0, 0, 0, 5000, 0);
    }
}
