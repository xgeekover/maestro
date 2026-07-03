package io.maestro.runner.grpc;

import com.google.protobuf.ByteString;
import io.maestro.protocol.v1.EmitMessage;
import io.maestro.protocol.v1.LogRecord;
import io.maestro.protocol.v1.RunnerMessage;
import io.maestro.sdk.KeyValueStore;
import io.maestro.sdk.Logger;
import io.maestro.sdk.ScriptContext;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 백엔드 연결 모드의 ScriptContext. 로그/emit을 gRPC 텔레메트리로 전송하고, 상류 메시지는
 * {@link #deliver}로 라우팅된다. 상태(state)는 백엔드 영속 저장소(재시작 간 유지)를 주입받는다.
 */
public final class GrpcContext implements ScriptContext {

    private final StreamSender sender;
    private final Map<String, String> params;
    private final KeyValueStore state;
    private final Map<String, Consumer<Object>> handlers = new ConcurrentHashMap<>();
    private final Logger logger;

    public GrpcContext(String name, Map<String, String> params, StreamSender sender, KeyValueStore state) {
        this.sender = sender;
        this.params = Map.copyOf(params);
        this.state = state;
        this.logger = new GrpcLogger(name, sender);
    }

    @Override
    public Logger log() {
        return logger;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T param(String key, Class<T> type) {
        String raw = params.get(key);
        if (raw == null) {
            return null;
        }
        if (type == String.class) return (T) raw;
        if (type == Integer.class) return (T) Integer.valueOf(raw);
        if (type == Long.class) return (T) Long.valueOf(raw);
        if (type == Double.class) return (T) Double.valueOf(raw);
        if (type == Boolean.class) return (T) Boolean.valueOf(raw);
        throw new IllegalArgumentException("지원하지 않는 파라미터 타입: " + type.getName());
    }

    @Override
    public void emit(String port, Object message) {
        byte[] payload = String.valueOf(message).getBytes(StandardCharsets.UTF_8);
        sender.send(RunnerMessage.newBuilder()
                .setEmit(EmitMessage.newBuilder()
                        .setPort(port)
                        .setPayloadJson(ByteString.copyFrom(payload))
                        .setContentType("text/plain"))
                .build());
    }

    @Override
    public void onMessage(String port, Consumer<Object> handler) {
        handlers.put(port, handler);
    }

    @Override
    public KeyValueStore state() {
        return state;
    }

    /** 백엔드 DeliverMessage 수신 시 등록 핸들러로 라우팅. */
    public void deliver(String port, String message) {
        Consumer<Object> handler = handlers.get(port);
        if (handler != null) {
            handler.accept(message);
        }
    }

    /** gRPC LogRecord로 스트리밍하는 Logger. */
    private static final class GrpcLogger implements Logger {
        private final String name;
        private final StreamSender sender;

        GrpcLogger(String name, StreamSender sender) {
            this.name = name;
            this.sender = sender;
        }

        @Override public void trace(String m, Object... a) { send(LogRecord.Level.TRACE, fmt(m, a), null); }
        @Override public void debug(String m, Object... a) { send(LogRecord.Level.DEBUG, fmt(m, a), null); }
        @Override public void info(String m, Object... a) { send(LogRecord.Level.INFO, fmt(m, a), null); }
        @Override public void warn(String m, Object... a) { send(LogRecord.Level.WARN, fmt(m, a), null); }
        @Override public void error(String m, Object... a) { send(LogRecord.Level.ERROR, fmt(m, a), null); }

        @Override
        public void error(String m, Throwable t) {
            java.io.StringWriter sw = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(sw));
            send(LogRecord.Level.ERROR, m, sw.toString());
        }

        private void send(LogRecord.Level level, String message, String thrown) {
            LogRecord.Builder b = LogRecord.newBuilder()
                    .setLevel(level)
                    .setMessage("[" + name + "] " + message);
            if (thrown != null) {
                b.setThrown(thrown);
            }
            sender.send(RunnerMessage.newBuilder().setLog(b).build());
        }

        private static String fmt(String msg, Object... args) {
            return io.maestro.runner.engine.SimpleLogger.format(msg, args);
        }
    }
}
