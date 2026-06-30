package io.maestro.backend.api;

import io.maestro.backend.domain.ScriptEntity;
import io.maestro.backend.flow.FlowEntity;
import io.maestro.backend.flow.FlowModel.FlowGraph;
import io.maestro.backend.history.RunHistoryEntity;
import io.maestro.backend.module.ModuleEntity;
import io.maestro.backend.process.RunInfo;
import io.maestro.backend.telemetry.MetricSnapshot;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

/** REST 요청/응답 DTO 모음. */
public final class Dtos {

    private Dtos() {
    }

    /** 스크립트 소스 최대 크기(QA H-2: 초대용량 소스 방지). 256KB. */
    public static final int MAX_SOURCE_BYTES = 256 * 1024;

    public record CreateScriptRequest(
            @NotBlank(message = "name은 비어 있을 수 없습니다") String name,
            @NotBlank(message = "source는 비어 있을 수 없습니다")
            @Size(max = MAX_SOURCE_BYTES, message = "source가 최대 크기(256KB)를 초과했습니다") String source) {}

    public record ScriptResponse(String id, String name, String source, String sourceHash,
                                 Instant createdAt, Instant updatedAt) {
        public static ScriptResponse of(ScriptEntity e) {
            return new ScriptResponse(e.getId(), e.getName(), e.getSource(), e.getSourceHash(),
                    e.getCreatedAt(), e.getUpdatedAt());
        }
    }

    public record UpdatePeriodRequest(
            @NotNull(message = "tickPeriodMs는 필수입니다")
            @Positive(message = "tickPeriodMs는 양수여야 합니다") Long tickPeriodMs) {}

    // 0/null = "기본값 사용"(센티넬), 음수 = 거부 → @PositiveOrZero
    public record CreateRunRequest(
            @NotBlank(message = "scriptId는 필수입니다") String scriptId,
            @PositiveOrZero(message = "tickPeriodMs는 음수일 수 없습니다") Long tickPeriodMs,
            Map<String, String> params,
            Boolean stopOnError,
            @PositiveOrZero(message = "maxHeapBytes는 음수일 수 없습니다") Long maxHeapBytes,
            @PositiveOrZero(message = "tickTimeoutMs는 음수일 수 없습니다") Long tickTimeoutMs,
            @PositiveOrZero(message = "errorThreshold는 음수일 수 없습니다") Long errorThreshold) {}

    public record RunResponse(String runId, String scriptId, String scriptName, String status,
                              long pid, int restartCount, Instant startedAt, String lastError) {
        public static RunResponse of(RunInfo r) {
            return new RunResponse(r.runId(), r.scriptId(), r.scriptName(), r.status().name(),
                    r.pid(), r.restartCount(), r.startedAt(), r.lastError());
        }
    }

    // ---- 플로우 ----
    public record CreateFlowRequest(
            @NotBlank(message = "name은 필수입니다") String name,
            @NotNull(message = "graph는 필수입니다") FlowGraph graph) {}

    public record FlowResponse(String id, String name, FlowGraph graph, Instant createdAt, Instant updatedAt) {
        public static FlowResponse of(FlowEntity e, FlowGraph graph) {
            return new FlowResponse(e.getId(), e.getName(), graph, e.getCreatedAt(), e.getUpdatedAt());
        }
    }

    public record DeployResponse(String flowId, Map<String, String> nodeRuns) {}

    // ---- 대시보드 ----
    public record RunSummary(RunResponse run, MetricSnapshot latest) {}

    // ---- 실행 이력 ----
    public record RunHistoryResponse(String runId, String scriptId, String scriptName, String status,
                                     long pid, int restartCount, Instant startedAt, Instant endedAt,
                                     String lastError, String flowId, String nodeId) {
        public static RunHistoryResponse of(RunHistoryEntity e) {
            return new RunHistoryResponse(e.getRunId(), e.getScriptId(), e.getScriptName(), e.getStatus(),
                    e.getPid(), e.getRestartCount(), e.getStartedAt(), e.getEndedAt(), e.getLastError(),
                    e.getFlowId(), e.getNodeId());
        }
    }

    // ---- 모듈 ----
    public record CreateModuleRequest(
            @NotBlank(message = "name은 필수입니다") String name,
            @NotBlank(message = "version은 필수입니다") String version,
            String specJson,
            @NotBlank(message = "source는 필수입니다")
            @Size(max = MAX_SOURCE_BYTES, message = "source가 최대 크기(256KB)를 초과했습니다") String source) {}

    public record ModuleResponse(String id, String name, String version, String specJson, Instant createdAt) {
        public static ModuleResponse of(ModuleEntity m) {
            return new ModuleResponse(m.getId(), m.getName(), m.getVersion(), m.getSpecJson(), m.getCreatedAt());
        }
    }
}
