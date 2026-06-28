package io.maestro.backend.process;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/** 인메모리 실행 레지스트리. runId 및 (현재 시도의) runnerId 로 조회. */
@Component
public class RunRegistry {

    private final ConcurrentHashMap<String, RunInfo> byRunId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RunInfo> byRunnerId = new ConcurrentHashMap<>();

    public void register(RunInfo run) {
        byRunId.put(run.runId(), run);
        if (run.runnerId() != null) {
            byRunnerId.put(run.runnerId(), run);
        }
    }

    /** 재시작 등으로 runnerId가 바뀔 때 인덱스 갱신. */
    public void rebindRunner(RunInfo run, String oldRunnerId, String newRunnerId) {
        if (oldRunnerId != null) {
            byRunnerId.remove(oldRunnerId);
        }
        run.setRunnerId(newRunnerId);
        byRunnerId.put(newRunnerId, run);
    }

    public RunInfo byRunId(String runId) {
        return byRunId.get(runId);
    }

    public RunInfo byRunnerId(String runnerId) {
        return byRunnerId.get(runnerId);
    }

    public Collection<RunInfo> all() {
        return byRunId.values();
    }
}
