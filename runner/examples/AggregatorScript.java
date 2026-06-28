import io.maestro.sdk.Script;

/**
 * 플로우 변환(transform) 노드 예제. 입력 포트 {@code in}의 숫자 메시지를 누적하고,
 * 매 tick 평균을 계산해 출력 포트 {@code out}으로 내보낸다(consume → aggregate → emit).
 *
 * 플로우 배치: [producer] --out→in--> [AggregatorScript] --out→in--> [sink]
 */
public class AggregatorScript extends Script {

    private double sum = 0;
    private long count = 0;

    @Override
    public void onStart() {
        ctx.onMessage("in", msg -> {
            try {
                sum += Double.parseDouble(String.valueOf(msg));
                count++;
            } catch (NumberFormatException e) {
                ctx.log().warn("숫자 아님(무시): {}", msg);
            }
        });
    }

    @Override
    public void onTick() {
        if (count == 0) {
            return; // 받은 게 없으면 조용히
        }
        double avg = sum / count;
        ctx.log().info("평균 = {} (n={})", avg, count);
        ctx.emit("out", avg);
    }
}
