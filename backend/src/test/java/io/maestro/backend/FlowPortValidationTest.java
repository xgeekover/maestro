package io.maestro.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 모듈 저작 T3: 엣지가 참조 노드의 선언된 포트를 쓰는지 검증(모듈 specJson 기반).
 * 선언 포트 → 201, 미선언 포트 → 422.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"maestro.grpc.port=0"})
class FlowPortValidationTest {

    private static final String SRC =
            "import io.maestro.sdk.Script; public class M extends Script { @Override public void onTick(){} }";

    @Autowired
    private TestRestTemplate rest;

    private int postFlow(String moduleId, String fromPort) {
        Map<String, Object> graph = Map.of(
                "nodes", List.of(
                        Map.of("id", "m", "kind", "MODULE", "refId", moduleId, "params", Map.of(), "tickPeriodMs", 500),
                        Map.of("id", "s", "kind", "SCRIPT", "refId", "x", "params", Map.of(), "tickPeriodMs", 500)),
                "edges", List.of(
                        Map.of("fromNode", "m", "fromPort", fromPort, "toNode", "s", "toPort", "in")));
        return rest.postForEntity("/api/flows", Map.of("name", "f", "graph", graph), String.class)
                .getStatusCode().value();
    }

    @Test
    @SuppressWarnings("unchecked")
    void edgeOnDeclaredModulePortIsAccepted() {
        Map<String, Object> created = rest.postForEntity("/api/modules",
                Map.of("name", "filter", "version", "1.0.0",
                        "specJson", "{\"in\":[\"in\"],\"out\":[\"result\",\"error\"]}", "source", SRC),
                Map.class).getBody();
        String id = (String) created.get("id");

        assertEquals(201, postFlow(id, "result"), "선언된 출력 포트 → 201");
        assertEquals(422, postFlow(id, "ghost"), "미선언 출력 포트 → 422");
    }
}
