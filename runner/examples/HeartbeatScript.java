import io.maestro.sdk.Script;

/**
 * 예제 스크립트: 주기마다 하트비트를 로그/emit 하고 카운터를 누적한다.
 * 실행:  ./gradlew :runner:run --args="examples/HeartbeatScript.java --ticks 3 --period 300"
 */
public class HeartbeatScript extends Script {

    @Override
    public void onStart() {
        ctx.log().info("하트비트 시작");
        ctx.state().put("beats", 0);
    }

    @Override
    public void onTick() {
        int beats = ctx.state().get("beats", Integer.class).orElse(0) + 1;
        ctx.state().put("beats", beats);
        ctx.log().info("beat #{}", beats);
        ctx.emit("heartbeat", beats);
    }

    @Override
    public void onEnd() {
        int beats = ctx.state().get("beats", Integer.class).orElse(0);
        ctx.log().info("종료 — 총 {} beats", beats);
    }
}
