package io.maestro.backend.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/** 완료된 실행(run)의 영속 이력 (H-5 확장: 재시작 후에도 "무엇이 언제 어떻게 돌았는지" 조회). */
@Entity
@Table(name = "run_history")
public class RunHistoryEntity {

    @Id
    @Column(name = "run_id", length = 36)
    private String runId;

    @Column(name = "script_id")
    private String scriptId;

    @Column(name = "script_name")
    private String scriptName;

    @Column(length = 16)
    private String status;

    private long pid;

    @Column(name = "restart_count")
    private int restartCount;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Lob
    @Column(name = "last_error")
    private String lastError;

    @Column(name = "flow_id")
    private String flowId;

    @Column(name = "node_id")
    private String nodeId;

    protected RunHistoryEntity() {
    }

    public RunHistoryEntity(String runId, String scriptId, String scriptName, String status, long pid,
                            int restartCount, Instant startedAt, Instant endedAt, String lastError,
                            String flowId, String nodeId) {
        this.runId = runId;
        this.scriptId = scriptId;
        this.scriptName = scriptName;
        this.status = status;
        this.pid = pid;
        this.restartCount = restartCount;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.lastError = lastError;
        this.flowId = flowId;
        this.nodeId = nodeId;
    }

    public String getRunId() { return runId; }
    public String getScriptId() { return scriptId; }
    public String getScriptName() { return scriptName; }
    public String getStatus() { return status; }
    public long getPid() { return pid; }
    public int getRestartCount() { return restartCount; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public String getLastError() { return lastError; }
    public String getFlowId() { return flowId; }
    public String getNodeId() { return nodeId; }
}
