package io.maestro.backend.history;

import io.maestro.backend.process.RunInfo;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class RunHistoryService {

    private final RunHistoryRepository repository;

    public RunHistoryService(RunHistoryRepository repository) {
        this.repository = repository;
    }

    /** 종료된 실행을 이력에 기록(runId PK → 재기록 시 멱등). */
    public void record(RunInfo run) {
        RunHistoryEntity entity = new RunHistoryEntity(
                run.runId(), run.scriptId(), run.scriptName(), run.status().name(),
                run.pid(), run.restartCount(), run.startedAt(), Instant.now(),
                run.lastError(), run.flowId(), run.nodeId());
        repository.save(entity);
    }

    /** 최근 이력(종료시각 내림차순, 페이지네이션). */
    public List<RunHistoryEntity> recent(int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(Math.max(1, size), 500);
        return repository.findAll(PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "endedAt"))).getContent();
    }

    public Optional<RunHistoryEntity> get(String runId) {
        return repository.findById(runId);
    }
}
