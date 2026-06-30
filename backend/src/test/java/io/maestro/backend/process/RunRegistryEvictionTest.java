package io.maestro.backend.process;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** QA H-4: 회수 메커니즘(레지스트리 제거 + 종료 시각 기록) 단위 검증. */
class RunRegistryEvictionTest {

    private static RunInfo newRun(String id) {
        return new RunInfo(id, "s", "n", "src", RunConfig.defaults(100, Map.of()));
    }

    @Test
    void removeClearsBothIndexes() {
        RunRegistry reg = new RunRegistry();
        RunInfo r = newRun("run1");
        r.setRunnerId("rn1");
        reg.register(r);
        assertNotNull(reg.byRunId("run1"));
        assertNotNull(reg.byRunnerId("rn1"));

        reg.remove(r);
        assertNull(reg.byRunId("run1"));
        assertNull(reg.byRunnerId("rn1"));
    }

    @Test
    void terminalTimestampSetOnceOnTerminalTransition() {
        RunInfo r = newRun("x");
        assertEquals(0, r.terminalSinceNanos());
        r.setStatus(RunStatus.RUNNING);
        assertEquals(0, r.terminalSinceNanos(), "비종료 전이는 기록 안 함");
        r.setStatus(RunStatus.STOPPED);
        long first = r.terminalSinceNanos();
        assertTrue(first > 0, "종료 전이 시 시각 기록");
        r.setStatus(RunStatus.ERROR);
        assertEquals(first, r.terminalSinceNanos(), "이미 종료면 갱신 안 함");
    }
}
