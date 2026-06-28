package io.maestro.backend.flow;

import com.google.protobuf.ByteString;
import io.maestro.backend.flow.FlowDeployment.Target;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 백프레셔 단위 테스트: 바운디드 큐 포화 시 drop-oldest, 미라우팅 포트 무시. */
class FlowDeploymentBackpressureTest {

    @Test
    void dropsOldestWhenQueueFull() {
        // 디스패처는 start()하지 않음 → 큐가 소진되지 않아 포화 → 드롭 발생
        Map<String, List<Target>> routes = Map.of("a|out", List.of(new Target("b", "in")));
        FlowDeployment dep = new FlowDeployment("f", routes, 2);
        for (int i = 0; i < 5; i++) {
            dep.enqueue("a", "out", ByteString.copyFromUtf8("" + i));
        }
        assertEquals(3, dep.droppedCount(), "용량 2에 5건 적재 → 3건 드롭");
    }

    @Test
    void unroutedPortIsIgnored() {
        FlowDeployment dep = new FlowDeployment("f", Map.of(), 2);
        dep.enqueue("a", "out", ByteString.copyFromUtf8("x"));
        assertEquals(0, dep.droppedCount());
    }

    @Test
    void fanOutToMultipleTargets() {
        Map<String, List<Target>> routes = Map.of(
                "a|out", List.of(new Target("b", "in"), new Target("c", "in")));
        FlowDeployment dep = new FlowDeployment("f", routes, 100);
        dep.enqueue("a", "out", ByteString.copyFromUtf8("x"));
        // 큐에 2건(두 타깃)이 적재되고 드롭 없음
        assertTrue(dep.droppedCount() == 0);
    }
}
