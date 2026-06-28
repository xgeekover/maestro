package io.maestro.backend.flow;

import java.util.List;
import java.util.Map;

/** 플로우 그래프 데이터 모델 (노드=스크립트/모듈, 엣지=메시지 경로). DAG 강제(ADR-0002 O-9). */
public final class FlowModel {

    private FlowModel() {
    }

    public enum NodeKind { SCRIPT, MODULE }

    public record FlowNode(
            String id,
            NodeKind kind,
            String refId,                 // scriptId 또는 moduleId
            Map<String, String> params,
            Long tickPeriodMs) {}

    public record FlowEdge(String fromNode, String fromPort, String toNode, String toPort) {}

    public record FlowGraph(List<FlowNode> nodes, List<FlowEdge> edges) {}
}
