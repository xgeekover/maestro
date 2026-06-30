package io.maestro.backend.history;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RunHistoryRepository extends JpaRepository<RunHistoryEntity, String> {
}
