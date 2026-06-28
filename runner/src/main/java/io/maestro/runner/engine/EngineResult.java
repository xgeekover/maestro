package io.maestro.runner.engine;

import io.maestro.runner.compile.Diag;

import java.util.List;

/**
 * 엔진 실행 결과. 라이프사이클 보장을 검증할 수 있는 카운트를 노출한다(DoD).
 *
 * @param finalState   종료 상태(STOPPED 정상 / ERROR)
 * @param onStartCount onStart 성공 호출 수 (보장: 0 또는 1)
 * @param tickCount    onTick 호출 시도 수
 * @param tickErrorCount onTick 중 예외/타임아웃 발생 수
 * @param onEndCount   onEnd 호출 수 (보장: 인스턴스 생성 시 정확히 1)
 * @param diagnostics  컴파일 진단
 * @param failure      치명 실패 원인(있을 때)
 */
public record EngineResult(
        LifecycleState finalState,
        int onStartCount,
        long tickCount,
        long tickErrorCount,
        int onEndCount,
        List<Diag> diagnostics,
        Throwable failure
) {
    public boolean isError() {
        return finalState == LifecycleState.ERROR;
    }
}
