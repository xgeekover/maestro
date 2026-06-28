package io.maestro.runner.engine;

/**
 * 라이프사이클 엔진 실행 설정. 불변. 빌더로 생성.
 */
public final class EngineConfig {

    private final long tickPeriodMs;
    private final long maxTicks;          // < 0 이면 무제한(중지 요청까지)
    private final TickPolicy tickPolicy;
    private final long errorThreshold;    // CONTINUE에서 누적 에러 임계(이상이면 중지). <= 0 이면 무제한
    private final long onStartTimeoutMs;  // <= 0 이면 무제한
    private final long tickTimeoutMs;     // tick 행 감지 워치독. <= 0 이면 무제한
    private final long onEndTimeoutMs;

    private EngineConfig(Builder b) {
        this.tickPeriodMs = b.tickPeriodMs;
        this.maxTicks = b.maxTicks;
        this.tickPolicy = b.tickPolicy;
        this.errorThreshold = b.errorThreshold;
        this.onStartTimeoutMs = b.onStartTimeoutMs;
        this.tickTimeoutMs = b.tickTimeoutMs;
        this.onEndTimeoutMs = b.onEndTimeoutMs;
    }

    public long tickPeriodMs() { return tickPeriodMs; }
    public long maxTicks() { return maxTicks; }
    public TickPolicy tickPolicy() { return tickPolicy; }
    public long errorThreshold() { return errorThreshold; }
    public long onStartTimeoutMs() { return onStartTimeoutMs; }
    public long tickTimeoutMs() { return tickTimeoutMs; }
    public long onEndTimeoutMs() { return onEndTimeoutMs; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long tickPeriodMs = 1000;
        private long maxTicks = -1;
        private TickPolicy tickPolicy = TickPolicy.CONTINUE;
        private long errorThreshold = -1;
        private long onStartTimeoutMs = -1;
        private long tickTimeoutMs = -1;
        private long onEndTimeoutMs = 5000;

        public Builder tickPeriodMs(long v) { this.tickPeriodMs = v; return this; }
        public Builder maxTicks(long v) { this.maxTicks = v; return this; }
        public Builder tickPolicy(TickPolicy v) { this.tickPolicy = v; return this; }
        public Builder errorThreshold(long v) { this.errorThreshold = v; return this; }
        public Builder onStartTimeoutMs(long v) { this.onStartTimeoutMs = v; return this; }
        public Builder tickTimeoutMs(long v) { this.tickTimeoutMs = v; return this; }
        public Builder onEndTimeoutMs(long v) { this.onEndTimeoutMs = v; return this; }

        public EngineConfig build() {
            return new EngineConfig(this);
        }
    }
}
