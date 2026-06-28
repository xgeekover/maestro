package io.maestro.runner.engine;

import io.maestro.sdk.Logger;

import java.io.PrintStream;

/**
 * 표준출력 기반 Logger 구현(단독 실행/테스트용). SLF4J 스타일 {@code {}} 플레이스홀더 지원.
 * Phase 4에서 텔레메트리(gRPC LogRecord)로 스트리밍하는 구현으로 대체/병행.
 */
public final class SimpleLogger implements Logger {

    private final PrintStream out;
    private final String name;

    public SimpleLogger(String name) {
        this(name, System.out);
    }

    public SimpleLogger(String name, PrintStream out) {
        this.name = name;
        this.out = out;
    }

    @Override public void trace(String msg, Object... args) { log("TRACE", msg, args); }
    @Override public void debug(String msg, Object... args) { log("DEBUG", msg, args); }
    @Override public void info(String msg, Object... args) { log("INFO", msg, args); }
    @Override public void warn(String msg, Object... args) { log("WARN", msg, args); }
    @Override public void error(String msg, Object... args) { log("ERROR", msg, args); }

    @Override
    public void error(String msg, Throwable t) {
        log("ERROR", msg);
        t.printStackTrace(out);
    }

    private void log(String level, String msg, Object... args) {
        out.printf("%-5s [%s] %s%n", level, name, format(msg, args));
    }

    /** {@code {}} 플레이스홀더를 순서대로 치환. */
    static String format(String msg, Object... args) {
        if (args == null || args.length == 0) {
            return msg;
        }
        StringBuilder sb = new StringBuilder(msg.length() + 16 * args.length);
        int argIdx = 0;
        int i = 0;
        while (i < msg.length()) {
            if (argIdx < args.length && i + 1 < msg.length()
                    && msg.charAt(i) == '{' && msg.charAt(i + 1) == '}') {
                sb.append(String.valueOf(args[argIdx++]));
                i += 2;
            } else {
                sb.append(msg.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }
}
