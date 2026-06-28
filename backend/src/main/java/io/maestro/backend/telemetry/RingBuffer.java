package io.maestro.backend.telemetry;

import java.util.ArrayList;
import java.util.List;

/** 고정 용량 링버퍼(스레드 안전). 가득 차면 가장 오래된 항목을 덮어쓴다. */
public final class RingBuffer<T> {

    private final Object[] items;
    private final int capacity;
    private int head = 0;
    private int size = 0;

    public RingBuffer(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.items = new Object[this.capacity];
    }

    public synchronized void add(T item) {
        items[head] = item;
        head = (head + 1) % capacity;
        if (size < capacity) {
            size++;
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized List<T> snapshot() {
        List<T> result = new ArrayList<>(size);
        int start = (head - size + capacity) % capacity;
        for (int i = 0; i < size; i++) {
            result.add((T) items[(start + i) % capacity]);
        }
        return result;
    }

    public synchronized int size() {
        return size;
    }
}
