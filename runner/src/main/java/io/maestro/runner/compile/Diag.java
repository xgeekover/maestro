package io.maestro.runner.compile;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/** 컴파일러 진단(에러/경고/노트)을 UI/백엔드 전달용으로 단순화한 레코드. */
public record Diag(Kind kind, long line, long column, String code, String message) {

    public enum Kind { ERROR, WARNING, NOTE }

    static Diag from(Diagnostic<? extends JavaFileObject> d) {
        Kind kind = switch (d.getKind()) {
            case ERROR -> Kind.ERROR;
            case WARNING, MANDATORY_WARNING -> Kind.WARNING;
            default -> Kind.NOTE;
        };
        return new Diag(kind, d.getLineNumber(), d.getColumnNumber(), d.getCode(),
                d.getMessage(null));
    }

    @Override
    public String toString() {
        return "[%s] line %d:%d %s".formatted(kind, line, column, message);
    }
}
