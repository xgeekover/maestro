package io.maestro.runner.grpc;

import com.sun.management.OperatingSystemMXBean;
import io.maestro.protocol.v1.MetricSample;
import io.maestro.protocol.v1.RunnerMessage;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** 주기적으로 heap/CPU/tick/error 메트릭을 백엔드로 스트리밍한다(FR-6). */
public final class MetricSampler {

    private final StreamSender sender;
    private final RunnerStats stats;
    private final long periodMs;
    private final ScheduledExecutorService exec;
    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    private final OperatingSystemMXBean os =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    public MetricSampler(StreamSender sender, RunnerStats stats, long periodMs) {
        this.sender = sender;
        this.stats = stats;
        this.periodMs = periodMs;
        this.exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "maestro-metrics");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        exec.scheduleAtFixedRate(this::sample, periodMs, periodMs, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        exec.shutdownNow();
    }

    private void sample() {
        try {
            var heap = memory.getHeapMemoryUsage();
            sender.send(RunnerMessage.newBuilder()
                    .setMetric(MetricSample.newBuilder()
                            .setHeapUsedBytes(heap.getUsed())
                            .setHeapMaxBytes(heap.getMax())
                            .setProcessCpuLoad(Math.max(0, os.getProcessCpuLoad()))
                            .setTickCount(stats.tickCount.get())
                            .setErrorCount(stats.errorCount.get())
                            .setUptimeMs(stats.uptimeMs())
                            .setLastTickMs(stats.lastTickMs))
                    .build());
        } catch (RuntimeException ignored) {
            // 샘플 실패는 무시(다음 주기 재시도)
        }
    }
}
