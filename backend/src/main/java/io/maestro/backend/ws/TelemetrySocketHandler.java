package io.maestro.backend.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.maestro.backend.telemetry.TelemetryEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /ws/runs/{runId}/logs · /ws/runs/{runId}/metrics 구독. 텔레메트리 이벤트를 JSON으로 푸시(FR-6).
 */
@Component
public class TelemetrySocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TelemetrySocketHandler.class);

    private final ObjectMapper mapper;
    // key = runId|channel → 세션 집합
    private final ConcurrentHashMap<String, Set<WebSocketSession>> subscribers = new ConcurrentHashMap<>();

    public TelemetrySocketHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String key = keyOf(session.getUri());
        if (key == null) {
            try {
                session.close(CloseStatus.BAD_DATA);
            } catch (IOException ignored) {
                // 무시
            }
            return;
        }
        subscribers.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        subscribers.values().forEach(set -> set.remove(session));
    }

    @EventListener
    public void onMetric(TelemetryEvents.MetricEvent event) {
        broadcast(event.runId() + "|metrics", event.sample());
    }

    @EventListener
    public void onLog(TelemetryEvents.LogEvent event) {
        broadcast(event.runId() + "|logs", event.entry());
    }

    private void broadcast(String key, Object payload) {
        Set<WebSocketSession> sessions = subscribers.get(key);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (IOException e) {
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(message);
                    }
                } catch (IOException e) {
                    log.debug("WS 전송 실패: {}", e.toString());
                }
            }
        }
    }

    /** URI 경로 /ws/runs/{runId}/{channel} 에서 key(runId|channel) 추출. */
    private static String keyOf(URI uri) {
        if (uri == null) {
            return null;
        }
        String[] parts = uri.getPath().split("/");
        // ["", "ws", "runs", runId, channel]
        if (parts.length >= 5 && "ws".equals(parts[1]) && "runs".equals(parts[2])) {
            String runId = parts[3];
            String channel = parts[4];
            if ("logs".equals(channel) || "metrics".equals(channel)) {
                return runId + "|" + channel;
            }
        }
        return null;
    }
}
