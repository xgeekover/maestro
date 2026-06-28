package io.maestro.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Maestro 오케스트레이터 진입점.
 *
 * <p>러너 프로세스 기동/감시/재시작(Supervisor), gRPC RunnerGateway 서버, REST+WS API,
 * 프로세스별 CPU/메모리 메트릭(링버퍼), 로그 스트리밍.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MaestroApplication {

    public static void main(String[] args) {
        SpringApplication.run(MaestroApplication.class, args);
    }
}
