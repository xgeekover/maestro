package io.maestro.backend.process;

import io.maestro.backend.config.MaestroProperties;
import io.maestro.backend.grpc.GrpcServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 러너를 독립 JVM 프로세스로 기동한다(process-per-script, FR-4/FR-5). 소스는 gRPC StartCommand로
 * 전달되므로 여기서는 접속 정보·토큰·리소스 한도만 인자로 넘긴다.
 *
 * <p>클래스패스는 기본 {@code java.class.path}(개발/테스트). 운영 패키징은 Phase 10에서 명시 주입.</p>
 */
@Component
public class ProcessManager {

    private static final Logger log = LoggerFactory.getLogger(ProcessManager.class);
    private static final String RUNNER_MAIN = "io.maestro.runner.RunnerMain";

    private final MaestroProperties props;
    private final GrpcServer grpcServer;

    // @Lazy: GrpcServer→Gateway→Supervisor→ProcessManager 순환을 끊는다.
    public ProcessManager(MaestroProperties props, @Lazy GrpcServer grpcServer) {
        this.props = props;
        this.grpcServer = grpcServer;
    }

    public Process spawn(RunInfo run) throws IOException {
        String java = props.getRunner().getJavaExecutable();
        if (java == null || java.isBlank()) {
            java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        }
        String classpath = props.getRunner().getClasspath();
        if (classpath == null || classpath.isBlank()) {
            classpath = System.getProperty("java.class.path");
        }
        int port = grpcServer.getPort();

        List<String> cmd = new ArrayList<>();
        cmd.add(java);
        if (run.config().maxHeapBytes() > 0) {
            cmd.add("-Xmx" + run.config().maxHeapBytes());
        }
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(RUNNER_MAIN);
        cmd.add("--connect");
        cmd.add(props.getRunner().getBackendHost() + ":" + port);
        cmd.add("--run-id");
        cmd.add(run.runnerId());
        cmd.add("--script-id");
        cmd.add(run.scriptId());
        cmd.add("--token");
        cmd.add(run.token());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT); // 기동 실패 스택트레이스 표면화
        Process process = pb.start();
        log.info("러너 기동 — runId={} runnerId={} pid={}", run.runId(), run.runnerId(), process.pid());
        return process;
    }

    public void kill(Process process) {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }
}
