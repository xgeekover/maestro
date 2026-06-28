package io.maestro.sdk;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SDK 계약 테스트: 라이프사이클 기본 동작·컨텍스트 주입·ScriptContext/KeyValueStore 형상. */
class ScriptContractTest {

    static final class Probe extends Script {
        boolean started;
        boolean ended;
        int ticks;

        @Override public void onStart() { started = true; }
        @Override public void onTick() { ticks++; }
        @Override public void onEnd() { ended = true; }

        ScriptContext context() { return ctx; }
    }

    private static ScriptContext stubContext() {
        return new ScriptContext() {
            @Override public Logger log() { return null; }
            @Override public <T> T param(String key, Class<T> type) { return null; }
            @Override public void emit(String port, Object message) { }
            @Override public void onMessage(String port, Consumer<Object> handler) { }
            @Override public KeyValueStore state() { return null; }
        };
    }

    @Test
    void defaultLifecycleMethodsAreNoop() {
        Script s = new Script() { };
        assertDoesNotThrow(s::onStart);
        assertDoesNotThrow(s::onTick);
        assertDoesNotThrow(s::onEnd);
    }

    @Test
    void bindInjectsContext() {
        Probe p = new Probe();
        assertNull(p.context());
        ScriptContext ctx = stubContext();
        p.__bind(ctx);
        assertSame(ctx, p.context());
    }

    @Test
    void lifecycleOverridesInvoked() {
        Probe p = new Probe();
        p.onStart();
        p.onTick();
        p.onTick();
        p.onEnd();
        assertTrue(p.started);
        assertEquals(2, p.ticks);
        assertTrue(p.ended);
    }

    @Test
    void keyValueStoreContract() {
        KeyValueStore kv = new KeyValueStore() {
            private final Map<String, Object> m = new HashMap<>();
            @Override public <T> Optional<T> get(String k, Class<T> t) { return Optional.ofNullable(t.cast(m.get(k))); }
            @Override public void put(String k, Object v) { m.put(k, v); }
            @Override public void remove(String k) { m.remove(k); }
            @Override public boolean contains(String k) { return m.containsKey(k); }
        };
        kv.put("a", 1);
        assertEquals(1, kv.get("a", Integer.class).orElse(0));
        assertTrue(kv.contains("a"));
        kv.remove("a");
        assertFalse(kv.contains("a"));
    }
}
