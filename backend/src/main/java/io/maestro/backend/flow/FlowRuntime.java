package io.maestro.backend.flow;

import com.google.protobuf.ByteString;
import io.maestro.backend.config.MaestroProperties;
import io.maestro.backend.domain.ScriptEntity;
import io.maestro.backend.domain.ScriptService;
import io.maestro.backend.flow.FlowDeployment.Target;
import io.maestro.backend.flow.FlowModel.FlowEdge;
import io.maestro.backend.flow.FlowModel.FlowGraph;
import io.maestro.backend.flow.FlowModel.FlowNode;
import io.maestro.backend.flow.FlowModel.NodeKind;
import io.maestro.backend.module.ModuleEntity;
import io.maestro.backend.module.ModuleService;
import io.maestro.backend.process.RunConfig;
import io.maestro.backend.process.RunConfigFactory;
import io.maestro.backend.process.RunInfo;
import io.maestro.backend.process.Supervisor;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 플로우 배포 오케스트레이션 + 메시지 라우팅(릴레이, ADR-0001 D5). 노드별 러너를 기동하고,
 * 러너 emit을 라우팅 테이블에 따라 하류 노드로 전달(FR-7).
 */
@Component
public class FlowRuntime {

    private static final Logger log = LoggerFactory.getLogger(FlowRuntime.class);

    private final Supervisor supervisor;
    private final FlowService flowService;
    private final ScriptService scriptService;
    private final ModuleService moduleService;
    private final MaestroProperties props;
    private final RunConfigFactory configFactory;
    private final Map<String, FlowDeployment> deployments = new ConcurrentHashMap<>();

    public FlowRuntime(Supervisor supervisor, FlowService flowService, ScriptService scriptService,
                       ModuleService moduleService, MaestroProperties props, RunConfigFactory configFactory) {
        this.supervisor = supervisor;
        this.flowService = flowService;
        this.scriptService = scriptService;
        this.moduleService = moduleService;
        this.props = props;
        this.configFactory = configFactory;
    }

    public FlowDeployment deploy(String flowId) {
        FlowEntity entity = flowService.get(flowId)
                .orElseThrow(() -> new io.maestro.backend.support.NotFoundException("플로우 없음: " + flowId));
        FlowGraph graph = flowService.graphOf(entity);
        FlowValidator.validateDag(graph);

        FlowDeployment dep = new FlowDeployment(flowId, buildRoutes(graph), props.getBuffer().getFlowQueueCapacity());
        deployments.put(flowId, dep);
        dep.start();

        for (FlowNode node : graph.nodes()) {
            Resolved r = resolve(node);
            RunConfig cfg = configFactory.forFlowNode(node.tickPeriodMs(), node.params());
            RunInfo run = supervisor.startNode(node.refId(), r.name(), r.source(), cfg, flowId, node.id());
            dep.putNodeRun(node.id(), run);
        }
        log.info("플로우 배포 flowId={} 노드 {}개", flowId, graph.nodes().size());
        return dep;
    }

    /** 러너 emit 라우팅: 하류 노드의 입력 포트로 전달. */
    public void route(RunInfo run, String port, ByteString payload) {
        String flowId = run.flowId();
        if (flowId == null) {
            return;
        }
        FlowDeployment dep = deployments.get(flowId);
        if (dep != null) {
            dep.enqueue(run.nodeId(), port, payload);
        }
    }

    public void stop(String flowId) {
        FlowDeployment dep = deployments.remove(flowId);
        if (dep == null) {
            return;
        }
        dep.nodeRuns().values().forEach(run -> supervisor.stopRun(run.runId()));
        dep.stop();
    }

    public Optional<FlowDeployment> deployment(String flowId) {
        return Optional.ofNullable(deployments.get(flowId));
    }

    @PreDestroy
    public void shutdown() {
        deployments.keySet().forEach(this::stop);
    }

    private Map<String, List<Target>> buildRoutes(FlowGraph graph) {
        Map<String, List<Target>> routes = new HashMap<>();
        for (FlowEdge e : graph.edges()) {
            routes.computeIfAbsent(e.fromNode() + "|" + e.fromPort(), k -> new ArrayList<>())
                    .add(new Target(e.toNode(), e.toPort()));
        }
        return routes;
    }

    private record Resolved(String name, String source) {}

    private Resolved resolve(FlowNode node) {
        if (node.kind() == NodeKind.MODULE) {
            ModuleEntity m = moduleService.get(node.refId())
                    .orElseThrow(() -> new io.maestro.backend.support.NotFoundException("모듈 없음: " + node.refId()));
            return new Resolved(m.getName() + "@" + m.getVersion(), m.getSource());
        }
        ScriptEntity s = scriptService.get(node.refId())
                .orElseThrow(() -> new io.maestro.backend.support.NotFoundException("스크립트 없음: " + node.refId()));
        return new Resolved(s.getName(), s.getSource());
    }
}
