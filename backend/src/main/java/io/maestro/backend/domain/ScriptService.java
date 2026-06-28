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

    private static String hash(String source) {
        return Integer.toHexString(source.hashCode());
    }
}
