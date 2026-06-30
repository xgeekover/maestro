package io.maestro.backend.process;

import io.maestro.backend.config.MaestroProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** QA C-2: 리소스 한도 기본값 강제 + 상한 클램프 검증. */
class RunConfigFactoryTest {

    private static final long MB = 1024 * 1024;
    private final RunConfigFactory f = new RunConfigFactory(new MaestroProperties());

    @Test
    void appliesDefaultTickTimeoutAndHeapWhenUnset() {
        RunConfig c = f.forRun(1000L, Map.of(), false, null, null, null);
        assertEquals(30_000, c.tickTimeoutMs(), "행 워치독 기본값 강제(0 아님)");
        assertEquals(512 * MB, c.maxHeapBytes(), "heap 캡 기본값 강제");
    }

    @Test
    void zeroValuesTreatedAsUnset() {
        RunConfig c = f.forRun(1000L, Map.of(), false, 0L, 0L, null);
        assertEquals(30_000, c.tickTimeoutMs());
        assertEquals(512 * MB, c.maxHeapBytes());
    }

    @Test
    void clampsTickTimeoutToMax() {
        assertEquals(300_000, f.forRun(1000L, Map.of(), false, null, 999_999_999L, null).tickTimeoutMs());
    }

    @Test
    void clampsHeapToMax() {
        assertEquals(4L * 1024 * MB, f.forRun(1000L, Map.of(), false, 99_999_999_999L, null, null).maxHeapBytes());
    }

    @Test
    void negativeOrZeroPeriodBecomesSafeDefault() {
        assertEquals(1000, f.forRun(-5L, Map.of(), false, null, null, null).tickPeriodMs());
        assertEquals(1000, f.forRun(0L, Map.of(), false, null, null, null).tickPeriodMs());
    }

    @Test
    void respectsExplicitWithinBounds() {
        RunConfig c = f.forRun(500L, Map.of("k", "v"), true, 100 * MB, 5000L, 3L);
        assertEquals(500, c.tickPeriodMs());
        assertEquals(100 * MB, c.maxHeapBytes());
        assertEquals(5000, c.tickTimeoutMs());
        assertTrue(c.stopOnError());
        assertEquals(3, c.errorThreshold());
        assertEquals("v", c.params().get("k"));
    }

    @Test
    void flowNodeAlsoGetsDefaultLimits() {
        RunConfig c = f.forFlowNode(200L, Map.of());
        assertEquals(200, c.tickPeriodMs());
        assertEquals(30_000, c.tickTimeoutMs(), "플로우 노드도 워치독 강제");
        assertEquals(512 * MB, c.maxHeapBytes());
    }

    @Test
    void flowNodeNullPeriodDefaults() {
        assertEquals(1000, f.forFlowNode(null, null).tickPeriodMs());
    }
}
