package io.maestro.sdk;

/**
 * 스크립트용 구조적 로깅 파사드. 구현은 러너가 제공하며, 로그 레코드는 텔레메트리
 * 채널(gRPC 스트리밍)을 통해 백엔드로 전달되어 대시보드에 실시간 표시된다(FR-6, NFR-2).
 *
 * <p>메시지는 SLF4J 스타일 {@code {}} 플레이스홀더를 지원한다.</p>
 */
public interface Logger {
    void trace(String msg, Object... args);
    void debug(String msg, Object... args);
    void info(String msg, Object... args);
    void warn(String msg, Object... args);
    void error(String msg, Object... args);

    /** 예외 동반 에러 로깅. */
    void error(String msg, Throwable t);
}
