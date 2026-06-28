import io.maestro.sdk.Script;

/**
 * tick 예외 격리 데모. {@code everyN}(기본 5)번째 tick마다 예외를 던진다.
 * 기본 정책(CONTINUE)에서는 예외가 격리되어 계속 실행되고, 대시보드의 error 카운터가 증가한다.
 * STOP 정책으로 실행하면 첫 예외에서 중지된다.
 * 실행: ./gradlew :runner:run --args="examples/FlakyScript.java --ticks 12 --period 300 --param everyN=4"
 */
public class FlakyScript extends Script {

    private int tick = 0;
    private int everyN;

    @Override
    public void onStart() {
        Integer n = ctx.param("everyN", Integer.class);
        everyN = (n == null || n <= 0) ? 5 : n;
    }

    @Override
    public void onTick() {
        tick++;
        if (tick % everyN == 0) {
            throw new RuntimeException("주입된 결함 (tick " + tick + ")");
        }
        ctx.log().info("정상 tick {}", tick);
    }
}
