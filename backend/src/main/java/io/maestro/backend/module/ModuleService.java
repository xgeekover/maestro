package io.maestro.backend.module;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ModuleService {

    private final ModuleRepository repository;

    public ModuleService(ModuleRepository repository) {
        this.repository = repository;
    }

    public ModuleEntity create(String name, String version, String specJson, String source) {
        ModuleEntity entity = new ModuleEntity(
                UUID.randomUUID().toString(), name, version,
                specJson == null ? "{}" : specJson, source, Instant.now());
        return repository.save(entity);
    }

    public List<ModuleEntity> list() {
        return repository.findAll();
    }

    public Optional<ModuleEntity> get(String id) {
        return repository.findById(id);
    }
}
