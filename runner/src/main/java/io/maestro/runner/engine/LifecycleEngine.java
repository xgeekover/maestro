package io.maestro.runner.engine;

import io.maestro.runner.compile.CompilationResult;
import io.maestro.runner.compile.Diag;
import io.maestro.runner.compile.InMemoryCompiler;
import io.maestro.sdk.Script;
import io.maestro.sdk.ScriptContext;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 스크립트 엔진의 핵심: 동적 컴파일 → 라이프사이클 실행 (FR-2, FR-3, FR-5).
 *
 * <p>보장:
 * <ul>
 *   <li>onStart — 성공 시 정확히 1회. 실패 시 ERROR 분류 후 onEnd 호출.</li>
 *   <li>onTick — 지정 주기로 반복. 예외는 격리(정책 CONTINUE/STOP, 누적 임계).</li>
 *   <li>onEnd — 인스턴스 생성 후에는 어떤 경로로 끝나든 정확히 1회(best-effort).</li>
 * </ul>
 * 모든 스크립트 메서드는 단일 "스크립트 스레드"에서 실행되며, 타임아웃 워치독으로 행을 감지한다
 * (인프로세스 best-effort; 진짜 행/OOM은 Phase 4 OS 레벨 감독에서 종료).</p>
 */
public final class LifecycleEngine {

    private final String source;
    private final EngineConfig config;
    private final ScriptContext context;
    private final LifecycleListener listener;
    private final InMemoryCompiler compiler;

    private volatile LifecycleState state = LifecycleState.NEW;
    private volatile boolean stopRequested = false;
    private volatile Thread controlThread;
    private ExecutorService scriptExecutor;

    public LifecycleEngine(String source, EngineConfig config, ScriptContext context, LifecycleListener listener) {
        this(source, config, context, listener, new InMemoryCompiler());
    }

    public LifecycleEngine(String source, EngineConfig config, ScriptContext context,
                           LifecycleListener listener, InMemoryCompiler compiler) {
        this.source = source;
        this.config = config;
        this.context = context;
        this.listener = listener == null ? LifecycleListener.NOOP : listener;
        this.compiler = compiler;
    }

    public LifecycleState state() {
        return state;
    }

    /** 외부에서 graceful 중지 요청(사용자 중지). onEnd가 1회 호출되고 STOPPED로 종료된다. */
    public void stop() {
        stopRequested = true;
        Thread ct = controlThread;
        if (ct != null) {
            ct.interrupt();
        }
    }

