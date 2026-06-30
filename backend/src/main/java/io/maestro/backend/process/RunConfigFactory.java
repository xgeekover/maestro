package io.maestro.backend.process;

import io.maestro.backend.config.MaestroProperties;
import io.maestro.backend.config.MaestroProperties.Limits;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 원시 실행 파라미터로부터 안전한 {@link RunConfig}를 생성한다 (QA C-2).
 *
 * <p>미지정 값은 기본값을 강제하고(특히 tickTimeout·maxHeap → 행 워치독/heap 캡 항상 적용),
 * 지정 값은 상한으로 클램프한다. REST·플로우 노드 양쪽이 동일 정책을 사용한다.</p>
 */
@Component
public class RunConfigFactory {

    private final MaestroProperties props;

    public RunConfigFactory(MaestroProperties props) {
        this.props = props;
    }

    public RunConfig forRun(Long tickPeriodMs, Map<String, String> params, Boolean stopOnError,
                            Long maxHeapBytes, Long tickTimeoutMs, Long errorThreshold) {
        Limits l = props.getLimits();

        long period = (tickPeriodMs == null || tickPeriodMs <= 0) ? 1000 : tickPeriodMs;
        period = Math.max(l.getMinTickPeriodMs(), period);

        long heap = (maxHeapBytes != null && maxHeapBytes > 0)
                ? Math.min(maxHeapBytes, l.getMaxMaxHeapBytes())
                : l.getDefaultMaxHeapBytes();

        long tickTimeout = (tickTimeoutMs != null && tickTimeoutMs > 0)
                ? Math.min(tickTimeoutMs, l.getMaxTickTimeoutMs())
                : l.getDefaultTickTimeoutMs();

        long errThreshold = (errorThreshold != null && errorThreshold > 0) ? errorThreshold : 0;

        return new RunConfig(
                period,
                params == null ? Map.of() : Map.copyOf(params),
                Boolean.TRUE.equals(stopOnError),
                heap,
                tickTimeout,
                0,
                l.getDefaultOnEndTimeoutMs(),
                errThreshold);
    }

    /** 플로우 노드용 — 노드 주기/파라미터만 받고 나머지는 기본 한도 적용. */
    public RunConfig forFlowNode(Long tickPeriodMs, Map<String, String> params) {
        return forRun(tickPeriodMs, params, false, null, null, null);
    }
}
