package io.maestro.sdk;

import java.util.Optional;

/**
 * 재시작 간 유지되는 선택적 키-값 상태 저장소.
 *
 * <p>러너 사망 후 재시작 정책(ADR-0002 O-6)에 따라 다시 기동될 때, 스크립트가 이전 상태를
 * 복원하는 데 사용한다. 영속 백엔드는 backend(H2)이며, 러너는 텔레메트리 채널을 통해 접근한다.</p>
 *
 * <p>값은 직렬화 가능해야 하며, 구현은 최종적 일관성(eventual consistency)을 가질 수 있다.</p>
 */
public interface KeyValueStore {

    <T> Optional<T> get(String key, Class<T> type);

    void put(String key, Object value);

    void remove(String key);

    boolean contains(String key);
}
