package io.maestro.sdk;

/**
 * Maestro 스크립트의 베이스 클래스. 사용자는 이 클래스를 상속해 순수 자바 스크립트를 작성한다.
 *
 * <h2>라이프사이클 보장 (FR-3)</h2>
 * <ul>
 *   <li>{@link #onStart()} — 프로세스당 <b>정확히 1회</b>. 실패 시 프로세스는 ERROR 전이 후 {@link #onEnd()} 호출.</li>
 *   <li>{@link #onTick()} — 지정한 실행 주기마다 반복. tick 단위 예외는 격리된다
 *       (기본 정책 {@code continue}; 임계 초과 시 {@code stop} — ADR-0002 O-5).</li>
 *   <li>{@link #onEnd()} — 종료/중지 시 <b>정확히 1회</b>(정상 종료·사용자 중지·에러 종료 모두).
 *       단, {@code kill -9}/OOM/전원차단에서는 <b>best-effort</b>이며 보장되지 않는다(분석 R-2).</li>
 * </ul>
 *
 * <p>각 스크립트는 격리된 JVM 프로세스(러너)에서 실행되므로, 한 스크립트의 예외/크래시/OOM/행이
 * 다른 스크립트에 영향을 주지 않는다(FR-5).</p>
 */
public abstract class Script {

    /** 런타임이 주입하는 실행 컨텍스트. {@link #onStart()} 호출 전에 설정됨이 보장된다. */
    protected ScriptContext ctx;

    /** 최초 실행 시 정확히 1회 호출된다. 리소스 초기화·구독 등록에 사용. */
    public void onStart() {}

    /** 지정한 실행 주기마다 반복 호출된다. 짧고 논블로킹으로 작성한다(행 감지 워치독 존재). */
    public void onTick() {}

    /** 종료/중지 시 정확히 1회 호출된다(best-effort). 리소스 정리·flush에 사용. */
    public void onEnd() {}

    /** 런타임 전용: 컨텍스트 주입. 사용자 코드에서 호출하지 않는다. */
    public final void __bind(ScriptContext context) {
        this.ctx = context;
    }
}
