package io.maestro.backend.telemetry;

/** 프로세스별 로그 레코드(러너 보고). */
public record LogEntry(long epochMs, String level, String message, String thrown) {}
