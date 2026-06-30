package io.maestro.backend;

import io.maestro.backend.process.RunConfig;
import io.maestro.backend.process.RunInfo;
import io.maestro.backend.process.RunRegistry;
import io.maestro.backend.process.RunStatus;
import io.maestro.backend.process.Supervisor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * QA H-4: 종료(STOPPED/ERROR) 런이 TTL 후 RunRegistry에서 회수되어 메모리 무한 증가를 막는다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "maestro.grpc.port=0",
                "maestro.runner.stop-grace-ms=1000",
                "maestro.limits.terminal-retention-ms=500",
                "maestro.limits.eviction-sweep-ms=300"
        })
class EvictionIntegrationTest {

    private static final String INFINITE = """
            import io.maestro.sdk.Script;
            public class Ev extends Script {
                @Override public void onTick() { ctx.state().put("n", ctx.state().get("n", Integer.class).orElse(0)+1); }
            }
            """;

    @Autowired private Supervisor supervisor;
    @Autowired private RunRegistry registry;

    @Test
    void terminatedRunIsEvictedAfterTtl() throws Exception {
        RunInfo run = supervisor.startRunWithSource("evict", INFINITE, RunConfig.defaults(100, Map.of()));
        String runId = run.runId();

        awaitUntil(Duration.ofSeconds(40), "RUNNING", () -> run.status() == RunStatus.RUNNING);
        assertNotNull(registry.byRunId(runId));

        supervisor.stopRun(runId);
        awaitUntil(Duration.ofSeconds(15), "STOPPED", () -> run.status() == RunStatus.STOPPED);

        // TTL(500ms) + 스윕(300ms) 이후 레지스트리에서 회수
        awaitUntil(Duration.ofSeconds(10), "레지스트리에서 회수", () -> registry.byRunId(runId) == null);
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
