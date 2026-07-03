package io.maestro.runner.grpc;

import io.maestro.protocol.v1.StateResult;
import io.maestro.sdk.KeyValueStore;

import java.util.Optional;

/**
 * 백엔드 영속 저장소를 사용하는 KeyValueStore(재시작 간 유지). {@link StateClient}로 gRPC 왕복.
 * 값은 문자열로 인코딩(String/Integer/Long/Double/Boolean) — emit/param과 동일한 규칙.
 */
public final class RemoteKeyValueStore implements KeyValueStore {

    private final StateClient client;

    public RemoteKeyValueStore(StateClient client) {
        this.client = client;
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        StateResult r = client.get(key);
        if (r == null || !r.getFound()) {
            return Optional.empty();
        }
        return Optional.ofNullable(convert(r.getValueJson().toStringUtf8(), type));
    }

    @Override
    public void put(String key, Object value) {
        client.put(key, String.valueOf(value));
    }

    @Override
    public void remove(String key) {
        client.remove(key);
    }

    @Override
    public boolean contains(String key) {
        StateResult r = client.contains(key);
        return r != null && r.getFound();
    }

    @SuppressWarnings("unchecked")
    private static <T> T convert(String raw, Class<T> type) {
        if (type == String.class) return (T) raw;
        if (type == Integer.class) return (T) Integer.valueOf(raw);
        if (type == Long.class) return (T) Long.valueOf(raw);
        if (type == Double.class) return (T) Double.valueOf(raw);
        if (type == Boolean.class) return (T) Boolean.valueOf(raw);
        throw new IllegalArgumentException("지원하지 않는 상태 타입: " + type.getName());
    }
}
