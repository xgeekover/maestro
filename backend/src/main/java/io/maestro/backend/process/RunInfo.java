package io.maestro.backend.process;

import io.grpc.stub.StreamObserver;
import io.maestro.protocol.v1.BackendMessage;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/** 한 실행(run)의 가변 상태. 라이브 프로세스 상태이므로 인메모리(RunRegistry). */
public final class RunInfo {

    private final String runId;
    private final String scriptId;
    private final String scriptName;
    private final String source;
    private final RunConfig config;

    private volatile String runnerId;          // 현재 시도의 러너 ID
    private volatile String token;             // 현재 시도의 핸드셰이크 토큰
    private volatile Process process;
    private volatile long pid = -1;
    private volatile RunStatus status = RunStatus.PENDING;
    private volatile Instant startedAt;
    private final AtomicInteger restartCount = new AtomicInteger();
    private volatile boolean userStopped = false;
    private volatile String lastError;
    private volatile StreamObserver<BackendMessage> commandStream;
    private volatile long lastTelemetryNanos = System.nanoTime();
    private volatile long terminalSinceNanos = 0; // 종료(STOPPED/ERROR) 최초 진입 시각 → 회수 판정
    private volatile long deathPendingSinceNanos = 0; // 프로세스 사망 최초 감지 시각 → 재시작 grace(H-7)
    private volatile boolean historyRecorded = false; // 종료 이력 영속 1회 기록 여부
    private volatile String flowId;   // 플로우 배포로 기동된 경우
    private volatile String nodeId;

    public RunInfo(String runId, String scriptId, String scriptName, String source, RunConfig config) {
        this.runId = runId;
        this.scriptId = scriptId;
        this.scriptName = scriptName;
        this.source = source;
        this.config = config;
    }

    public String runId() { return runId; }
    public String scriptId() { return scriptId; }
    public String scriptName() { return scriptName; }
    public String source() { return source; }
    public RunConfig config() { return config; }

    public String runnerId() { return runnerId; }
    public void setRunnerId(String v) { this.runnerId = v; }
    public String token() { return token; }
    public void setToken(String v) { this.token = v; }

    public Process process() { return process; }
    public void setProcess(Process p) { this.process = p; this.pid = p == null ? -1 : p.pid(); }
    public long pid() { return pid; }

    public RunStatus status() { return status; }
    public void setStatus(RunStatus s) {
        if (s.isTerminal() && !this.status.isTerminal()) {
            this.terminalSinceNanos = System.nanoTime();
        }
        this.status = s;
    }
    public long terminalSinceNanos() { return terminalSinceNanos; }

    public long deathPendingSinceNanos() { return deathPendingSinceNanos; }
    public void setDeathPendingSinceNanos(long v) { this.deathPendingSinceNanos = v; }

    public boolean isHistoryRecorded() { return historyRecorded; }
    public void setHistoryRecorded(boolean v) { this.historyRecorded = v; }

    public Instant startedAt() { return startedAt; }
    public void setStartedAt(Instant t) { this.startedAt = t; }

    public int restartCount() { return restartCount.get(); }
    public int incrementRestart() { return restartCount.incrementAndGet(); }

    public boolean userStopped() { return userStopped; }
    public void setUserStopped(boolean v) { this.userStopped = v; }

    public String lastError() { return lastError; }
    public void setLastError(String v) { this.lastError = v; }

    public StreamObserver<BackendMessage> commandStream() { return commandStream; }
    public void setCommandStream(StreamObserver<BackendMessage> s) { this.commandStream = s; }

    /** 명령 송신을 스트림별로 직렬화(gRPC StreamObserver는 스레드 안전하지 않음). */
    public synchronized void sendCommand(BackendMessage msg) {
        StreamObserver<BackendMessage> s = commandStream;
        if (s != null) {
            s.onNext(msg);
        }
    }

    public void touchTelemetry() { this.lastTelemetryNanos = System.nanoTime(); }
    public long lastTelemetryNanos() { return lastTelemetryNanos; }

    public String flowId() { return flowId; }
    public void setFlowId(String v) { this.flowId = v; }
    public String nodeId() { return nodeId; }
    public void setNodeId(String v) { this.nodeId = v; }
}
