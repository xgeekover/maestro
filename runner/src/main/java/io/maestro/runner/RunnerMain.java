package io.maestro.runner;

import io.maestro.runner.compile.Diag;
import io.maestro.runner.engine.EngineConfig;
import io.maestro.runner.engine.EngineResult;
import io.maestro.runner.engine.LifecycleEngine;
import io.maestro.runner.engine.LifecycleListener;
import io.maestro.runner.engine.LifecycleState;
import io.maestro.runner.engine.StandaloneContext;
import io.maestro.runner.engine.TickPolicy;
import io.maestro.runner.grpc.RunnerClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 스크립트 러너 CLI — 두 모드:
 * <ul>
 *   <li><b>연결 모드</b> {@code --connect host:port --run-id ID [--script-id S] [--token T]}:
 *       백엔드 gRPC에 접속해 StartCommand로 스크립트를 받아 실행(Phase 4 오케스트레이션).</li>
 *   <li><b>단독 모드</b> {@code <script.java> [옵션]}: 파일을 직접 컴파일·실행(Phase 3).</li>
 * </ul>
 */
public final class RunnerMain {

    private RunnerMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--connect")) {
            runConnected(args);
        } else {
            runStandalone(args);
        }
    }

    // ---- 연결 모드 (백엔드 오케스트레이션) ----
    private static void runConnected(String[] args) throws InterruptedException {
        String hostPort = null;
        String runId = null;
        String scriptId = "";
        String token = "";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--connect" -> hostPort = args[++i];
                case "--run-id" -> runId = args[++i];
                case "--script-id" -> scriptId = args[++i];
                case "--token" -> token = args[++i];
                default -> { /* 무시 */ }
            }
        }
        if (hostPort == null || runId == null) {
            System.err.println("연결 모드 사용법: --connect host:port --run-id ID [--script-id S] [--token T]");
            System.exit(2);
            return;
        }
        int colon = hostPort.lastIndexOf(':');
        String host = hostPort.substring(0, colon);
        int port = Integer.parseInt(hostPort.substring(colon + 1));
        new RunnerClient(host, port, runId, scriptId, token).run();
    }

    // ---- 단독 모드 (Phase 3) ----
    private static void runStandalone(String[] args) throws IOException {
        if (args.length == 0 || args[0].startsWith("--")) {
            System.err.println("사용법: runner <script.java> [--period ms] [--ticks n] "
                    + "[--policy continue|stop] [--tick-timeout ms] [--error-threshold n] [--param k=v ...]");
            System.exit(2);
            return;
        }

        Path scriptPath = Path.of(args[0]);
        String source = Files.readString(scriptPath);

        EngineConfig.Builder cfg = EngineConfig.builder();
        Map<String, String> params = new HashMap<>();

        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--period" -> cfg.tickPeriodMs(Long.parseLong(args[++i]));
                case "--ticks" -> cfg.maxTicks(Long.parseLong(args[++i]));
                case "--tick-timeout" -> cfg.tickTimeoutMs(Long.parseLong(args[++i]));
                case "--error-threshold" -> cfg.errorThreshold(Long.parseLong(args[++i]));
                case "--policy" -> cfg.tickPolicy(
                        "stop".equalsIgnoreCase(args[++i]) ? TickPolicy.STOP : TickPolicy.CONTINUE);
                case "--param" -> {
                    String[] kv = args[++i].split("=", 2);
                    params.put(kv[0], kv.length > 1 ? kv[1] : "");
                }
                default -> {
                    System.err.println("알 수 없는 옵션: " + a);
                    System.exit(2);
                    return;
                }
            }
        }

        String name = scriptPath.getFileName().toString().replaceFirst("\\.java$", "");
        StandaloneContext context = new StandaloneContext(name, params);
        LifecycleEngine engine = new LifecycleEngine(source, cfg.build(), context, new ConsoleListener());

        Thread main = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            engine.stop();
            try {
                main.join(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }));

        EngineResult result = engine.run();

        System.out.printf("%n=== 결과 ===%n state=%s onStart=%d ticks=%d tickErrors=%d onEnd=%d%n",
                result.finalState(), result.onStartCount(), result.tickCount(),
                result.tickErrorCount(), result.onEndCount());
        if (!result.diagnostics().isEmpty()) {
            System.out.println(" 진단:");
            result.diagnostics().forEach(d -> System.out.println("  " + d));
        }
        System.exit(result.isError() ? 1 : 0);
    }

    /** 콘솔로 상태 전이/틱을 출력하는 리스너(단독 모드). */
    private static final class ConsoleListener implements LifecycleListener {
        @Override
        public void onStateChange(LifecycleState from, LifecycleState to, String detail) {
            System.out.printf(">> %s → %s (%s)%n", from, to, detail);
        }

        @Override
        public void onTickComplete(long tickNumber, long durationMs) {
            System.out.printf("   tick #%d ok (%d ms)%n", tickNumber, durationMs);
        }

        @Override
        public void onTickError(long tickNumber, Throwable error) {
            System.out.printf("   tick #%d ERROR: %s (격리됨)%n", tickNumber, error);
        }

        @Override
        public void onTickTimeout(long tickNumber, long timeoutMs) {
            System.out.printf("   tick #%d TIMEOUT(%d ms) — 행 감지%n", tickNumber, timeoutMs);
        }

        @Override
        public void onCompileDiagnostics(boolean success, List<Diag> diagnostics) {
            if (!success || !diagnostics.isEmpty()) {
                System.out.printf("   컴파일 %s, 진단 %d건%n", success ? "성공" : "실패", diagnostics.size());
            }
        }

        @Override
        public void onLog(String message) {
            System.out.println("   [engine] " + message);
        }
    }
}
