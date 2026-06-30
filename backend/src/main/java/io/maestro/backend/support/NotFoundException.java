package io.maestro.backend.support;

/** 리소스를 찾을 수 없을 때 → HTTP 404 (QA H-3: 없는 리소스를 422가 아닌 404로). */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
