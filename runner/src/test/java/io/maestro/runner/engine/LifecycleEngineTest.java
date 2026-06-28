package io.maestro.runner.engine;

import io.maestro.sdk.ScriptContext;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 완료기준 증명: OnStart 정확히 1회 · OnTick 주기 반복 · OnEnd 정확히 1회,
 * 그리고 tick 예외 격리(CONTINUE/STOP/임계)와 onStart 실패·컴파일 실패 처리.
 */
class LifecycleEngineTest {

    private static final String COUNTER = """
            import io.maestro.sdk.Script;
            public class Counter extends Script {
                @Override public void onStart() {
                    ctx.state().put("started", ctx.state().get("started", Integer.class).orElse(0) + 1);
                }
                @Override public void onTick() {
                    ctx.state().put("ticks", ctx.state().get("ticks", Integer.class).orElse(0) + 1);
                }
                @Override public void onEnd() {
                    ctx.state().put("ended", ctx.state().get("ended", Integer.class).orElse(0) + 1);
                }
            }
            """;

    private static EngineResult run(String src, EngineConfig cfg, ScriptContext ctx) {
        return new LifecycleEngine(src, cfg, ctx, LifecycleListener.NOOP).run();
    }

    @Test
    void onStartOnce_onTickPeriodic_onEndOnce() {
        StandaloneContext ctx = new StandaloneContext("counter", Map.of());
        EngineConfig cfg = EngineConfig.builder().tickPeriodMs(10).maxTicks(5).build();

        EngineResult r = run(COUNTER, cfg, ctx);

        // 엔진 관측 카운트
        assertEquals(LifecycleState.STOPPED, r.finalState());
        assertEquals(1, r.onStartCount(), "onStart는 정확히 1회");
        assertEquals(5, r.tickCount(), "onTick은 주기마다 maxTicks회");
        assertEquals(0, r.tickErrorCount());
        assertEquals(1, r.onEndCount(), "onEnd는 정확히 1회");

        // 스크립트 본문이 실제 실행됐음을 컨텍스트 상태로 증명
        assertEquals(1, ctx.state().get("started", Integer.class).orElse(0));
        assertEquals(5, ctx.state().get("ticks", Integer.class).orElse(0));
        assertEquals(1, ctx.state().get("ended", Integer.class).orElse(0));
    }

    @Test
    void onTickRespectsPeriod() {
        StandaloneContext ctx = new StandaloneContext("counter", Map.of());
        long period = 25;
        long ticks = 5;
        EngineConfig cfg = EngineConfig.builder().tickPeriodMs(period).maxTicks(ticks).build();

        long t0 = System.nanoTime();
        EngineResult r = run(COUNTER, cfg, ctx);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

        assertEquals(ticks, r.tickCount());
        // 주기성 하한(느슨): 최소 (N-1) 간격의 절반 이상 소요
        assertTrue(elapsedMs >= period * (ticks - 1) / 2,
                "주기 실행이면 일정 시간 이상 소요되어야 함, elapsed=" + elapsedMs);
    }

    @Test
    void tickExceptionIsolation_continueKeepsTicking() {
        String src = """
                import io.maestro.sdk.Script;
                public class Boom extends Script {
                    @Override public void onTick() { throw new RuntimeException("boom"); }
                }
                """;
        StandaloneContext ctx = new StandaloneContext("boom", Map.of());
        EngineConfig cfg = EngineConfig.builder()
                .tickPeriodMs(5).maxTicks(5).tickPolicy(TickPolicy.CONTINUE).build();

        EngineResult r = run(src, cfg, ctx);

        assertEquals(5, r.tickCount(), "예외가 격리되어 계속 tick");
        assertEquals(5, r.tickErrorCount(), "모든 tick이 예외");
        assertEquals(1, r.onEndCount(), "예외에도 onEnd 1회 보장");
        assertEquals(LifecycleState.STOPPED, r.finalState(), "격리는 정상 종료(STOPPED)");
    }

    @Test
    void tickExceptionPolicy_stopHaltsOnFirstError() {
        String src = """
                import io.maestro.sdk.Script;
                public class Boom extends Script {
                    @Override public void onTick() { throw new RuntimeException("boom"); }
                }
                """;
        StandaloneContext ctx = new StandaloneContext("boom", Map.of());
        EngineConfig cfg = EngineConfig.builder()
                .tickPeriodMs(5).maxTicks(10).tickPolicy(TickPolicy.STOP).build();

        EngineResult r = run(src, cfg, ctx);

        assertEquals(1, r.tickCount(), "STOP 정책은 첫 예외에서 중지");
        assertEquals(1, r.tickErrorCount());
        assertEquals(1, r.onEndCount());
    }

