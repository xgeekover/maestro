package io.maestro.backend;

import io.maestro.backend.process.RunConfig;
import io.maestro.backend.process.RunInfo;
import io.maestro.backend.process.RunStatus;
import io.maestro.backend.process.Supervisor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * QA H-7(자가종료 vs 돌연사 구분) + H-6(onStart 행 바운드) 신뢰성 검증.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "maestro.grpc.port=0",
                "maestro.restart.base-delay-ms=300",
                "maestro.restart.watchdog-period-ms=200",
                "maestro.restart.death-grace-ms=500",
                "maestro.restart.max-attempts=5",
                "maestro.runner.stop-grace-ms=1500"
        })
class ReliabilityTest {

    private static final String FAULT_STOP = """
            import io.maestro.sdk.Script;
            public class Stopper extends Script {
                @Override public void onTick() { throw new RuntimeException("의도적 결함"); }
            }
            """;

    private static final String HANG_START = """
            import io.maestro.sdk.Script;
            public class HangStart extends Script {
                @Override public void onStart() { while (true) { Thread.onSpinWait(); } }
            }
            """;

    private static final String HEALTHY = """
            import io.maestro.sdk.Script;
            public class Healthy extends Script {
                @Override public void onTick() { ctx.state().put("n", ctx.state().get("n", Integer.class).orElse(0)+1); }
            }
            """;

    @Autowired
    private Supervisor supervisor;

    @Test
    void selfTerminatingRunIsNotRestarted() throws Exception {
        // STOP 정책 + 첫 tick 예외 → 자가종료(STOPPED). 감시자가 부활시키면 안 됨(H-7).
        RunConfig stop = new RunConfig(100, Map.of(), true, 0, 0, 0, 1000, 0);
        RunInfo run = supervisor.startRunWithSource("stopper", FAULT_STOP, stop);

        awaitUntil(Duration.ofSeconds(30), "자가종료(STOPPED)", () -> run.status() == RunStatus.STOPPED);
        // 부활 기회를 충분히 준 뒤에도 재시작 없음
        Thread.sleep(3000);
        assertEquals(RunStatus.STOPPED, run.status(), "여전히 STOPPED");
        assertEquals(0, run.restartCount(), "자가종료는 재시작되지 않아야 함");

        supervisor.stopRun(run.runId());
    }

    @Test
    void onStartHangIsBoundedToError() throws Exception {
        // onStart 무한루프 → onStart 타임아웃(800ms)으로 바운드되어 ERROR. STARTING에 영구 정지 금지(H-6).
        RunConfig cfg = new RunConfig(200, Map.of(), false, 0, 0, 800, 800, 0);
        RunInfo run = supervisor.startRunWithSource("hangstart", HANG_START, cfg);

        awaitUntil(Duration.ofSeconds(30), "onStart 행 바운드 → ERROR", () -> run.status() == RunStatus.ERROR);

        supervisor.stopRun(run.runId());
    }

    @Test
    void killStillRestarts() throws Exception {
        // grace가 정당한 돌연사(kill) 재시작을 막지 않아야 함.
        RunInfo run = supervisor.startRunWithSource("killable", HEALTHY, RunConfig.defaults(100, Map.of()));
        awaitUntil(Duration.ofSeconds(40), "RUNNING", () -> run.status() == RunStatus.RUNNING);

        run.process().destroyForcibly();
        run.process().waitFor();

        awaitUntil(Duration.ofSeconds(25), "kill 후 재시작",
                () -> run.restartCount() >= 1 && run.status() == RunStatus.RUNNING);
        assertTrue(run.restartCount() >= 1);

        supervisor.stopRun(run.runId());
    }

    private static void awaitUntil(Duration timeout, String desc, BooleanSupplier cond) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                if (cond.getAsBoolean()) {
                    return;
                }
            } catch (RuntimeException ignored) {
                // 폴링 재시도
            }
            Thread.sleep(150);
        }
        fail("타임아웃 — " + desc);
    }
}
