package io.maestro.backend.flow;

import io.maestro.backend.flow.FlowModel.FlowEdge;
import io.maestro.backend.flow.FlowModel.FlowGraph;
import io.maestro.backend.flow.FlowModel.FlowNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 플로우 그래프 검증: 엣지 무결성 + DAG(사이클 금지, Kahn 위상정렬) + 포트 스펙(T3). */
public final class FlowValidator {

    private FlowValidator() {
    }

    /** 노드가 선언한 입출력 포트 집합. */
    public record Ports(Set<String> in, Set<String> out) {}

    /**
     * 각 엣지의 fromPort/toPort가 참조 노드의 선언된 출력/입력 포트에 존재하는지 검증(T3).
     * resolver는 노드 → 선언 포트를 해석(모듈 specJson, 스크립트 기본 in/out).
     * DAG 검증 이후에 호출한다(존재하지 않는 노드 참조는 여기서 건너뜀).
     */
    public static void validatePorts(FlowGraph graph, Function<FlowNode, Ports> resolver) {
        Map<String, FlowNode> byId = graph.nodes().stream()
                .collect(Collectors.toMap(FlowNode::id, n -> n, (a, b) -> a));
        for (FlowEdge e : graph.edges()) {
            FlowNode from = byId.get(e.fromNode());
            FlowNode to = byId.get(e.toNode());
            if (from == null || to == null) {
                continue; // DAG 검증에서 이미 거부됨
            }
            if (!resolver.apply(from).out().contains(e.fromPort())) {
                throw new IllegalArgumentException(
                        "출력 포트 없음: " + e.fromNode() + "." + e.fromPort());
            }
            if (!resolver.apply(to).in().contains(e.toPort())) {
                throw new IllegalArgumentException(
                        "입력 포트 없음: " + e.toNode() + "." + e.toPort());
            }
        }
    }

    public static void validateDag(FlowGraph graph) {
        Set<String> nodeIds = new HashSet<>();
        for (FlowNode n : graph.nodes()) {
            if (!nodeIds.add(n.id())) {
                throw new IllegalArgumentException("중복 노드 ID: " + n.id());
            }
        }
        Map<String, List<String>> adj = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        nodeIds.forEach(id -> {
            adj.put(id, new ArrayList<>());
            indegree.put(id, 0);
        });

        for (FlowEdge e : graph.edges()) {
            if (!nodeIds.contains(e.fromNode()) || !nodeIds.contains(e.toNode())) {
                throw new IllegalArgumentException("엣지가 존재하지 않는 노드를 참조: "
                        + e.fromNode() + " → " + e.toNode());
            }
            adj.get(e.fromNode()).add(e.toNode());
            indegree.merge(e.toNode(), 1, Integer::sum);
        }

        Deque<String> queue = new ArrayDeque<>();
        indegree.forEach((id, deg) -> {
            if (deg == 0) {
                queue.add(id);
            }
        });
        int processed = 0;
        while (!queue.isEmpty()) {
            String id = queue.poll();
            processed++;
            for (String next : adj.get(id)) {
                if (indegree.merge(next, -1, Integer::sum) == 0) {
                    queue.add(next);
                }
            }
        }
        if (processed != nodeIds.size()) {
            throw new IllegalArgumentException("플로우에 사이클이 있습니다(DAG만 허용).");
        }
    }
}
