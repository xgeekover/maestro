package io.maestro.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Maestro 오케스트레이터 진입점 (스캐폴딩).
 *
 * <p>Phase 4에서 구현: 러너 프로세스 기동/감시/재시작(Supervisor), 스케줄, REST+WS API,
 * gRPC RunnerGateway 서버, 프로세스별 CPU/메모리 메트릭(링버퍼), 로그 스트리밍.</p>
 */
@SpringBootApplication
public class MaestroApplication {

    public static void main(String[] args) {
        SpringApplication.run(MaestroApplication.class, args);
    }
}
