package io.maestro.runner.compile;

import java.util.List;

/**
 * 동적 컴파일 결과: 성공 여부 + 진단 목록 + (성공 시) 로드된 스크립트 클래스.
 */
public final class CompilationResult {

    private final boolean success;
    private final String className;
    private final List<Diag> diagnostics;
    private final Class<?> scriptClass;

    private CompilationResult(boolean success, String className, List<Diag> diagnostics, Class<?> scriptClass) {
        this.success = success;
        this.className = className;
        this.diagnostics = List.copyOf(diagnostics);
        this.scriptClass = scriptClass;
    }

    static CompilationResult success(String className, List<Diag> diagnostics, Class<?> scriptClass) {
        return new CompilationResult(true, className, diagnostics, scriptClass);
    }

    static CompilationResult failure(String className, List<Diag> diagnostics) {
        return new CompilationResult(false, className, diagnostics, null);
    }

    public boolean success() {
        return success;
    }

    public String className() {
        return className;
    }

    public List<Diag> diagnostics() {
        return diagnostics;
    }

    /** 성공 시 로드된 클래스, 실패 시 {@code null}. */
    public Class<?> scriptClass() {
        return scriptClass;
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.kind() == Diag.Kind.ERROR);
    }
}
