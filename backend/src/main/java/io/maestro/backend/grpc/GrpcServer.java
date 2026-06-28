package io.maestro.backend.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.maestro.backend.config.MaestroProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
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
        server = ServerBuilder.forPort(props.getGrpc().getPort())
                .addService(service)
                .build()
                .start();
        log.info("gRPC RunnerGateway 서버 시작 — 포트 {}", server.getPort());
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
