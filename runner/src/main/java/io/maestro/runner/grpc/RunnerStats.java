package io.maestro.runner.grpc;

import java.util.concurrent.atomic.AtomicLong;

/** 리스너가 갱신하고 메트릭 샘플러가 읽는 공유 카운터. */
public final class RunnerStats {
    final AtomicLong tickCount = new AtomicLong();
    final AtomicLong errorCount = new AtomicLong();
    final long startNanos = System.nanoTime();
    volatile double lastTickMs = 0;

    long uptimeMs() {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
