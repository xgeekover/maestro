package io.maestro.backend.flow;

import io.maestro.backend.flow.FlowModel.FlowEdge;
import io.maestro.backend.flow.FlowModel.FlowGraph;
import io.maestro.backend.flow.FlowModel.FlowNode;
import io.maestro.backend.flow.FlowModel.NodeKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowValidatorTest {

    private static FlowNode node(String id) {
        return new FlowNode(id, NodeKind.SCRIPT, "script-" + id, Map.of(), 1000L);
    }

    @Test
    void acceptsDag() {
        FlowGraph g = new FlowGraph(
                List.of(node("a"), node("b"), node("c")),
                List.of(new FlowEdge("a", "out", "b", "in"), new FlowEdge("b", "out", "c", "in")));
        assertDoesNotThrow(() -> FlowValidator.validateDag(g));
    }

    @Test
    void rejectsCycle() {
        FlowGraph g = new FlowGraph(
                List.of(node("a"), node("b")),
                List.of(new FlowEdge("a", "out", "b", "in"), new FlowEdge("b", "out", "a", "in")));
        assertThrows(IllegalArgumentException.class, () -> FlowValidator.validateDag(g));
    }

    @Test
    void rejectsEdgeToUnknownNode() {
        FlowGraph g = new FlowGraph(
                List.of(node("a")),
                List.of(new FlowEdge("a", "out", "ghost", "in")));
        assertThrows(IllegalArgumentException.class, () -> FlowValidator.validateDag(g));
    }
}
