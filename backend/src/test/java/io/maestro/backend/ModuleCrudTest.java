package io.maestro.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 모듈 저작 T2: 생성 → 조회(소스 포함) → 수정(제자리) → 삭제 → 404 라이프사이클.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"maestro.grpc.port=0"})
class ModuleCrudTest {

    private static final String SRC =
            "import io.maestro.sdk.Script; public class M extends Script { @Override public void onTick(){} }";

    @Autowired
    private TestRestTemplate rest;

    @SuppressWarnings("unchecked")
    @Test
    void createReadUpdateDeleteLifecycle() {
        // 생성
        ResponseEntity<Map> created = rest.postForEntity("/api/modules",
                Map.of("name", "mod", "version", "1.0.0", "specJson", "{\"out\":[\"out\"]}", "source", SRC),
                Map.class);
        assertEquals(201, created.getStatusCode().value());
        String id = (String) created.getBody().get("id");
        assertNotNull(id);
        assertEquals(SRC, created.getBody().get("source"), "응답에 source 포함");

        // 조회 — 소스 확인
        ResponseEntity<Map> got = rest.getForEntity("/api/modules/" + id, Map.class);
        assertEquals(200, got.getStatusCode().value());
        assertEquals("1.0.0", got.getBody().get("version"));

        // 수정 — 제자리(같은 id) 버전/소스 변경
        String src2 = SRC.replace("onTick(){}", "onTick(){ ctx.log().info(\"v2\"); }");
        ResponseEntity<Map> updated = rest.exchange("/api/modules/" + id, HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "mod", "version", "1.1.0", "specJson", "{}", "source", src2)),
                Map.class);
        assertEquals(200, updated.getStatusCode().value());
        assertEquals(id, updated.getBody().get("id"), "id 유지");
        assertEquals("1.1.0", updated.getBody().get("version"));
        assertEquals(src2, updated.getBody().get("source"));

        // 삭제
        ResponseEntity<Void> deleted = rest.exchange("/api/modules/" + id, HttpMethod.DELETE,
                null, Void.class);
        assertEquals(204, deleted.getStatusCode().value());

        // 삭제 후 조회 → 404
        assertEquals(404, rest.getForEntity("/api/modules/" + id, String.class).getStatusCode().value());
    }

    @Test
    void updateAndDeleteMissingAre404() {
        assertEquals(404, rest.exchange("/api/modules/ghost", HttpMethod.PUT,
                new HttpEntity<>(Map.of("name", "x", "version", "1.0.0", "specJson", "{}", "source", SRC)),
                String.class).getStatusCode().value());
        assertEquals(404, rest.exchange("/api/modules/ghost", HttpMethod.DELETE,
                null, String.class).getStatusCode().value());
    }
}
