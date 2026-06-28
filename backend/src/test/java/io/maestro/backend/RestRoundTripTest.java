package io.maestro.backend;

import io.maestro.backend.api.Dtos;
import io.maestro.backend.telemetry.MetricSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Phase 5 완료기준의 헤드리스 대체 증명: 데스크탑 UI가 호출하는 REST 흐름
 * (작성 → 실행 → 상태 확인 → 중지)을 실제 HTTP로 왕복 검증한다. 러너는 실제 프로세스로 기동된다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "maestro.grpc.port=0",
                "maestro.restart.watchdog-period-ms=200",
                "maestro.runner.stop-grace-ms=2000"
        })
class RestRoundTripTest {

    private static final String SCRIPT = """
            import io.maestro.sdk.Script;
            public class RoundTrip extends Script {
                @Override public void onTick() {
                    ctx.state().put("n", ctx.state().get("n", Integer.class).orElse(0) + 1);
                }
            }
            """;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void authorRunCheckStatusStop() throws Exception {
        // 1) 작성 — POST /api/scripts
        ResponseEntity<Dtos.ScriptResponse> created = rest.postForEntity(
                "/api/scripts", new Dtos.CreateScriptRequest("round-trip", SCRIPT), Dtos.ScriptResponse.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        Dtos.ScriptResponse script = created.getBody();
        assertNotNull(script);

        // 2) 실행 — POST /api/runs
        Dtos.CreateRunRequest runReq = new Dtos.CreateRunRequest(
                script.id(), 100L, Map.of(), false, 0L, 0L, 0L);
        ResponseEntity<Dtos.RunResponse> started = rest.postForEntity(
                "/api/runs", runReq, Dtos.RunResponse.class);
        assertEquals(HttpStatus.ACCEPTED, started.getStatusCode());
        Dtos.RunResponse run = started.getBody();
        assertNotNull(run);
        String runId = run.runId();

        // 3) 상태 확인 — GET /api/runs/{id} 가 RUNNING + 메트릭 수신
        awaitUntil(Duration.ofSeconds(40), "RUNNING 상태",
                () -> "RUNNING".equals(getRun(runId).status()));
        awaitUntil(Duration.ofSeconds(20), "메트릭 수신", () -> !getMetrics(runId).isEmpty());

        // 4) 중지 — POST /api/runs/{id}/stop → 종료 확인
        ResponseEntity<Void> stop = rest.postForEntity("/api/runs/" + runId + "/stop", null, Void.class);
        assertEquals(HttpStatus.ACCEPTED, stop.getStatusCode());
        awaitUntil(Duration.ofSeconds(15), "STOPPED 상태",
                () -> "STOPPED".equals(getRun(runId).status()));

        // 목록에도 노출
        ResponseEntity<List<Dtos.RunResponse>> runs = rest.exchange(
                "/api/runs", HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
        assertNotNull(runs.getBody());
        assertTrue(runs.getBody().stream().anyMatch(r -> r.runId().equals(runId)));
    }

    private Dtos.RunResponse getRun(String runId) {
        return rest.getForObject("/api/runs/" + runId, Dtos.RunResponse.class);
    }

    private List<MetricSnapshot> getMetrics(String runId) {
        ResponseEntity<List<MetricSnapshot>> res = rest.exchange(
                "/api/runs/" + runId + "/metrics", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});
        List<MetricSnapshot> body = res.getBody();
        return body == null ? List.of() : body;
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
                // 일시적 오류 무시(폴링 재시도)
            }
            Thread.sleep(150);
        }
        fail("타임아웃 — 조건 미충족: " + desc);
    }
}
