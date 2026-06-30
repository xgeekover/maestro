package io.maestro.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QA M-1(메트릭 노출) + M-6(CORS 오리진 제한) 검증.
 * MockMvc로 실제 CORS/액추에이터 처리를 직접 검증한다.
 * {@code @AutoConfigureObservability}로 테스트에서도 Prometheus 레지스트리를 활성화(기본은 비활성).
 */
@SpringBootTest(properties = {"maestro.grpc.port=0"})
@AutoConfigureObservability
@AutoConfigureMockMvc
class ObservabilityCorsTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void prometheusEndpointExposed() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
    }

    @Test
    void metricsEndpointExposed() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isOk());
    }

    @Test
    void sensitiveActuatorStillBlocked() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    }

    @Test
    void corsAllowsConfiguredOrigin() throws Exception {
        mvc.perform(get("/api/scripts").header("Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void corsRejectsUnknownOrigin() throws Exception {
        // 비허용 오리진: ACAO 헤더 미부여 (Spring CORS는 403으로 거부)
        mvc.perform(get("/api/scripts").header("Origin", "http://evil.example.com"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
