package io.maestro.backend.api;

import io.maestro.backend.process.RunRegistry;
import io.maestro.backend.telemetry.TelemetryStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/** 대시보드 개요: 모든 실행의 상태 + 최신 메트릭(CPU/메모리/tick/error)을 한 번에 제공(FR-6). */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final RunRegistry registry;
    private final TelemetryStore telemetry;

    public DashboardController(RunRegistry registry, TelemetryStore telemetry) {
        this.registry = registry;
        this.telemetry = telemetry;
    }

    @GetMapping
    public List<Dtos.RunSummary> overview() {
        return registry.all().stream()
                .sorted(Comparator.comparing(r -> r.startedAt() == null ? null : r.startedAt(),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(r -> new Dtos.RunSummary(
                        Dtos.RunResponse.of(r),
                        telemetry.latest(r.runId()).orElse(null)))
                .toList();
    }
}
