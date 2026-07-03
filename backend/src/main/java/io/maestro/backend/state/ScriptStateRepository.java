package io.maestro.backend.state;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScriptStateRepository extends JpaRepository<ScriptStateEntity, ScriptStateId> {
}
