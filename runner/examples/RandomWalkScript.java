import io.maestro.sdk.Script;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 상태 저장소(ctx.state) + 난수 예제. 매 tick ±1 랜덤 워크로 위치를 갱신하고 emit한다.
 * ctx.state()를 쓰므로 재시작 간 위치가 복원될 수 있다(영속 구현 시).
 * 플로우 producer 노드로 사용 가능(포트: out).
 * 실행: ./gradlew :runner:run --args="examples/RandomWalkScript.java --ticks 20 --period 300"
 */
public class RandomWalkScript extends Script {

    @Override
    public void onStart() {
        if (!ctx.state().contains("pos")) {
            ctx.state().put("pos", 0);
        }
    }

    @Override
    public void onTick() {
        int pos = ctx.state().get("pos", Integer.class).orElse(0);
        pos += ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        ctx.state().put("pos", pos);
        ctx.log().info("position = {}", pos);
        ctx.emit("out", pos);
    }
}
