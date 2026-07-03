package io.maestro.backend.state;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 상태 영속 저장소 단위 검증: put(upsert)·get·remove·contains + owner 격리. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {"maestro.grpc.port=0"})
class ScriptStateServiceTest {

    @Autowired
    private ScriptStateService svc;

    @Test
    void putGetRemoveContains() {
        assertTrue(svc.get("o1", "k").isEmpty());
        assertFalse(svc.contains("o1", "k"));

        svc.put("o1", "k", "42");
        assertEquals("42", svc.get("o1", "k").orElse(null));
        assertTrue(svc.contains("o1", "k"));

        svc.put("o1", "k", "43"); // upsert
        assertEquals("43", svc.get("o1", "k").orElse(null));

        svc.remove("o1", "k");
        assertTrue(svc.get("o1", "k").isEmpty());
        assertFalse(svc.contains("o1", "k"));
    }

    @Test
    void isolatedByOwner() {
        svc.put("owA", "k", "1");
        svc.put("owB", "k", "2");
        assertEquals("1", svc.get("owA", "k").orElse(null));
        assertEquals("2", svc.get("owB", "k").orElse(null));
    }
}
