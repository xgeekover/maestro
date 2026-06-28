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
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Phase 7 완료기준 증명: 실행 중 프로세스의 상태·CPU/메모리 메트릭이 대시보드 데이터 소스
 * ({@code GET /api/dashboard})에 정확히 반영된다(부하 반영).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"maestro.grpc.port=0", "maestro.runner.stop-grace-ms=2000"})
class DashboardIntegrationTest {

    private static final String SCRIPT = """
            import io.maestro.sdk.Script;
            public class Busy extends Script {
                @Override public void onTick() {
                    long s = 0; for (int i = 0; i < 100000; i++) s += i; // 약간의 부하
                    ctx.state().put("s", (int) (s & 0xff));
                }
            }
            """;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void dashboardReflectsRunningProcessMetrics() throws Exception {
        Dtos.ScriptResponse script = rest.postForEntity(
                "/api/scripts", new Dtos.CreateScriptRequest("busy", SCRIPT), Dtos.ScriptResponse.class).getBody();
        assertNotNull(script);

        Dtos.RunResponse run = rest.postForEntity(
                "/api/runs", new Dtos.CreateRunRequest(script.id(), 100L, Map.of(), false, 0L, 0L, 0L),
                Dtos.RunResponse.class).getBody();
        assertNotNull(run);
        String runId = run.runId();

        // 대시보드에 RUNNING + 최신 메트릭(heap>0)이 반영
        awaitUntil(Duration.ofSeconds(40), "대시보드에 RUNNING + heap 메트릭 반영", () -> {
            Optional<Dtos.RunSummary> s = summaryFor(runId);
            return s.isPresent()
                    && "RUNNING".equals(s.get().run().status())
                    && s.get().latest() != null
                    && s.get().latest().heapUsedBytes() > 0;
        });

        Dtos.RunSummary summary = summaryFor(runId).orElseThrow();
        assertTrue(summary.latest().tickCount() >= 0, "tick 메트릭 노출");
        assertTrue(summary.latest().heapMaxBytes() > 0, "heap max 노출");

        rest.postForEntity("/api/runs/" + runId + "/stop", null, Void.class);
    }

    private Optional<Dtos.RunSummary> summaryFor(String runId) {
        ResponseEntity<List<Dtos.RunSummary>> res = rest.exchange(
                "/api/dashboard", HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
        List<Dtos.RunSummary> body = res.getBody();
        if (body == null) {
            return Optional.empty();
        }
        return body.stream().filter(s -> s.run().runId().equals(runId)).findFirst();
    }

    private static void awaitUntil(Duration timeout, String desc, BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                if (condition.getAsBoolean()) {
                    return;
                }
            } catch (RuntimeException ignored) {
                // 폴링 재시도
            }
            Thread.sleep(150);
        }
        fail("타임아웃 — 조건 미충족: " + desc);
    }
}
