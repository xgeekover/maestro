import io.maestro.sdk.Script;

/**
 * 플로우 필터 노드 예제. 입력 포트 {@code in}으로 들어온 숫자가 임계값({@code threshold} 파라미터,
 * 기본 0) 이상이면 출력 포트 {@code out}으로 통과시킨다(consume → 조건부 emit).
 *
 * 플로우 배치: [producer] --out→in--> [FilterScript] --out→in--> [sink]
 */
public class FilterScript extends Script {

    private double threshold;

    @Override
    public void onStart() {
        Double t = ctx.param("threshold", Double.class);
        threshold = t == null ? 0.0 : t;
        ctx.onMessage("in", msg -> {
            try {
                double v = Double.parseDouble(String.valueOf(msg));
                if (v >= threshold) {
                    ctx.emit("out", v);
                    ctx.log().info("통과: {}", v);
                } else {
                    ctx.log().debug("필터됨: {}", v);
                }
            } catch (NumberFormatException e) {
                ctx.log().warn("숫자 아님(무시): {}", msg);
            }
        });
    }
}
