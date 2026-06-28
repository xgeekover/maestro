import io.maestro.sdk.Script;

/**
 * CPU 메트릭 데모. 매 tick {@code iterations}(기본 5,000,000)회 연산을 수행해 부하를 만든다.
 * 대시보드의 CPU% 스파크라인이 반응하는 것을 확인할 수 있다.
 * onTick은 짧게 유지하는 것이 원칙이므로 iterations를 과도하게 키우지 말 것(tick 워치독 존재).
 * 실행: ./gradlew :runner:run --args="examples/CpuBusyScript.java --ticks 20 --period 500 --param iterations=8000000"
 */
public class CpuBusyScript extends Script {

    private long iterations;

    @Override
    public void onStart() {
        Long it = ctx.param("iterations", Long.class);
        iterations = (it == null || it <= 0) ? 5_000_000L : it;
    }

    @Override
    public void onTick() {
        double acc = 0;
        for (long i = 0; i < iterations; i++) {
            acc += Math.sqrt(i);
        }
        ctx.log().info("work done (checksum={})", (long) acc & 0xff);
    }
}
