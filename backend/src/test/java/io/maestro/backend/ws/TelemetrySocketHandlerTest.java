package io.maestro.backend.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.maestro.backend.config.MaestroProperties;
import io.maestro.backend.telemetry.LogEntry;
import io.maestro.backend.telemetry.TelemetryEvents;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * QA H-8: 멈춘 WS 구독자가 텔레메트리 수신 경로를 블록하지 않고, 큐 포화 시 드롭됨을 검증.
 */
class TelemetrySocketHandlerTest {

    private static MaestroProperties props(int cap, int threads) {
        MaestroProperties p = new MaestroProperties();
        p.getWs().setQueueCapacity(cap);
        p.getWs().setDeliveryThreads(threads);
        return p;
    }

    @Test
    void stalledSubscriberDoesNotBlockIngestionAndDrops() throws Exception {
        TelemetrySocketHandler handler = new TelemetrySocketHandler(new ObjectMapper(), props(5, 2));

        // 전송에서 멈추는(블록) 구독자
        CountDownLatch block = new CountDownLatch(1);
        WebSocketSession stalled = mock(WebSocketSession.class);
        when(stalled.getUri()).thenReturn(URI.create("ws://h/ws/runs/r1/logs"));
        when(stalled.getId()).thenReturn("s1");
        when(stalled.isOpen()).thenReturn(true);
        doAnswer(inv -> {
            block.await(3, TimeUnit.SECONDS); // 멈춘 클라이언트 시뮬레이션
            return null;
        }).when(stalled).sendMessage(any());

        handler.afterConnectionEstablished(stalled);

        // 수신 경로(broadcast)를 다량 호출 — 멈춘 구독자에도 불구하고 빠르게 반환해야 함
        long t0 = System.nanoTime();
        for (int i = 0; i < 200; i++) {
            handler.onLog(new TelemetryEvents.LogEvent("r1", new LogEntry(i, "INFO", "msg " + i, "")));
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

        assertTrue(elapsedMs < 1000, "수신 경로가 멈춘 구독자에 블록되지 않아야 함, elapsed=" + elapsedMs + "ms");
        assertTrue(handler.droppedCount() > 0, "큐 포화 시 드롭 발생해야 함, dropped=" + handler.droppedCount());

        block.countDown();
        handler.shutdown();
    }

    @Test
    void deliversToHealthySubscriber() throws Exception {
        TelemetrySocketHandler handler = new TelemetrySocketHandler(new ObjectMapper(), props(100, 2));
        CountDownLatch got = new CountDownLatch(3);
        WebSocketSession healthy = mock(WebSocketSession.class);
        when(healthy.getUri()).thenReturn(URI.create("ws://h/ws/runs/r2/metrics"));
        when(healthy.getId()).thenReturn("s2");
        when(healthy.isOpen()).thenReturn(true);
        doAnswer(inv -> {
            got.countDown();
            return null;
        }).when(healthy).sendMessage(any());

        handler.afterConnectionEstablished(healthy);
        for (int i = 0; i < 3; i++) {
            handler.onMetric(new TelemetryEvents.MetricEvent("r2",
                    new io.maestro.backend.telemetry.MetricSnapshot(i, 1, 2, 0.1, i, 0, i, 0.0)));
        }
        assertTrue(got.await(3, TimeUnit.SECONDS), "정상 구독자에게 전송되어야 함");
        handler.shutdown();
    }
}
