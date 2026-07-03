package io.maestro.runner.grpc;

import com.google.protobuf.ByteString;
import io.maestro.protocol.v1.RunnerMessage;
import io.maestro.protocol.v1.StateOp;
import io.maestro.protocol.v1.StateResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 러너 → 백엔드 상태(KeyValueStore) 요청 클라이언트.
 * put/remove는 비동기(fire-and-forget), get/contains는 correlation_id로 StateResult를 동기 대기.
 */
public final class StateClient {

    private final StreamSender sender;
    private final long timeoutMs;
    private final AtomicLong seq = new AtomicLong();
    private final Map<String, CompletableFuture<StateResult>> pending = new ConcurrentHashMap<>();

    public StateClient(StreamSender sender, long timeoutMs) {
        this.sender = sender;
        this.timeoutMs = timeoutMs;
    }

    public void put(String key, String value) {
        send(StateOp.Op.PUT, "", key, value);
    }

    public void remove(String key) {
        send(StateOp.Op.REMOVE, "", key, null);
    }

    public StateResult get(String key) {
        return request(StateOp.Op.GET, key);
    }

    public StateResult contains(String key) {
        return request(StateOp.Op.CONTAINS, key);
    }

    /** 백엔드가 보낸 StateResult로 대기 중인 요청을 완료(RunnerClient onNext에서 호출). */
    public void complete(StateResult res) {
        CompletableFuture<StateResult> f = pending.get(res.getCorrelationId());
        if (f != null) {
            f.complete(res);
        }
    }

    private StateResult request(StateOp.Op op, String key) {
        String cid = "s" + seq.incrementAndGet();
        CompletableFuture<StateResult> f = new CompletableFuture<>();
        pending.put(cid, f);
        try {
            send(op, cid, key, null);
            return f.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return null; // 타임아웃/오류 → 미발견 취급
        } finally {
            pending.remove(cid);
        }
    }

    private void send(StateOp.Op op, String cid, String key, String value) {
        StateOp.Builder b = StateOp.newBuilder().setOp(op).setKey(key);
        if (!cid.isEmpty()) {
            b.setCorrelationId(cid);
        }
        if (value != null) {
            b.setValueJson(ByteString.copyFromUtf8(value));
        }
        sender.send(RunnerMessage.newBuilder().setStateOp(b).build());
    }
}
