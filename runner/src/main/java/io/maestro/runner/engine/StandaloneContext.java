package io.maestro.runner.engine;

import io.maestro.sdk.KeyValueStore;
import io.maestro.sdk.Logger;
import io.maestro.sdk.ScriptContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 단독 실행(CLI/테스트)용 ScriptContext. 백엔드/플로우 없이 동작한다.
 *
 * <p>emit은 로컬 버퍼에 적재(+로그)하고, onMessage 핸들러는 {@link #deliver}로 시뮬레이션한다.
 * Phase 4/6에서 gRPC EmitMessage/DeliverMessage 기반 구현으로 대체.</p>
 */
public final class StandaloneContext implements ScriptContext {

    /** 단독 실행에서 관측 가능한 emit 레코드. */
    public record Emitted(String port, Object message) {}

    private final Logger logger;
    private final Map<String, String> params;
    private final KeyValueStore state = new InMemoryKeyValueStore();
    private final Map<String, Consumer<Object>> handlers = new ConcurrentHashMap<>();
    private final List<Emitted> emitted = new CopyOnWriteArrayList<>();

    public StandaloneContext(String name, Map<String, String> params) {
        this.logger = new SimpleLogger(name);
        this.params = Map.copyOf(params);
    }

    @Override
    public Logger log() {
        return logger;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T param(String key, Class<T> type) {
        String raw = params.get(key);
        if (raw == null) {
            return null;
        }
        if (type == String.class) return (T) raw;
        if (type == Integer.class) return (T) Integer.valueOf(raw);
        if (type == Long.class) return (T) Long.valueOf(raw);
        if (type == Double.class) return (T) Double.valueOf(raw);
        if (type == Boolean.class) return (T) Boolean.valueOf(raw);
        throw new IllegalArgumentException("지원하지 않는 파라미터 타입: " + type.getName());
    }

    @Override
    public void emit(String port, Object message) {
        emitted.add(new Emitted(port, message));
        logger.debug("emit[{}] {}", port, message);
    }

    @Override
    public void onMessage(String port, Consumer<Object> handler) {
        handlers.put(port, handler);
    }

    @Override
    public KeyValueStore state() {
        return state;
    }

    // ---- 테스트/CLI 관측·시뮬레이션 훅 ----

    /** 상류 메시지 도착을 시뮬레이션해 등록된 핸들러를 호출한다. */
    public void deliver(String port, Object message) {
        Consumer<Object> handler = handlers.get(port);
        if (handler != null) {
            handler.accept(message);
        }
    }

    public List<Emitted> emitted() {
        return List.copyOf(emitted);
    }
}
