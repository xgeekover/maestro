package io.maestro.backend.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TelemetrySocketHandler handler;

    public WebSocketConfig(TelemetrySocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // /ws/runs/{runId}/logs · /ws/runs/{runId}/metrics
        registry.addHandler(handler, "/ws/runs/*/logs", "/ws/runs/*/metrics")
                .setAllowedOrigins("*");
    }
}
