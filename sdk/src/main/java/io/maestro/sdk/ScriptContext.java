package io.maestro.sdk;

import java.util.function.Consumer;

/**
 * 스크립트가 런타임과 상호작용하는 컨텍스트 API.
 *
 * <p>로깅, 실행 파라미터, 플로우 메시지 송수신(노드레드식), 재시작 간 상태 저장을 제공한다.
 * 모든 메서드는 러너 프로세스 내부에서 동작하며, 플로우 메시지는 백엔드 릴레이를 통해
 * 하류/상류 노드로 전달된다(ADR-0001 D5).</p>
 */
public interface ScriptContext {

    /** 구조적 로깅 파사드. 로그는 텔레메트리 채널로 백엔드/대시보드에 스트리밍된다(FR-6). */
    Logger log();

    /**
     * 실행 파라미터 조회.
     *
     * @param key  파라미터 키
     * @param type 기대 타입(역직렬화 대상)
     * @return 변환된 값, 없으면 {@code null}
     */
    <T> T param(String key, Class<T> type);

    /**
     * 지정한 출력 포트로 메시지를 emit한다. 백엔드 라우팅을 거쳐 하류 노드로 전달된다(FR-7).
     * 플로우는 DAG로 강제되며 노드별 바운디드 큐 백프레셔가 적용된다(ADR-0002 O-9).
     *
     * @param port    출력 포트 이름
     * @param message 직렬화 가능한 메시지
     */
    void emit(String port, Object message);

    /**
     * 지정한 입력 포트의 상류 메시지를 수신할 핸들러를 등록한다.
     * 일반적으로 {@link Script#onStart()}에서 등록한다.
     *
     * @param port    입력 포트 이름
     * @param handler 메시지 소비 핸들러
     */
    void onMessage(String port, Consumer<Object> handler);

    /** (선택) 재시작 간 유지되는 키-값 상태 저장소. 재시작 정책과 함께 동작(ADR-0002 O-6). */
    KeyValueStore state();
}
