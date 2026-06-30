package io.maestro.backend.api;

import io.maestro.backend.support.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 표준 에러 응답 매핑 (QA H-3). 내부 예외/스택을 누출하지 않고 일관된 HTTP 의미를 제공한다.
 * 엔벨로프: {@code {status, error, details?}}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static Map<String, Object> body(HttpStatus status, String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status.value());
        m.put("error", error);
        return m;
    }

    /** 리소스 없음 → 404. */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    /** Bean Validation 실패 → 400 + 필드별 메시지. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        Map<String, Object> b = body(HttpStatus.BAD_REQUEST, "요청 검증 실패");
        b.put("details", fields);
        return ResponseEntity.badRequest().body(b);
    }

    /** 본문 파싱 실패(깨진 JSON 등) → 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없습니다"));
    }

    /** DB 무결성 위반(검증을 통과한 비정상 데이터) → 400. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> dataIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, "데이터 제약 위반"));
    }

    /** 도메인 규칙 위반(예: 플로우 사이클·잘못된 참조) → 422. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> illegalArgument(IllegalArgumentException e) {
        return ResponseEntity.unprocessableEntity().body(body(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()));
    }

    /**
     * 서버 내부 불변 위반(그래프 역직렬화 손상·환경 문제 등) → 500(내부 메시지 비노출, 로그만).
     *
     * <p>그 외 미처리 예외와 프레임워크 상태 예외(미지정 경로 404, 405 등)는 Spring Boot 기본
     * 에러 처리에 위임한다(기본 설정상 스택/메시지 비노출이며 올바른 상태코드 유지).</p>
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> illegalState(IllegalStateException e) {
        log.error("내부 상태 오류", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(HttpStatus.INTERNAL_SERVER_ERROR, "내부 서버 오류"));
    }
}
