import io.maestro.sdk.Script;

/**
 * 인스턴스 상태(필드)를 tick 간 유지하는 예제. 매 tick 다음 피보나치 수를 계산해 로그하고 emit한다.
 * 플로우에서 상류(producer) 노드로 사용 가능(포트: out).
 * 실행: ./gradlew :runner:run --args="examples/FibonacciScript.java --ticks 10 --period 500"
 */
public class FibonacciScript extends Script {

    private long a = 0;
    private long b = 1;

    @Override
    public void onTick() {
        long next = a;
        a = b;
        b = next + b;
        if (b < 0) { // overflow 시 리셋
            a = 0;
            b = 1;
        }
        ctx.log().info("fib = {}", next);
        ctx.emit("out", next);
    }
}
