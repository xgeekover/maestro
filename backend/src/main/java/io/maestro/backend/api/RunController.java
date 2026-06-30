package io.maestro.backend.api;

import io.maestro.backend.process.RunConfig;
import io.maestro.backend.process.RunConfigFactory;
import io.maestro.backend.process.RunInfo;
import io.maestro.backend.process.RunRegistry;
import io.maestro.backend.process.Supervisor;
import io.maestro.backend.telemetry.LogEntry;
import io.maestro.backend.telemetry.MetricSnapshot;
import io.maestro.backend.telemetry.TelemetryStore;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final Supervisor supervisor;
    private final RunRegistry registry;
    private final TelemetryStore telemetry;
    private final RunConfigFactory configFactory;

    public RunController(Supervisor supervisor, RunRegistry registry, TelemetryStore telemetry,
                         RunConfigFactory configFactory) {
        this.supervisor = supervisor;
        this.registry = registry;
        this.telemetry = telemetry;
        this.configFactory = configFactory;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Dtos.RunResponse run(@Valid @RequestBody Dtos.CreateRunRequest req) {
        // 기본 한도(행 워치독/heap 캡) 강제 + 상한 클램프 (QA C-2)
        RunConfig config = configFactory.forRun(
                req.tickPeriodMs(), req.params(), req.stopOnError(),
                req.maxHeapBytes(), req.tickTimeoutMs(), req.errorThreshold());
        RunInfo run = supervisor.startRun(req.scriptId(), config);
        return Dtos.RunResponse.of(run);
    }

    @GetMapping
    public List<Dtos.RunResponse> list() {
        return registry.all().stream().map(Dtos.RunResponse::of).toList();
    }

    @GetMapping("/{runId}")
    public ResponseEntity<Dtos.RunResponse> get(@PathVariable String runId) {
        RunInfo run = registry.byRunId(runId);
        return run == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(Dtos.RunResponse.of(run));
    }

    @PostMapping("/{runId}/stop")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void stop(@PathVariable String runId) {
        if (registry.byRunId(runId) == null) {
            throw new io.maestro.backend.support.NotFoundException("실행 없음: " + runId);
        }
        supervisor.stopRun(runId);
    }

    @GetMapping("/{runId}/metrics")
    public List<MetricSnapshot> metrics(@PathVariable String runId) {
        return telemetry.metrics(runId);
    }

    @GetMapping("/{runId}/logs")
    public List<LogEntry> logs(@PathVariable String runId) {
        return telemetry.logs(runId);
    }
}
