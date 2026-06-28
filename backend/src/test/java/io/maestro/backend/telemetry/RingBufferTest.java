package io.maestro.backend.telemetry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingBufferTest {

    @Test
    void keepsInsertionOrderWithinCapacity() {
        RingBuffer<Integer> b = new RingBuffer<>(3);
        b.add(1);
        b.add(2);
        assertEquals(List.of(1, 2), b.snapshot());
        assertEquals(2, b.size());
    }

    @Test
    void overwritesOldestWhenFull() {
        RingBuffer<Integer> b = new RingBuffer<>(3);
        b.add(1);
        b.add(2);
        b.add(3);
        b.add(4);
        b.add(5);
        assertEquals(List.of(3, 4, 5), b.snapshot());
        assertEquals(3, b.size());
    }

    @Test
    void emptySnapshot() {
        assertTrue(new RingBuffer<Integer>(2).snapshot().isEmpty());
    }
}
