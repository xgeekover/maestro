package io.maestro.backend.api;

import io.maestro.backend.flow.FlowDeployment;
import io.maestro.backend.flow.FlowEntity;
import io.maestro.backend.flow.FlowRuntime;
import io.maestro.backend.flow.FlowService;
import io.maestro.backend.process.RunInfo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/flows")
public class FlowController {

    private final FlowService flows;
    private final FlowRuntime runtime;

    public FlowController(FlowService flows, FlowRuntime runtime) {
        this.flows = flows;
        this.runtime = runtime;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.FlowResponse create(@RequestBody Dtos.CreateFlowRequest req) {
        FlowEntity entity = flows.create(req.name(), req.graph()); // DAG 위반 시 422(GlobalExceptionHandler)
        return Dtos.FlowResponse.of(entity, flows.graphOf(entity));
    }

    @GetMapping
    public List<Dtos.FlowResponse> list() {
        return flows.list().stream().map(e -> Dtos.FlowResponse.of(e, flows.graphOf(e))).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Dtos.FlowResponse> get(@PathVariable String id) {
        return flows.get(id)
                .map(e -> Dtos.FlowResponse.of(e, flows.graphOf(e)))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        runtime.stop(id);
        flows.delete(id);
    }

    @PostMapping("/{id}/deploy")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Dtos.DeployResponse deploy(@PathVariable String id) {
        FlowDeployment dep = runtime.deploy(id);
        Map<String, String> nodeRuns = new LinkedHashMap<>();
        dep.nodeRuns().forEach((nodeId, run) -> nodeRuns.put(nodeId, run.runId()));
        return new Dtos.DeployResponse(id, nodeRuns);
    }

    @PostMapping("/{id}/stop")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void stop(@PathVariable String id) {
        runtime.stop(id);
    }
}
