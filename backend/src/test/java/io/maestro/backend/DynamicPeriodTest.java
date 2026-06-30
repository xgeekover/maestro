package io.maestro.backend;

import io.maestro.backend.process.RunConfig;
import io.maestro.backend.process.RunInfo;
import io.maestro.backend.process.RunStatus;
import io.maestro.backend.process.Supervisor;
import io.maestro.backend.telemetry.MetricSnapshot;
import io.maestro.backend.telemetry.TelemetryStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 4️⃣ 동적 주기 변경: 실행 중 tick 주기를 단축하면 tick 빈도가 가속됨을 검증(UpdatePeriodCommand 배선).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"maestro.grpc.port=0", "maestro.restart.watchdog-period-ms=200", "maestro.runner.stop-grace-ms=1500"})
class DynamicPeriodTest {

    private static final String SCRIPT = """
            import io.maestro.sdk.Script;
            public class Dyn extends Script {
                @Override public void onTick() { ctx.state().put("n", ctx.state().get("n", Integer.class).orElse(0)+1); }
            }
            """;

    @Autowired private Supervisor supervisor;
    @Autowired private TelemetryStore telemetry;

    @Test
    void shorteningPeriodAcceleratesTicks() throws Exception {
        // 1초 주기로 시작
        RunInfo run = supervisor.startRunWithSource("dyn", SCRIPT, RunConfig.defaults(1000, Map.of()));
        awaitUntil(Duration.ofSeconds(40), "RUNNING + 메트릭",
                () -> run.status() == RunStatus.RUNNING && telemetry.latest(run.runId()).isPresent());

        Thread.sleep(1500);
        long before = tick(run);

        // 50ms로 단축
        supervisor.updatePeriod(run.runId(), 50);
        Thread.sleep(3000);
        long after = tick(run);

        long delta = after - before;
        // 50ms 주기면 ~수십 tick. 1초였다면 ~3. 큰 차이로 가속 입증.
        assertTrue(delta >= 15, "주기 단축으로 tick 가속되어야 함, delta=" + delta);

        supervisor.stopRun(run.runId());
    }

    private long tick(RunInfo run) {
        return telemetry.latest(run.runId()).map(MetricSnapshot::tickCount).orElse(0L);
    }

    private static void awaitUntil(Duration timeout, String desc, BooleanSupplier cond) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (cond.getAsBoolean()) {
                return;
            }
            Thread.sleep(150);
        }
        fail("타임아웃 — " + desc);
    }
}
