package io.maestro.runner.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimpleLoggerTest {

    @Test
    void formatsPlaceholdersInOrder() {
        assertEquals("a=1 b=2", SimpleLogger.format("a={} b={}", 1, 2));
    }

    @Test
    void noArgsReturnsMessageAsIs() {
        assertEquals("none {}", SimpleLogger.format("none {}"));
    }

    @Test
    void extraArgsIgnored() {
        assertEquals("x=1", SimpleLogger.format("x={}", 1, 2, 3));
    }

    @Test
    void noPlaceholdersWithArgs() {
        assertEquals("plain", SimpleLogger.format("plain", 1));
    }
}
