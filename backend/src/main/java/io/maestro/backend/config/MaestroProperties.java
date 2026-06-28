package io.maestro.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Maestro 오케스트레이터 설정. {@code maestro.*} (application.yaml). */
@ConfigurationProperties(prefix = "maestro")
public class MaestroProperties {

    private final Grpc grpc = new Grpc();
    private final Runner runner = new Runner();
    private final Restart restart = new Restart();
    private final Buffer buffer = new Buffer();

    public Grpc getGrpc() { return grpc; }
    public Runner getRunner() { return runner; }
    public Restart getRestart() { return restart; }
    public Buffer getBuffer() { return buffer; }

    /** 러너 텔레메트리 수신 gRPC 서버. */
    public static class Grpc {
        /** 0이면 임의 포트(테스트). 기본 9090. */
        private int port = 9090;
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
    }

    /** 러너 프로세스 기동 설정. */
    public static class Runner {
        /** 러너 JVM 실행 파일. 비면 현재 java.home/bin/java. */
        private String javaExecutable = "";
        /** 러너 클래스패스. 비면 현재 java.class.path(개발/테스트). 운영은 명시(Phase 10). */
        private String classpath = "";
        /** 백엔드 접속 호스트(러너가 본 백엔드 주소). */
        private String backendHost = "127.0.0.1";
        /** graceful 중지 대기(ms). 초과 시 강제 종료. */
        private long stopGraceMs = 5000;
        /** 메트릭 보고 주기(ms) — 러너에 전달(현재 러너 기본 1000 사용). */
        private long metricPeriodMs = 1000;

        public String getJavaExecutable() { return javaExecutable; }
        public void setJavaExecutable(String v) { this.javaExecutable = v; }
        public String getClasspath() { return classpath; }
        public void setClasspath(String v) { this.classpath = v; }
        public String getBackendHost() { return backendHost; }
        public void setBackendHost(String v) { this.backendHost = v; }
        public long getStopGraceMs() { return stopGraceMs; }
        public void setStopGraceMs(long v) { this.stopGraceMs = v; }
        public long getMetricPeriodMs() { return metricPeriodMs; }
        public void setMetricPeriodMs(long v) { this.metricPeriodMs = v; }
    }

    /** 재시작 정책 (ADR-0002 O-6: exponential backoff + 최대 횟수). */
    public static class Restart {
        private long baseDelayMs = 500;
        private long maxDelayMs = 30000;
        private int maxAttempts = 5;
        /** 워치독 점검 주기(ms). */
        private long watchdogPeriodMs = 500;

        public long getBaseDelayMs() { return baseDelayMs; }
        public void setBaseDelayMs(long v) { this.baseDelayMs = v; }
        public long getMaxDelayMs() { return maxDelayMs; }
        public void setMaxDelayMs(long v) { this.maxDelayMs = v; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int v) { this.maxAttempts = v; }
        public long getWatchdogPeriodMs() { return watchdogPeriodMs; }
        public void setWatchdogPeriodMs(long v) { this.watchdogPeriodMs = v; }
    }

    /** 메트릭/로그 인메모리 링버퍼 크기(프로세스당) + 플로우 라우팅 큐 용량. */
    public static class Buffer {
        private int metricCapacity = 600;  // ~10분 @1s
        private int logCapacity = 500;
        private int flowQueueCapacity = 1000;  // 플로우 배포당 바운디드 큐(백프레셔)
        public int getMetricCapacity() { return metricCapacity; }
        public void setMetricCapacity(int v) { this.metricCapacity = v; }
        public int getLogCapacity() { return logCapacity; }
        public void setLogCapacity(int v) { this.logCapacity = v; }
        public int getFlowQueueCapacity() { return flowQueueCapacity; }
        public void setFlowQueueCapacity(int v) { this.flowQueueCapacity = v; }
    }
}
