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
    public void setStatus(RunStatus s) { this.status = s; }

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

    public void touchTelemetry() { this.lastTelemetryNanos = System.nanoTime(); }
    public long lastTelemetryNanos() { return lastTelemetryNanos; }
}
