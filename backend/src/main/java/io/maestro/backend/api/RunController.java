package io.maestro.backend.api;

import io.maestro.backend.history.RunHistoryService;
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
import org.springframework.web.bind.annotation.RequestParam;
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
    private final RunHistoryService history;

    public RunController(Supervisor supervisor, RunRegistry registry, TelemetryStore telemetry,
                         RunConfigFactory configFactory, RunHistoryService history) {
        this.supervisor = supervisor;
        this.registry = registry;
        this.telemetry = telemetry;
        this.configFactory = configFactory;
        this.history = history;
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

    /** 완료된 실행 이력(영속, 재시작 후에도 조회). 페이지네이션. */
    @GetMapping("/history")
    public List<Dtos.RunHistoryResponse> history(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "50") int size) {
        return history.recent(page, size).stream().map(Dtos.RunHistoryResponse::of).toList();
    }

    @GetMapping("/history/{runId}")
    public ResponseEntity<Dtos.RunHistoryResponse> historyOne(@PathVariable String runId) {
        return history.get(runId)
                .map(Dtos.RunHistoryResponse::of)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{runId}")
    public ResponseEntity<Dtos.RunResponse> get(@PathVariable String runId) {
        RunInfo run = registry.byRunId(runId);
        return run == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(Dtos.RunResponse.of(run));
    }

    @PostMapping("/{runId}/period")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void updatePeriod(@PathVariable String runId, @Valid @RequestBody Dtos.UpdatePeriodRequest req) {
        supervisor.updatePeriod(runId, req.tickPeriodMs());
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
