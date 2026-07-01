package io.maestro.backend.flow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.maestro.backend.flow.FlowModel.FlowGraph;
import io.maestro.backend.flow.FlowModel.FlowNode;
import io.maestro.backend.flow.FlowModel.NodeKind;
import io.maestro.backend.module.ModuleService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class FlowService {

    /** 스크립트(및 스펙 없는 모듈)의 기본 단일 포트. */
    private static final FlowValidator.Ports DEFAULT_PORTS =
            new FlowValidator.Ports(Set.of("in"), Set.of("out"));

    private final FlowRepository repository;
    private final ObjectMapper mapper;
    private final ModuleService modules;

    public FlowService(FlowRepository repository, ObjectMapper mapper, ModuleService modules) {
        this.repository = repository;
        this.mapper = mapper;
        this.modules = modules;
    }

    /** DAG + 포트 검증 후 저장. 위반 시 IllegalArgumentException(→422). */
    public FlowEntity create(String name, FlowGraph graph) {
        FlowValidator.validateDag(graph);
        FlowValidator.validatePorts(graph, this::portsOf);
        Instant now = Instant.now();
        FlowEntity entity = new FlowEntity(UUID.randomUUID().toString(), name, toJson(graph), now, now);
        return repository.save(entity);
    }

    /** 노드의 선언 포트 해석: 모듈은 specJson, 스크립트는 기본 in/out. */
    private FlowValidator.Ports portsOf(FlowNode node) {
        if (node.kind() == NodeKind.MODULE) {
            return modules.get(node.refId())
                    .map(m -> parsePorts(m.getSpecJson()))
                    .orElse(DEFAULT_PORTS);
        }
        return DEFAULT_PORTS;
    }

    /** specJson({"in":[...],"out":[...]})을 포트 집합으로 파싱. 없으면 기본 in/out. */
    private FlowValidator.Ports parsePorts(String specJson) {
        Set<String> in = new LinkedHashSet<>();
        Set<String> out = new LinkedHashSet<>();
        try {
            JsonNode root = mapper.readTree(specJson == null || specJson.isBlank() ? "{}" : specJson);
            root.path("in").forEach(x -> { if (x.isTextual()) in.add(x.asText()); });
            root.path("out").forEach(x -> { if (x.isTextual()) out.add(x.asText()); });
        } catch (JsonProcessingException ignore) {
            // 잘못된 스펙 → 기본 포트로 폴백
        }
        if (in.isEmpty()) {
            in.add("in");
        }
        if (out.isEmpty()) {
            out.add("out");
        }
        return new FlowValidator.Ports(in, out);
    }

    public List<FlowEntity> list() {
        return repository.findAll();
    }

    public Optional<FlowEntity> get(String id) {
        return repository.findById(id);
    }

    public boolean delete(String id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return true;
        }
        return false;
    }

    public FlowGraph graphOf(FlowEntity entity) {
        try {
            return mapper.readValue(entity.getGraphJson(), FlowGraph.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("플로우 그래프 역직렬화 실패", e);
        }
    }

    private String toJson(FlowGraph graph) {
        try {
            return mapper.writeValueAsString(graph);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("플로우 그래프 직렬화 실패", e);
        }
    }
}
