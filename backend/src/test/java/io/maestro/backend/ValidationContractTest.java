package io.maestro.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QA H-2/H-3: 입력 검증(→400) · 없는 리소스(→404) · 도메인 위반(→422) · 표준 에러 엔벨로프 검증.
 * 대부분 케이스는 검증/조회 단계에서 거부되어 러너를 띄우지 않는다(빠름).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"maestro.grpc.port=0"})
class ValidationContractTest {

    private static final String VALID =
            "import io.maestro.sdk.Script; public class Ok extends Script { @Override public void onTick(){} }";

    @Autowired
    private TestRestTemplate rest;

    private int status(String path, Object body) {
        return rest.postForEntity(path, body, String.class).getStatusCode().value();
    }

    @Test
    void scriptNullNameIsBadRequestNotServerError() {
        assertEquals(400, status("/api/scripts", Map.of("source", VALID)), "name 누락 → 400(500 아님)");
    }

    @Test
    void scriptEmptySourceRejected() {
        assertEquals(400, status("/api/scripts", Map.of("name", "x", "source", "")));
    }

    @Test
    void scriptOversizeSourceRejected() {
        String big = "x".repeat(300 * 1024); // 300KB > 256KB 캡
        assertEquals(400, status("/api/scripts", Map.of("name", "big", "source", big)));
    }

    @Test
    void runMissingScriptIdRejected() {
        assertEquals(400, status("/api/runs", Map.of()), "scriptId 누락 → 400");
    }

    @Test
    void runNegativeTickPeriodRejected() {
        assertEquals(400, status("/api/runs", Map.of("scriptId", "x", "tickPeriodMs", -5)));
    }

    @Test
    void runNonexistentScriptIsNotFound() {
        // 유효한 본문 + 존재하지 않는 id → 404 (이전엔 422)
        assertEquals(404, status("/api/runs", Map.of("scriptId", "ghost-id", "tickPeriodMs", 1000)));
    }

    @Test
    void stopNonexistentRunIsNotFound() {
        assertEquals(404, status("/api/runs/ghost-run/stop", null));
    }

    @Test
    void deployNonexistentFlowIsNotFound() {
        assertEquals(404, status("/api/flows/ghost-flow/deploy", null));
    }

    @Test
    void flowNullGraphIsBadRequest() {
        assertEquals(400, status("/api/flows", Map.of("name", "f")), "graph 누락 → 400(NPE/500 아님)");
    }

    @Test
    void cyclicFlowIsUnprocessable() {
        Map<String, Object> cyc = Map.of("name", "c", "graph", Map.of(
                "nodes", java.util.List.of(
                        Map.of("id", "a", "kind", "SCRIPT", "refId", "x", "params", Map.of(), "tickPeriodMs", 500),
                        Map.of("id", "b", "kind", "SCRIPT", "refId", "y", "params", Map.of(), "tickPeriodMs", 500)),
                "edges", java.util.List.of(
                        Map.of("fromNode", "a", "fromPort", "o", "toNode", "b", "toPort", "i"),
                        Map.of("fromNode", "b", "fromPort", "o", "toNode", "a", "toPort", "i"))));
        assertEquals(422, status("/api/flows", cyc), "도메인 규칙(사이클) → 422");
    }

    @Test
    void brokenJsonIsBadRequest() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> r = rest.postForEntity("/api/scripts", new HttpEntity<>("{bad json", h), String.class);
        assertEquals(400, r.getStatusCode().value());
    }

    @Test
    void validScriptStillCreated() {
        assertEquals(201, status("/api/scripts", Map.of("name", "good", "source", VALID)));
    }

    @Test
    void errorEnvelopeShape() {
        ResponseEntity<Map> r = rest.postForEntity("/api/scripts", Map.of("source", VALID), Map.class);
        assertEquals(400, r.getStatusCode().value());
        Map<?, ?> b = r.getBody();
        assertNotNull(b);
        assertEquals(400, b.get("status"));
        assertNotNull(b.get("error"));
        assertTrue(b.containsKey("details"), "검증 실패는 필드 details 포함");
    }

    @Test
    void unknownRouteStillNotFound() {
        // 제네릭 예외 핸들러가 프레임워크 404를 500으로 덮지 않아야 함
        ResponseEntity<String> r = rest.getForEntity("/api/does-not-exist", String.class);
        assertEquals(404, r.getStatusCode().value());
    }
}
