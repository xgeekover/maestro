package io.maestro.backend.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.maestro.backend.config.MaestroProperties;
import io.maestro.backend.telemetry.TelemetryEvents;
import jakarta.annotation.PreDestroy;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * /ws/runs/{runId}/logs · /ws/runs/{runId}/metrics 구독. 텔레메트리 이벤트를 JSON으로 푸시(FR-6).
 *
 * <p>QA H-8: 수신(gRPC ingestion) 스레드는 구독자별 <b>바운디드 큐에 논블로킹 offer</b>만 하고,
 * 실제 전송은 별도 스레드풀이 담당한다. 느린/멈춘 구독자는 자기 큐가 차서 드롭될 뿐,
 * 텔레메트리 수신 경로 전체를 블록하지 못한다.</p>
 */
@Component
public class TelemetrySocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TelemetrySocketHandler.class);

    /** 구독자: 세션 + 바운디드 전송 큐 + 단일 드레인 가드. */
    static final class Subscriber {
        final WebSocketSession session;
        final String key;
        final BlockingQueue<TextMessage> queue;
        final AtomicBoolean draining = new AtomicBoolean(false);

        Subscriber(WebSocketSession session, String key, int capacity) {
            this.session = session;
            this.key = key;
            this.queue = new ArrayBlockingQueue<>(capacity);
        }
    }

    private final ObjectMapper mapper;
    private final int queueCapacity;
    private final ExecutorService delivery;
    private final ConcurrentHashMap<String, Set<Subscriber>> byKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Subscriber> bySession = new ConcurrentHashMap<>();
    private final AtomicLong dropped = new AtomicLong();

    public TelemetrySocketHandler(ObjectMapper mapper, MaestroProperties props) {
        this.mapper = mapper;
        this.queueCapacity = props.getWs().getQueueCapacity();
        this.delivery = Executors.newFixedThreadPool(props.getWs().getDeliveryThreads(), r -> {
            Thread t = new Thread(r, "maestro-ws-delivery");
            t.setDaemon(true);
            return t;
        });
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
        Subscriber sub = new Subscriber(session, key, queueCapacity);
        byKey.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(sub);
        bySession.put(session.getId(), sub);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Subscriber sub = bySession.remove(session.getId());
        if (sub != null) {
            Set<Subscriber> set = byKey.get(sub.key);
            if (set != null) {
                set.remove(sub);
            }
        }
    }

    @EventListener
    public void onMetric(TelemetryEvents.MetricEvent event) {
        broadcast(event.runId() + "|metrics", event.sample());
    }

    @EventListener
    public void onLog(TelemetryEvents.LogEvent event) {
        broadcast(event.runId() + "|logs", event.entry());
    }

    /** 수신 스레드에서 호출: 직렬화 1회 + 구독자별 논블로킹 offer(가득 차면 드롭). 절대 블록하지 않음. */
    private void broadcast(String key, Object payload) {
        Set<Subscriber> subs = byKey.get(key);
        if (subs == null || subs.isEmpty()) {
            return;
        }
        TextMessage message;
        try {
            message = new TextMessage(mapper.writeValueAsString(payload));
        } catch (IOException e) {
            return;
        }
        for (Subscriber sub : subs) {
            if (sub.queue.offer(message)) {
                scheduleDrain(sub);
            } else {
                dropped.incrementAndGet(); // 느린 구독자 — 드롭(수신은 계속)
            }
        }
    }

    private void scheduleDrain(Subscriber sub) {
        if (sub.draining.compareAndSet(false, true)) {
            try {
                delivery.execute(() -> drain(sub));
            } catch (RuntimeException e) {
                sub.draining.set(false); // 풀 종료 등
            }
        }
    }

    private void drain(Subscriber sub) {
        try {
            TextMessage m;
            while ((m = sub.queue.poll()) != null) {
                if (sub.session.isOpen()) {
                    synchronized (sub.session) {
                        sub.session.sendMessage(m);
                    }
                }
            }
        } catch (IOException e) {
            log.debug("WS 전송 실패(나머지 드롭): {}", e.toString());
            sub.queue.clear();
        } finally {
            sub.draining.set(false);
            if (!sub.queue.isEmpty()) {
                scheduleDrain(sub); // 드레인 중 도착한 메시지 처리
            }
        }
    }

    /** 테스트/관측용: 누적 드롭 수. */
    public long droppedCount() {
        return dropped.get();
    }

    @PreDestroy
    public void shutdown() {
        delivery.shutdownNow();
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
