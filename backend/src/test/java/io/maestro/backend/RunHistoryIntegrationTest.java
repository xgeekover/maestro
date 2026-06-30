package io.maestro.backend;

import io.maestro.backend.api.Dtos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 3️⃣ run 이력 영속: 종료된 실행이 DB에 기록되고 {@code GET /api/runs/history}로 조회된다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"maestro.grpc.port=0", "maestro.restart.watchdog-period-ms=200", "maestro.runner.stop-grace-ms=1500"})
class RunHistoryIntegrationTest {

    private static final String SCRIPT = """
            import io.maestro.sdk.Script;
            public class Hist extends Script {
                @Override public void onTick() { ctx.state().put("n", ctx.state().get("n", Integer.class).orElse(0)+1); }
            }
            """;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void terminatedRunIsRecordedAndQueryable() throws Exception {
        Dtos.ScriptResponse script = rest.postForEntity(
                "/api/scripts", new Dtos.CreateScriptRequest("hist", SCRIPT), Dtos.ScriptResponse.class).getBody();
        assertNotNull(script);
        Dtos.RunResponse run = rest.postForEntity(
                "/api/runs", new Dtos.CreateRunRequest(script.id(), 100L, Map.of(), false, 0L, 0L, 0L),
                Dtos.RunResponse.class).getBody();
        assertNotNull(run);
        String runId = run.runId();

        awaitUntil(Duration.ofSeconds(40), "RUNNING", () -> "RUNNING".equals(getRunStatus(runId)));
        rest.postForEntity("/api/runs/" + runId + "/stop", null, Void.class);

        // 종료 후 워치독이 이력에 기록 → /api/runs/history 에 노출
        awaitUntil(Duration.ofSeconds(15), "이력에 기록", () -> historyContains(runId));

        // 단건 조회
        ResponseEntity<Dtos.RunHistoryResponse> one = rest.getForEntity(
                "/api/runs/history/" + runId, Dtos.RunHistoryResponse.class);
        assertEquals(200, one.getStatusCode().value());
        assertNotNull(one.getBody());
        assertEquals("STOPPED", one.getBody().status());
        assertEquals("hist", one.getBody().scriptName());
        assertNotNull(one.getBody().endedAt());
    }

    private String getRunStatus(String runId) {
        Dtos.RunResponse r = rest.getForObject("/api/runs/" + runId, Dtos.RunResponse.class);
        return r == null ? null : r.status();
    }

    private boolean historyContains(String runId) {
        ResponseEntity<List<Dtos.RunHistoryResponse>> res = rest.exchange(
                "/api/runs/history?page=0&size=50", HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
        List<Dtos.RunHistoryResponse> body = res.getBody();
        return body != null && body.stream().anyMatch(h -> h.runId().equals(runId));
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