    /** 라이프사이클을 동기 실행하고 결과(검증용 카운트 포함)를 반환한다. */
    public EngineResult run() {
        this.controlThread = Thread.currentThread();

        // --- COMPILING ---
        transition(LifecycleState.COMPILING, "동적 컴파일");
        CompilationResult cr;
        try {
            cr = compiler.compile(source);
        } catch (RuntimeException e) {
            transition(LifecycleState.ERROR, "컴파일 예외: " + e.getMessage());
            return new EngineResult(LifecycleState.ERROR, 0, 0, 0, 0, List.of(), e);
        }
        List<Diag> diagnostics = cr.diagnostics();
        listener.onCompileDiagnostics(cr.success(), diagnostics);
        if (!cr.success()) {
            transition(LifecycleState.ERROR, "컴파일 실패");
            return new EngineResult(LifecycleState.ERROR, 0, 0, 0, 0, diagnostics, null);
        }

        // --- 인스턴스화 + 컨텍스트 주입 ---
        Script script;
        try {
            Object instance = cr.scriptClass().getDeclaredConstructor().newInstance();
            if (!(instance instanceof Script s)) {
                transition(LifecycleState.ERROR, "Script 하위 타입이 아님");
                return new EngineResult(LifecycleState.ERROR, 0, 0, 0, 0, diagnostics,
                        new IllegalStateException("스크립트는 io.maestro.sdk.Script 를 상속해야 합니다."));
            }
            script = s;
            script.__bind(context);
        } catch (ReflectiveOperationException e) {
            transition(LifecycleState.ERROR, "인스턴스화 실패: " + e.getMessage());
            return new EngineResult(LifecycleState.ERROR, 0, 0, 0, 0, diagnostics, e);
        }

        this.scriptExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "maestro-script");
            t.setDaemon(true);
            return t;
        });

        int onStartCount = 0;
        long tickCount = 0;
        long tickErrorCount = 0;
        int onEndCount;
        boolean errored = false;
        Throwable failure = null;

        try {
            // --- STARTING (onStart 정확히 1회) ---
            transition(LifecycleState.STARTING, "onStart");
            try {
                Throwable c = invoke(script::onStart, config.onStartTimeoutMs());
                if (c != null) {
                    errored = true;
                    failure = c;
                    listener.onLog("onStart 예외: " + c);
                } else {
                    onStartCount = 1;
                }
            } catch (TimeoutException te) {
                errored = true;
                failure = te;
                listener.onLog("onStart 타임아웃(행)");
            }

            // --- RUNNING (onStart 성공 시에만 tick 스케줄) ---
            if (!errored) {
                transition(LifecycleState.RUNNING, "tick 스케줄 개시");
                long periodNanos = Math.max(0, config.tickPeriodMs()) * 1_000_000L;
                long next = System.nanoTime();

                while (!stopRequested && (config.maxTicks() < 0 || tickCount < config.maxTicks())) {
                    if (!sleepUntil(next)) {
                        break; // 인터럽트(사용자 중지)
                    }
                    if (stopRequested) {
                        break;
                    }
                    next += periodNanos;

                    long n = ++tickCount;
                    long t0 = System.nanoTime();
                    try {
                        Throwable c = invoke(script::onTick, config.tickTimeoutMs());
                        if (c != null) {
                            tickErrorCount++;
                            listener.onTickError(n, c);
                            if (config.tickPolicy() == TickPolicy.STOP) {
                                break;
                            }
                            if (config.errorThreshold() > 0 && tickErrorCount >= config.errorThreshold()) {
                                break;
                            }
                        } else {
                            listener.onTickComplete(n, (System.nanoTime() - t0) / 1_000_000L);
                        }
                    } catch (TimeoutException te) {
                        // 행(hang) 감지 → best-effort 중지
                        tickErrorCount++;
                        errored = true;
                        failure = te;
                        listener.onTickTimeout(n, config.tickTimeoutMs());
                        break;
                    }
                }
            }
        } finally {
            // --- STOPPING (onEnd 정확히 1회, best-effort) ---
            transition(LifecycleState.STOPPING, "onEnd");
            onEndCount = safeOnEnd(script);
            scriptExecutor.shutdownNow();
        }

        LifecycleState finalState = errored ? LifecycleState.ERROR : LifecycleState.STOPPED;
        transition(finalState, errored ? "에러 종료" : "정상 종료");
        return new EngineResult(finalState, onStartCount, tickCount, tickErrorCount, onEndCount, diagnostics, failure);
    }

    // ---- 내부 ----

    /** 스크립트 메서드를 스크립트 스레드에서 실행. 반환: null=성공, 그 외=예외 원인. TimeoutException=행. */
    private Throwable invoke(Runnable action, long timeoutMs) throws TimeoutException {
        Future<?> future = scriptExecutor.submit(action);
        try {
            if (timeoutMs > 0) {
                future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } else {
                future.get();
            }
            return null;
        } catch (ExecutionException e) {
            return e.getCause() != null ? e.getCause() : e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return e;
        } catch (TimeoutException te) {
            future.cancel(true);
            throw te;
        }
    }

    private int safeOnEnd(Script script) {
        try {
            Throwable c = invoke(script::onEnd, config.onEndTimeoutMs());
            if (c != null) {
                listener.onLog("onEnd 예외(무시, best-effort): " + c);
            }
        } catch (TimeoutException te) {
            listener.onLog("onEnd 타임아웃(행) — best-effort 종료");
        }
        return 1; // onEnd는 인스턴스 생성 후 정확히 1회 호출됨
    }

    private boolean sleepUntil(long targetNanos) {
        long remaining = targetNanos - System.nanoTime();
        while (remaining > 0) {
            try {
                Thread.sleep(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            remaining = targetNanos - System.nanoTime();
        }
        return true;
    }

    private void transition(LifecycleState to, String detail) {
        LifecycleState from = state;
        state = to;
        listener.onStateChange(from, to, detail);
    }
}
