import io.maestro.sdk.Script;

/**
 * 플로우 싱크(sink) 노드 예제. 입력 포트 {@code in}으로 들어온 메시지를 받아 로그한다.
 * 수신 건수를 상태에 누적한다. 플로우 하류 끝단으로 사용.
 *
 * 플로우 배치: [producer] --out→in--> [EchoScript]
 */
public class EchoScript extends Script {

    @Override
    public void onStart() {
        ctx.state().put("received", 0);
        ctx.onMessage("in", msg -> {
            int n = ctx.state().get("received", Integer.class).orElse(0) + 1;
            ctx.state().put("received", n);
            ctx.log().info("#{} 수신: {}", n, msg);
        });
    }
}
