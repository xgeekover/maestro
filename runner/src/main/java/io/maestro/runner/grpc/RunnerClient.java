package io.maestro.runner.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.maestro.protocol.v1.BackendMessage;
import io.maestro.protocol.v1.Hello;
import io.maestro.protocol.v1.RunnerGatewayGrpc;
import io.maestro.protocol.v1.RunnerMessage;
import io.maestro.protocol.v1.StartCommand;
import io.maestro.protocol.v1.TickExceptionPolicy;
import io.maestro.runner.engine.EngineConfig;
import io.maestro.runner.engine.LifecycleEngine;
import io.maestro.runner.engine.TickPolicy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 백엔드 연결 모드 러너. 백엔드 gRPC RunnerGateway로 아웃바운드 접속해 단일 양방향 스트림을 열고
 * 텔레메트리↑/명령↓을 주고받는다. StartCommand 수신 시 {@link LifecycleEngine}을 별도 스레드로 구동.
 */
public final class RunnerClient {

    private final String host;
    private final int port;
    private final String runnerId;
    private final String scriptId;
    private final String token;

    private final CountDownLatch finished = new CountDownLatch(1);
    private final AtomicReference<LifecycleEngine> engineRef = new AtomicReference<>();
    private final AtomicReference<GrpcContext> contextRef = new AtomicReference<>();
    private volatile StreamSender sender;

    public RunnerClient(String host, int port, String runnerId, String scriptId, String token) {
        this.host = host;
        this.port = port;
        this.runnerId = runnerId;
        this.scriptId = scriptId;
        this.token = token;
    }

    public void run() throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        try {
            RunnerGatewayGrpc.RunnerGatewayStub stub = RunnerGatewayGrpc.newStub(channel);

            StreamObserver<BackendMessage> responseObserver = new StreamObserver<>() {
                @Override
                public void onNext(BackendMessage msg) {
                    switch (msg.getPayloadCase()) {
                        case START -> handleStart(msg.getStart());
                        case STOP -> {
                            LifecycleEngine e = engineRef.get();
                            if (e != null) {
                                e.stop();
                            }
                        }
                        case DELIVER -> {
                            GrpcContext c = contextRef.get();
                            if (c != null) {
                                c.deliver(msg.getDeliver().getInPort(),
                                        msg.getDeliver().getPayloadJson().toStringUtf8());
                            }
                        }
                        case UPDATE_PERIOD -> {
                            LifecycleEngine e = engineRef.get();
                            if (e != null) {
                                e.setTickPeriodMs(msg.getUpdatePeriod().getTickPeriodMs());
                            }
                        }
                        case HEARTBEAT, STATE_RESULT, PAYLOAD_NOT_SET -> {
                            // 미사용/무시
                        }
                    }
                }

                @Override
                public void onError(Throwable t) {
                    stopEngine();
                    finished.countDown();
                }

                @Override
                public void onCompleted() {
                    stopEngine();
                    finished.countDown();
                }
            };

            StreamObserver<RunnerMessage> request = stub.session(responseObserver);
            this.sender = new StreamSender(request);

            // 핸드셰이크
            sender.send(RunnerMessage.newBuilder()
                    .setHello(Hello.newBuilder()
                            .setRunnerId(runnerId)
                            .setScriptId(scriptId)
                            .setAuthToken(token)
                            .setSdkVersion("0.1.0"))
                    .build());

            finished.await();
        } finally {
            channel.shutdownNow();
            try {
                channel.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void handleStart(StartCommand cmd) {
        RunnerStats stats = new RunnerStats();
        GrpcContext ctx = new GrpcContext(scriptId, cmd.getParamsMap(), sender);
        contextRef.set(ctx);

        EngineConfig cfg = EngineConfig.builder()
                .tickPeriodMs(cmd.getTickPeriodMs() > 0 ? cmd.getTickPeriodMs() : 1000)
                .maxTicks(-1)
                .tickPolicy(cmd.getTickPolicy() == TickExceptionPolicy.STOP ? TickPolicy.STOP : TickPolicy.CONTINUE)
                .errorThreshold(cmd.getLimits().getErrorThreshold())
                .tickTimeoutMs(cmd.getLimits().getTickTimeoutMs())
                .onStartTimeoutMs(cmd.getLimits().getOnstartTimeoutMs())
                .onEndTimeoutMs(cmd.getLimits().getOnendTimeoutMs() > 0 ? cmd.getLimits().getOnendTimeoutMs() : 5000)
                .build();

        GrpcTelemetryListener listener = new GrpcTelemetryListener(sender, stats);
        LifecycleEngine engine = new LifecycleEngine(cmd.getSource(), cfg, ctx, listener);
        engineRef.set(engine);

        MetricSampler sampler = new MetricSampler(sender, stats, 1000);
        Thread engineThread = new Thread(() -> {
            sampler.start();
            try {
                engine.run();
            } finally {
                sampler.stop();
                sender.complete();
                finished.countDown();
            }
        }, "maestro-engine");
        engineThread.setDaemon(true);
        engineThread.start();
    }

    private void stopEngine() {
        LifecycleEngine e = engineRef.get();
        if (e != null) {
            e.stop();
        }
    }
}
