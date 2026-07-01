package io.maestro.backend.flow;

import io.maestro.backend.flow.FlowModel.FlowEdge;
import io.maestro.backend.flow.FlowModel.FlowGraph;
import io.maestro.backend.flow.FlowModel.FlowNode;
import io.maestro.backend.flow.FlowModel.NodeKind;
import io.maestro.backend.flow.FlowValidator.Ports;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowValidatorTest {

    private static FlowNode node(String id) {
        return new FlowNode(id, NodeKind.SCRIPT, "script-" + id, Map.of(), 1000L);
    }

    // 기본 단일 포트 리졸버(in/out)
    private static final Function<FlowNode, Ports> DEFAULT_PORTS =
            n -> new Ports(Set.of("in"), Set.of("out"));

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

    @Test
    void acceptsEdgesOnDeclaredPorts() {
        FlowGraph g = new FlowGraph(
                List.of(node("a"), node("b")),
                List.of(new FlowEdge("a", "out", "b", "in")));
        assertDoesNotThrow(() -> FlowValidator.validatePorts(g, DEFAULT_PORTS));
    }

    @Test
    void rejectsUndeclaredOutputPort() {
        FlowGraph g = new FlowGraph(
                List.of(node("a"), node("b")),
                List.of(new FlowEdge("a", "nope", "b", "in")));
        assertThrows(IllegalArgumentException.class,
                () -> FlowValidator.validatePorts(g, DEFAULT_PORTS));
    }

    @Test
    void rejectsUndeclaredInputPort() {
        FlowGraph g = new FlowGraph(
                List.of(node("a"), node("b")),
                List.of(new FlowEdge("a", "out", "b", "nope")));
        assertThrows(IllegalArgumentException.class,
                () -> FlowValidator.validatePorts(g, DEFAULT_PORTS));
    }

    @Test
    void honoursMultiPortResolver() {
        Function<FlowNode, Ports> resolver = n -> "a".equals(n.id())
                ? new Ports(Set.of("in"), Set.of("result", "error"))
                : new Ports(Set.of("in"), Set.of("out"));
        FlowGraph g = new FlowGraph(
                List.of(node("a"), node("b")),
                List.of(new FlowEdge("a", "error", "b", "in")));
        assertDoesNotThrow(() -> FlowValidator.validatePorts(g, resolver));
    }
}
