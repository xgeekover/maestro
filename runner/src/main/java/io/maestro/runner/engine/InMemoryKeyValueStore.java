package io.maestro.runner.engine;

import io.maestro.sdk.KeyValueStore;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 인메모리 KeyValueStore 구현(단독 실행/테스트용).
 * Phase 4에서 백엔드 H2(script_state) 영속 구현으로 대체/병행.
 */
public final class InMemoryKeyValueStore implements KeyValueStore {

    private final Map<String, Object> map = new ConcurrentHashMap<>();

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        Object v = map.get(key);
        if (v == null) {
            return Optional.empty();
        }
        return Optional.of(type.cast(v));
    }

    @Override
    public void put(String key, Object value) {
        if (value == null) {
            map.remove(key);
        } else {
            map.put(key, value);
        }
    }

    @Override
    public void remove(String key) {
        map.remove(key);
    }

    @Override
    public boolean contains(String key) {
        return map.containsKey(key);
    }
}
