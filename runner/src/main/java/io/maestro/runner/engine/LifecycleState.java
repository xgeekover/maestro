package io.maestro.runner.engine;

/** 러너 라이프사이클 상태 (docs/01-architecture.md §4 상태기계와 대응). */
public enum LifecycleState {
    NEW,
    COMPILING,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    ERROR
}
