package io.maestro.backend.flow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.maestro.backend.flow.FlowModel.FlowGraph;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FlowService {

    private final FlowRepository repository;
    private final ObjectMapper mapper;

    public FlowService(FlowRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /** DAG 검증 후 저장. 사이클/무결성 위반 시 IllegalArgumentException. */
    public FlowEntity create(String name, FlowGraph graph) {
        FlowValidator.validateDag(graph);
        Instant now = Instant.now();
        FlowEntity entity = new FlowEntity(UUID.randomUUID().toString(), name, toJson(graph), now, now);
        return repository.save(entity);
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
