package io.maestro.backend.flow;

import com.google.protobuf.ByteString;
import io.maestro.backend.process.RunInfo;
import io.maestro.protocol.v1.BackendMessage;
import io.maestro.protocol.v1.DeliverMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 한 플로우 배포의 런타임: 노드별 러너 + 라우팅 테이블 + 바운디드 큐(백프레셔).
 *
 * <p>노드 emit → 라우팅 테이블에서 하류 노드 조회 → 바운디드 큐 적재 → 디스패처가
 * 하류 러너로 DeliverMessage 전송. 큐가 가득 차면 drop-oldest(드롭 카운트, ADR-0002 O-9).</p>
 */
public final class FlowDeployment {

    private static final Logger log = LoggerFactory.getLogger(FlowDeployment.class);

    /** 라우팅 목적지. */
    public record Target(String nodeId, String inPort) {}

    private record RouteMsg(String targetNodeId, String inPort, ByteString payload, String fromNode) {}

    private final String flowId;
    private final Map<String, List<Target>> routes;   // key: "fromNodeId|fromPort"
    private final Map<String, RunInfo> nodeRuns = new ConcurrentHashMap<>();
    private final BlockingQueue<RouteMsg> queue;
    private final AtomicLong dropped = new AtomicLong();
    private volatile boolean running = true;
    private Thread dispatcher;

    public FlowDeployment(String flowId, Map<String, List<Target>> routes, int queueCapacity) {
        this.flowId = flowId;
        this.routes = routes;
        this.queue = new ArrayBlockingQueue<>(Math.max(1, queueCapacity));
    }

    public String flowId() {
        return flowId;
    }

    public void putNodeRun(String nodeId, RunInfo run) {
        nodeRuns.put(nodeId, run);
    }

    public Map<String, RunInfo> nodeRuns() {
        return nodeRuns;
    }

    public long droppedCount() {
        return dropped.get();
    }

    public void start() {
        dispatcher = new Thread(this::dispatchLoop, "maestro-flow-" + flowId);
        dispatcher.setDaemon(true);
        dispatcher.start();
    }

    /** 노드 emit을 라우팅 큐에 적재. 큐 포화 시 drop-oldest 백프레셔. */
    public void enqueue(String fromNodeId, String port, ByteString payload) {
        List<Target> targets = routes.get(fromNodeId + "|" + port);
        if (targets == null) {
            return;
        }
        for (Target t : targets) {
            RouteMsg msg = new RouteMsg(t.nodeId(), t.inPort(), payload, fromNodeId);
            if (!queue.offer(msg)) {
                queue.poll();              // 가장 오래된 것 드롭
                dropped.incrementAndGet();
                queue.offer(msg);
            }
        }
    }

    private void dispatchLoop() {
        while (running) {
            try {
                RouteMsg msg = queue.poll(200, TimeUnit.MILLISECONDS);
                if (msg == null) {
                    continue;
                }
                RunInfo target = nodeRuns.get(msg.targetNodeId());
                if (target == null) {
                    continue;
                }
                target.sendCommand(BackendMessage.newBuilder()
                        .setDeliver(DeliverMessage.newBuilder()
                                .setInPort(msg.inPort())
                                .setPayloadJson(msg.payload())
                                .setFromNode(msg.fromNode()))
                        .build());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException e) {
                log.debug("플로우 라우팅 전송 오류 flowId={}: {}", flowId, e.toString());
            }
        }
    }

    public void stop() {
        running = false;
        if (dispatcher != null) {
            dispatcher.interrupt();
        }
    }
}
