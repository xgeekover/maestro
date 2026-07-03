package io.maestro.backend.state;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * 스크립트/노드 KV 상태의 영속 저장소(재시작 간 유지). 러너의 {@code ScriptContext.state()} 요청을
 * gRPC 게이트웨이가 이 서비스로 위임한다(put=upsert, get/contains=조회, remove=삭제).
 */
@Service
public class ScriptStateService {

    private final ScriptStateRepository repository;

    public ScriptStateService(ScriptStateRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void put(String owner, String key, String value) {
        ScriptStateEntity e = repository.findById(new ScriptStateId(owner, key))
                .orElseGet(() -> new ScriptStateEntity(owner, key));
        e.setValueJson(value);
        e.setUpdatedAt(Instant.now());
        repository.save(e);
    }

    @Transactional(readOnly = true)
    public Optional<String> get(String owner, String key) {
        return repository.findById(new ScriptStateId(owner, key))
                .map(ScriptStateEntity::getValueJson);
    }

    @Transactional
    public void remove(String owner, String key) {
        repository.deleteById(new ScriptStateId(owner, key));
    }

    @Transactional(readOnly = true)
    public boolean contains(String owner, String key) {
        return repository.existsById(new ScriptStateId(owner, key));
    }
}
