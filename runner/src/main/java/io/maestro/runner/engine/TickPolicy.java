package io.maestro.runner.engine;

/** onTick 예외 처리 정책 (ADR-0002 O-5). */
public enum TickPolicy {
    /** 예외를 격리하고 다음 tick 진행(기본). 누적 에러가 임계 초과 시 중지. */
    CONTINUE,
    /** 예외 발생 시 즉시 onEnd 후 중지. */
    STOP
}