    @Test
    void errorThreshold_stopsAfterThreshold() {
        String src = """
                import io.maestro.sdk.Script;
                public class Boom extends Script {
                    @Override public void onTick() { throw new RuntimeException("boom"); }
                }
                """;
        StandaloneContext ctx = new StandaloneContext("boom", Map.of());
        EngineConfig cfg = EngineConfig.builder()
                .tickPeriodMs(5).maxTicks(10).tickPolicy(TickPolicy.CONTINUE).errorThreshold(3).build();

        EngineResult r = run(src, cfg, ctx);

        assertEquals(3, r.tickCount(), "누적 에러 임계 도달 시 중지");
        assertEquals(3, r.tickErrorCount());
        assertEquals(1, r.onEndCount());
    }

    @Test
    void onStartFailure_transitionsToErrorThenOnEnd() {
        String src = """
                import io.maestro.sdk.Script;
                public class FailStart extends Script {
                    @Override public void onStart() { throw new IllegalStateException("nope"); }
                    @Override public void onTick() { ctx.state().put("ticked", 1); }
                }
                """;
        StandaloneContext ctx = new StandaloneContext("failstart", Map.of());
        EngineConfig cfg = EngineConfig.builder().tickPeriodMs(5).maxTicks(5).build();

        EngineResult r = run(src, cfg, ctx);

        assertEquals(LifecycleState.ERROR, r.finalState());
        assertEquals(0, r.onStartCount(), "실패한 onStart는 미카운트");
        assertEquals(0, r.tickCount(), "onStart 실패 시 tick 미진입");
        assertEquals(1, r.onEndCount(), "onStart 실패에도 onEnd 1회");
        assertNotNull(r.failure());
        assertFalse(ctx.state().contains("ticked"), "tick은 실행되지 않아야 함");
    }

    @Test
    void compileFailure_errorWithDiagnosticsNoOnEnd() {
        String src = """
                public class Broken extends io.maestro.sdk.Script {
                    @Override public void onTick() { this is not java }
                }
                """;
        StandaloneContext ctx = new StandaloneContext("broken", Map.of());
        EngineConfig cfg = EngineConfig.builder().maxTicks(1).build();

        EngineResult r = run(src, cfg, ctx);

        assertEquals(LifecycleState.ERROR, r.finalState());
        assertFalse(r.diagnostics().isEmpty(), "컴파일 진단 존재");
        assertEquals(0, r.onStartCount());
        assertEquals(0, r.onEndCount(), "인스턴스가 없으므로 onEnd 없음");
    }

    @Test
    void userStop_endsGracefullyWithOnEndOnce() throws Exception {
        StandaloneContext ctx = new StandaloneContext("counter", Map.of());
        EngineConfig cfg = EngineConfig.builder().tickPeriodMs(10).maxTicks(-1).build(); // 무제한
        LifecycleEngine engine = new LifecycleEngine(COUNTER, cfg, ctx, LifecycleListener.NOOP);

        AtomicReference<EngineResult> ref = new AtomicReference<>();
        Thread t = new Thread(() -> ref.set(engine.run()));
        t.start();
        Thread.sleep(80); // 몇 tick 진행
        engine.stop();    // 사용자 중지
        t.join(2000);

        EngineResult r = ref.get();
        assertNotNull(r, "엔진이 종료되어야 함");
        assertEquals(LifecycleState.STOPPED, r.finalState());
        assertEquals(1, r.onEndCount(), "사용자 중지에도 onEnd 1회");
        assertTrue(r.tickCount() >= 1);
    }

    @Test
    void emitAndOnMessageWork() {
        String src = """
                import io.maestro.sdk.Script;
                public class Echo extends Script {
                    @Override public void onStart() {
                        ctx.onMessage("in", msg -> ctx.state().put("last", String.valueOf(msg)));
                    }
                    @Override public void onTick() { ctx.emit("out", "ping"); }
                }
                """;
        StandaloneContext ctx = new StandaloneContext("echo", Map.of());
        EngineConfig cfg = EngineConfig.builder().tickPeriodMs(5).maxTicks(2).build();

        EngineResult r = run(src, cfg, ctx);

        assertEquals(2, r.tickCount());
        assertEquals(2, ctx.emitted().size(), "tick마다 emit");
        assertEquals("out", ctx.emitted().get(0).port());

        ctx.deliver("in", "hello"); // 상류 메시지 시뮬레이션
        assertEquals("hello", ctx.state().get("last", String.class).orElse(null));
    }
}
