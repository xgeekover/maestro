package io.maestro.backend.process;

/** 백엔드가 추적하는 실행 상태(러너 라이프사이클 + 기동 대기). */
public enum RunStatus {
    PENDING,    // 프로세스 기동, Hello 대기
    COMPILING,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    ERROR;

    public boolean isTerminal() {
        return this == STOPPED || this == ERROR;
    }
}
