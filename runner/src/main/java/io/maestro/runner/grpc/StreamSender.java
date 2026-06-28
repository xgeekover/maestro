package io.maestro.runner.grpc;

import io.grpc.stub.StreamObserver;
import io.maestro.protocol.v1.RunnerMessage;

/**
 * gRPC 요청 스트림(StreamObserver)은 스레드 안전하지 않다. 엔진 스레드·메트릭 샘플러·응답 스레드가
 * 동시에 보낼 수 있으므로 단일 락으로 직렬화한다.
 */
public final class StreamSender {

    private final StreamObserver<RunnerMessage> delegate;
    private final Object lock = new Object();
    private volatile boolean closed = false;

    public StreamSender(StreamObserver<RunnerMessage> delegate) {
        this.delegate = delegate;
    }

    public void send(RunnerMessage message) {
        synchronized (lock) {
            if (!closed) {
                delegate.onNext(message);
            }
        }
    }

    public void complete() {
        synchronized (lock) {
            if (!closed) {
                closed = true;
                delegate.onCompleted();
            }
        }
    }

    public void error(Throwable t) {
        synchronized (lock) {
            if (!closed) {
                closed = true;
                delegate.onError(t);
            }
        }
    }
}
