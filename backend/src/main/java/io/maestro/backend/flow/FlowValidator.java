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

/** 플로우 그래프 검증: 엣지 무결성 + DAG(사이클 금지, Kahn 위상정렬). */
public final class FlowValidator {

    private FlowValidator() {
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
