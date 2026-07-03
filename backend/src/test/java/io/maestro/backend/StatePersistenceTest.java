package io.maestro.backend;

import io.maestro.backend.process.RunConfig;
import io.maestro.backend.process.RunInfo;
import io.maestro.backend.process.RunStatus;
import io.maestro.backend.process.Supervisor;
import io.maestro.backend.state.ScriptStateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 상태 영속 e2e: onStart에서 카운터를 state()에 증가 저장하는 스크립트를 같은 scriptId로
 * 실행·중지·재실행하면, 러너 프로세스가 새로 떠도 카운트가 이어진다(재시작 간 유지).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {"maestro.grpc.port=0"})
class StatePersistenceTest {

    private static final String COUNTER = """
            import io.maestro.sdk.Script;
            public class Counter extends Script {
                @Override public void onStart() {
                    int n = ctx.state().get("n", Integer.class).orElse(0) + 1;
                    ctx.state().put("n", n);
                    ctx.log().info("n=" + n);
                }
                @Override public void onTick() { /* keep alive */ }
            }
            """;

    @Autowired
    private Supervisor supervisor;
    @Autowired
    private ScriptStateService state;

    @Test
    void statePersistsAcrossRestart() throws Exception {
        String name = "persist-" + System.nanoTime();
        String owner = "inline:" + name; // startRunWithSource → scriptId="inline:"+name

        // --- 1회차: 카운터 1 ---
        RunInfo r1 = supervisor.startRunWithSource(name, COUNTER, RunConfig.defaults(200, Map.of()));
        awaitUntil(Duration.ofSeconds(30), "run1 RUNNING", () -> r1.status() == RunStatus.RUNNING);
        awaitUntil(Duration.ofSeconds(10), "n=1 저장", () -> "1".equals(state.get(owner, "n").orElse(null)));
        supervisor.stopRun(r1.runId());
        awaitUntil(Duration.ofSeconds(15), "run1 STOPPED", () -> r1.status() == RunStatus.STOPPED);

        // --- 2회차: 같은 scriptId → 상태 복원되어 카운터 2 ---
        RunInfo r2 = supervisor.startRunWithSource(name, COUNTER, RunConfig.defaults(200, Map.of()));
        awaitUntil(Duration.ofSeconds(30), "run2 RUNNING", () -> r2.status() == RunStatus.RUNNING);
        awaitUntil(Duration.ofSeconds(10), "n=2 (재시작 간 유지)",
                () -> "2".equals(state.get(owner, "n").orElse(null)));
        supervisor.stopRun(r2.runId());

        assertEquals("2", state.get(owner, "n").orElse(null),
                "상태가 재시작 간 유지되어 카운트가 이어져야 함");
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
