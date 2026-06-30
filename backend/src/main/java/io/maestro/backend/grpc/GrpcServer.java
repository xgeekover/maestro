package io.maestro.backend.grpc;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.maestro.backend.config.MaestroProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/** 러너 텔레메트리 수신 gRPC 서버 라이프사이클. */
@Component
public class GrpcServer {

    private static final Logger log = LoggerFactory.getLogger(GrpcServer.class);

    private final RunnerGatewayService service;
    private final MaestroProperties props;
    private Server server;

    public GrpcServer(RunnerGatewayService service, MaestroProperties props) {
        this.service = service;
        this.props = props;
    }

    @PostConstruct
    public void start() throws IOException {
        // QA H-1: 특정 주소(기본 루프백)에 바인딩 — 전 인터페이스 노출 금지
        String bind = props.getGrpc().getBindAddress();
        server = NettyServerBuilder.forAddress(new InetSocketAddress(bind, props.getGrpc().getPort()))
                .addService(service)
                .build()
                .start();
        log.info("gRPC RunnerGateway 서버 시작 — {}:{}", bind, server.getPort());
    }

    public int getPort() {
        if (server == null) {
            throw new IllegalStateException("gRPC 서버가 아직 시작되지 않았습니다.");
        }
        return server.getPort();
    }

    @PreDestroy
    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown();
            if (!server.awaitTermination(3, TimeUnit.SECONDS)) {
                server.shutdownNow();
            }
        }
    }
}
