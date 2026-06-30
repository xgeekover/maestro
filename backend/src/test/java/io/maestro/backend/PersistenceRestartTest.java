package io.maestro.backend;

import io.maestro.backend.domain.ScriptService;
import io.maestro.backend.flow.FlowModel.FlowGraph;
import io.maestro.backend.flow.FlowModel.FlowNode;
import io.maestro.backend.flow.FlowModel.NodeKind;
import io.maestro.backend.flow.FlowService;
import io.maestro.backend.module.ModuleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H-5: 스크립트·플로우·모듈이 백엔드 재시작 후에도 보존되는지 검증.
 * 같은 H2 파일 DB로 앱 컨텍스트를 두 번 기동해 1차에서 만든 데이터를 2차에서 조회한다.
 */
class PersistenceRestartTest {

    private static final String SRC =
            "import io.maestro.sdk.Script; public class P extends Script { @Override public void onTick(){} }";

    private ConfigurableApplicationContext boot(String url) {
        return new SpringApplicationBuilder(MaestroApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.datasource.url=" + url,
                        "spring.flyway.enabled=true",
                        "spring.jpa.hibernate.ddl-auto=none",
                        "maestro.grpc.port=0")
                .run();
    }

    @Test
    void dataSurvivesRestart(@TempDir Path tmp) {
        String url = "jdbc:h2:file:" + tmp.resolve("maestro").toAbsolutePath() + ";MODE=PostgreSQL";
        String scriptId;
        String moduleId;
        String flowId;

        // 1차 기동 — 데이터 생성
        try (ConfigurableApplicationContext ctx = boot(url)) {
            scriptId = ctx.getBean(ScriptService.class).create("persisted", SRC).getId();
            moduleId = ctx.getBean(ModuleService.class).create("mod", "1.0.0", "{}", SRC).getId();
            FlowGraph g = new FlowGraph(
                    List.of(new FlowNode("a", NodeKind.SCRIPT, scriptId, Map.of(), 1000L)), List.of());
            flowId = ctx.getBean(FlowService.class).create("flow", g).getId();
        }

        // 2차 기동 — 같은 파일 DB → 데이터 보존
        try (ConfigurableApplicationContext ctx = boot(url)) {
            assertTrue(ctx.getBean(ScriptService.class).get(scriptId).isPresent(), "스크립트 보존");
            assertTrue(ctx.getBean(ModuleService.class).get(moduleId).isPresent(), "모듈 보존");
            assertTrue(ctx.getBean(FlowService.class).get(flowId).isPresent(), "플로우 보존");
        }
    }
}
