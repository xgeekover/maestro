package io.maestro.backend;

import io.maestro.backend.domain.ScriptEntity;
import io.maestro.backend.domain.ScriptService;
import io.maestro.backend.flow.FlowDeployment;
import io.maestro.backend.flow.FlowEntity;
import io.maestro.backend.flow.FlowModel.FlowEdge;
import io.maestro.backend.flow.FlowModel.FlowGraph;
import io.maestro.backend.flow.FlowModel.FlowNode;
import io.maestro.backend.flow.FlowModel.NodeKind;
import io.maestro.backend.flow.FlowRuntime;
import io.maestro.backend.flow.FlowService;
import io.maestro.backend.process.RunInfo;
import io.maestro.backend.process.RunStatus;
import io.maestro.backend.telemetry.LogEntry;
import io.maestro.backend.telemetry.TelemetryStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Phase 6 완료기준 증명: 2개 노드를 선으로 이어(producer.out → consumer.in) 배포하면,
 * 한 프로세스(producer)의 emit이 백엔드 라우팅을 거쳐 다른 프로세스(consumer)로 전달되어
 * 처리(로그)된다 — 프로세스 간 메시지 분산 처리 시연.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "maestro.grpc.port=0",
                "maestro.restart.watchdog-period-ms=200",
                "maestro.runner.stop-grace-ms=2000"
        })
class FlowRoutingIntegrationTest {

    private static final String PRODUCER = """
            import io.maestro.sdk.Script;
            public class Producer extends Script {
                private int n = 0;
                @Override public void onTick() { ctx.emit("out", ++n); }
            }
            """;

    private static final String CONSUMER = """
            import io.maestro.sdk.Script;
            public class Consumer extends Script {
                @Override public void onStart() {
                    ctx.onMessage("in", msg -> ctx.log().info("got " + msg));
                }
            }
            """;

    @Autowired private ScriptService scripts;
    @Autowired private FlowService flows;
    @Autowired private FlowRuntime runtime;
    @Autowired private TelemetryStore telemetry;

    @Test
    void emitFromOneNodeIsRoutedToDownstreamNode() throws Exception {
        ScriptEntity producer = scripts.create("producer", PRODUCER);
        ScriptEntity consumer = scripts.create("consumer", CONSUMER);

        FlowGraph graph = new FlowGraph(
                List.of(
                        new FlowNode("p", NodeKind.SCRIPT, producer.getId(), Map.of(), 200L),
                        new FlowNode("c", NodeKind.SCRIPT, consumer.getId(), Map.of(), 1000L)),
                List.of(new FlowEdge("p", "out", "c", "in")));

        FlowEntity flow = flows.create("demo-flow", graph);
        FlowDeployment dep = runtime.deploy(flow.getId());

        RunInfo producerRun = dep.nodeRuns().get("p");
        RunInfo consumerRun = dep.nodeRuns().get("c");
        assertNotNull(producerRun);
        assertNotNull(consumerRun);

        // 두 노드 모두 RUNNING
        awaitUntil(Duration.ofSeconds(40), "두 노드 RUNNING",
                () -> producerRun.status() == RunStatus.RUNNING
                        && consumerRun.status() == RunStatus.RUNNING);

        // 라우팅 증명: consumer가 상류 메시지를 수신해 처리(로그 "got N")
        awaitUntil(Duration.ofSeconds(20), "consumer가 메시지 수신",
                () -> telemetry.logs(consumerRun.runId()).stream()
                        .map(LogEntry::message)
                        .anyMatch(m -> m.contains("got ")));

        long received = telemetry.logs(consumerRun.runId()).stream()
                .map(LogEntry::message).filter(m -> m.contains("got ")).count();
        assertTrue(received >= 1, "consumer는 producer의 emit을 1건 이상 수신해야 함");

        runtime.stop(flow.getId());
    }

    @Test
    void cyclicFlowIsRejected() {
        FlowGraph cyclic = new FlowGraph(
                List.of(
                        new FlowNode("a", NodeKind.SCRIPT, "x", Map.of(), 1000L),
                        new FlowNode("b", NodeKind.SCRIPT, "y", Map.of(), 1000L)),
                List.of(new FlowEdge("a", "out", "b", "in"), new FlowEdge("b", "out", "a", "in")));
        try {
            flows.create("cyclic", cyclic);
            fail("사이클 플로우는 거부되어야 함");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("사이클"));
        }
    }

    private static void awaitUntil(Duration timeout, String desc, BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                if (condition.getAsBoolean()) {
                    return;
                }
            } catch (RuntimeException ignored) {
                // 폴링 재시도
            }
            Thread.sleep(150);
        }
        fail("타임아웃 — 조건 미충족: " + desc);
    }
}
