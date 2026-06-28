package io.maestro.runner.engine;

import io.maestro.runner.compile.Diag;

import java.util.List;

/** 엔진 라이프사이클 이벤트 관측 훅(텔레메트리/로그/대시보드 연동 지점). */
public interface LifecycleListener {

    default void onStateChange(LifecycleState from, LifecycleState to, String detail) {}

    default void onCompileDiagnostics(boolean success, List<Diag> diagnostics) {}

    default void onTickComplete(long tickNumber, long durationMs) {}

    default void onTickError(long tickNumber, Throwable error) {}

    default void onTickTimeout(long tickNumber, long timeoutMs) {}

    default void onLog(String message) {}

    LifecycleListener NOOP = new LifecycleListener() {};
}
