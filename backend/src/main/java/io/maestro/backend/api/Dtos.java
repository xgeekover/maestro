package io.maestro.backend.api;

import io.maestro.backend.domain.ScriptEntity;
import io.maestro.backend.flow.FlowEntity;
import io.maestro.backend.flow.FlowModel.FlowGraph;
import io.maestro.backend.module.ModuleEntity;
import io.maestro.backend.process.RunInfo;

import java.time.Instant;
import java.util.Map;

/** REST 요청/응답 DTO 모음. */
public final class Dtos {

    private Dtos() {
    }

    public record CreateScriptRequest(String name, String source) {}

    public record ScriptResponse(String id, String name, String source, String sourceHash,
                                 Instant createdAt, Instant updatedAt) {
        public static ScriptResponse of(ScriptEntity e) {
            return new ScriptResponse(e.getId(), e.getName(), e.getSource(), e.getSourceHash(),
                    e.getCreatedAt(), e.getUpdatedAt());
        }
    }

    public record CreateRunRequest(
            String scriptId,
            Long tickPeriodMs,
            Map<String, String> params,
            Boolean stopOnError,
            Long maxHeapBytes,
            Long tickTimeoutMs,
            Long errorThreshold) {}

    public record RunResponse(String runId, String scriptId, String scriptName, String status,
                              long pid, int restartCount, Instant startedAt, String lastError) {
        public static RunResponse of(RunInfo r) {
            return new RunResponse(r.runId(), r.scriptId(), r.scriptName(), r.status().name(),
                    r.pid(), r.restartCount(), r.startedAt(), r.lastError());
        }
    }

    // ---- 플로우 ----
    public record CreateFlowRequest(String name, FlowGraph graph) {}

    public record FlowResponse(String id, String name, FlowGraph graph, Instant createdAt, Instant updatedAt) {
        public static FlowResponse of(FlowEntity e, FlowGraph graph) {
            return new FlowResponse(e.getId(), e.getName(), graph, e.getCreatedAt(), e.getUpdatedAt());
        }
    }

    public record DeployResponse(String flowId, Map<String, String> nodeRuns) {}

    // ---- 모듈 ----
    public record CreateModuleRequest(String name, String version, String specJson, String source) {}

    public record ModuleResponse(String id, String name, String version, String specJson, Instant createdAt) {
        public static ModuleResponse of(ModuleEntity m) {
            return new ModuleResponse(m.getId(), m.getName(), m.getVersion(), m.getSpecJson(), m.getCreatedAt());
        }
    }
}
