package io.maestro.backend.domain;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ScriptService {

    private final ScriptRepository repository;

    public ScriptService(ScriptRepository repository) {
        this.repository = repository;
    }

    public ScriptEntity create(String name, String source) {
        Instant now = Instant.now();
        ScriptEntity entity = new ScriptEntity(
                UUID.randomUUID().toString(), name, source, hash(source), now, now);
        return repository.save(entity);
    }

    public List<ScriptEntity> list() {
        return repository.findAll();
    }

    public Optional<ScriptEntity> get(String id) {
        return repository.findById(id);
    }

    public Optional<ScriptEntity> update(String id, String name, String source) {
        return repository.findById(id).map(entity -> {
            entity.update(name, source, hash(source), Instant.now());
            return repository.save(entity);
        });
    }

    public boolean delete(String id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return true;
        }
        return false;
    }

    private static String hash(String source) {
        return Integer.toHexString(source.hashCode());
    }
}
