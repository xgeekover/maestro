package io.maestro.protocol.v1;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 프로토콜 계약 테스트: 메시지 직렬화 round-trip + enum/서비스명 안정성(backend↔runner 호환 보장). */
class ProtocolContractTest {

    @Test
    void runnerHelloRoundTrips() throws InvalidProtocolBufferException {
        RunnerMessage msg = RunnerMessage.newBuilder()
                .setHello(Hello.newBuilder()
                        .setRunnerId("r1").setScriptId("s1").setAuthToken("tok").setSdkVersion("0.1.0"))
                .build();
        RunnerMessage back = RunnerMessage.parseFrom(msg.toByteArray());
        assertEquals(RunnerMessage.PayloadCase.HELLO, back.getPayloadCase());
        assertEquals("r1", back.getHello().getRunnerId());
        assertEquals("tok", back.getHello().getAuthToken());
    }

    @Test
    void backendStartCommandRoundTrips() throws InvalidProtocolBufferException {
        BackendMessage msg = BackendMessage.newBuilder()
                .setStart(StartCommand.newBuilder()
                        .setSource("class X {}")
                        .setTickPeriodMs(500)
                        .setTickPolicy(TickExceptionPolicy.STOP)
                        .putParams("k", "v")
                        .setLimits(ResourceLimits.newBuilder().setMaxHeapBytes(123).setTickTimeoutMs(1000)))
                .build();
        BackendMessage back = BackendMessage.parseFrom(msg.toByteArray());
        assertEquals(BackendMessage.PayloadCase.START, back.getPayloadCase());
        assertEquals("class X {}", back.getStart().getSource());
        assertEquals(500, back.getStart().getTickPeriodMs());
        assertEquals(TickExceptionPolicy.STOP, back.getStart().getTickPolicy());
        assertEquals("v", back.getStart().getParamsMap().get("k"));
        assertEquals(123, back.getStart().getLimits().getMaxHeapBytes());
    }

    @Test
    void emitMessageCarriesBytes() throws InvalidProtocolBufferException {
        RunnerMessage msg = RunnerMessage.newBuilder()
                .setEmit(EmitMessage.newBuilder().setPort("out").setPayloadJson(ByteString.copyFromUtf8("42")))
                .build();
        RunnerMessage back = RunnerMessage.parseFrom(msg.toByteArray());
        assertEquals("out", back.getEmit().getPort());
        assertEquals("42", back.getEmit().getPayloadJson().toStringUtf8());
    }

    @Test
    void lifecycleStateNamesStable() {
        assertEquals("RUNNING", LifecycleState.RUNNING.name());
        assertEquals("STOPPED", LifecycleState.STOPPED.name());
        assertEquals("ERROR", LifecycleState.ERROR.name());
    }

    @Test
    void serviceNameStable() {
        assertEquals("maestro.v1.RunnerGateway", RunnerGatewayGrpc.SERVICE_NAME);
    }
}
