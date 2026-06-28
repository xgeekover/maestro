package io.maestro.backend;

import io.maestro.backend.process.RunConfig;
import io.maestro.backend.process.RunInfo;
import io.maestro.backend.process.RunStatus;
import io.maestro.backend.telemetry.MetricSnapshot;
import io.maestro.backend.telemetry.TelemetryStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Phase 4 완료기준 증명: 여러 러너를 독립 프로세스로 기동하고, 한 프로세스를 강제 종료해도
 * 나머지가 정상 동작(격리)하며, 감시자가 죽은 프로세스를 재시작 정책에 따라 복구한다.
 *
 * <p>러너 JVM은 백엔드 테스트 클래스패스(java.class.path, runner 포함)로 기동된다.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "maestro.grpc.port=0",
                "maestro.restart.base-delay-ms=300",
                "maestro.restart.max-delay-ms=2000",
                "maestro.restart.watchdog-period-ms=200",
                "maestro.restart.max-attempts=5",
                "maestro.runner.stop-grace-ms=2000"
        })
class OrchestrationIntegrationTest {

    private static final String INFINITE_SCRIPT = """
            import io.maestro.sdk.Script;
            public class Loop extends Script {
                @Override public void onStart() { ctx.log().info("loop start"); }
                @Override public void onTick() {
                    ctx.state().put("n", ctx.state().get("n", Integer.class).orElse(0) + 1);
                }
                @Override public void onEnd() { ctx.log().info("loop end"); }
            }
            """;

    @Autowired
    private io.maestro.backend.process.Supervisor supervisor;

    @Autowired
    private TelemetryStore telemetry;

    @Test
    void killingOneProcessIsolatesOthersAndSupervisorRestarts() throws Exception {
        RunConfig cfg = RunConfig.defaults(100, Map.of());

        RunInfo a = supervisor.startRunWithSource("loop-a", INFINITE_SCRIPT, cfg);
        RunInfo b = supervisor.startRunWithSource("loop-b", INFINITE_SCRIPT, cfg);
        RunInfo c = supervisor.startRunWithSource("loop-c", INFINITE_SCRIPT, cfg);

        // 1) 세 프로세스 모두 RUNNING + 메트릭 수신(텔레메트리 흐름 증명)
        awaitUntil(Duration.ofSeconds(40), "모든 러너 RUNNING",
                () -> a.status() == RunStatus.RUNNING
                        && b.status() == RunStatus.RUNNING
                        && c.status() == RunStatus.RUNNING);
        awaitUntil(Duration.ofSeconds(20), "메트릭 수신",
                () -> !telemetry.metrics(a.runId()).isEmpty()
                        && !telemetry.metrics(b.runId()).isEmpty()
                        && !telemetry.metrics(c.runId()).isEmpty());

        long bTicksBefore = latestTick(b.runId());
        long cTicksBefore = latestTick(c.runId());

        // 2) 한 프로세스를 강제 종료(kill -9 시뮬레이션)
        Process killed = a.process();
        assertTrue(killed.isAlive(), "kill 대상 프로세스는 살아있어야 함");
        killed.destroyForcibly();
        killed.waitFor();

        // 3) 격리: 나머지 프로세스는 영향 없이 계속 동작(tick 증가 + RUNNING 유지)
        awaitUntil(Duration.ofSeconds(15), "나머지 러너 tick 진행(격리)",
                () -> latestTick(b.runId()) > bTicksBefore && latestTick(c.runId()) > cTicksBefore);
        assertEquals(RunStatus.RUNNING, b.status(), "B는 격리되어 RUNNING 유지");
        assertEquals(RunStatus.RUNNING, c.status(), "C는 격리되어 RUNNING 유지");
        assertTrue(alive(b) && alive(c), "나머지 프로세스 생존");

        // 4) 재시작 정책: 감시자가 죽은 프로세스를 감지·재시작(restartCount 증가 후 RUNNING 복귀)
        awaitUntil(Duration.ofSeconds(40), "A 재시작 + RUNNING 복귀",
                () -> a.restartCount() >= 1 && a.status() == RunStatus.RUNNING && alive(a));
        assertTrue(a.restartCount() >= 1, "재시작 횟수 증가");

        // 정리: graceful 중지
        supervisor.stopRun(a.runId());
        supervisor.stopRun(b.runId());
        supervisor.stopRun(c.runId());
    }

    private static boolean alive(RunInfo run) {
        Process p = run.process();
        return p != null && p.isAlive();
    }

    private long latestTick(String runId) {
        List<MetricSnapshot> samples = telemetry.metrics(runId);
        return samples.isEmpty() ? -1 : samples.get(samples.size() - 1).tickCount();
    }

    private static void awaitUntil(Duration timeout, String desc, BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(150);
        }
        fail("타임아웃 — 조건 미충족: " + desc);
    }
}
