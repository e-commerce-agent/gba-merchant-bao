package com.gba.merchantbao.knowledge.service;

import java.time.Duration;

/** One monotonic budget, shared by every embedding batch and Milvus operation. */
public final class ImportDeadline {
    private final long end = System.nanoTime() + Duration.ofSeconds(60).toNanos();
    public Duration remaining() {
        long nanos = end - System.nanoTime();
        if (nanos <= 0 || Thread.currentThread().isInterrupted()) throw new ImportTimeout();
        return Duration.ofNanos(nanos);
    }
    public static final class ImportTimeout extends RuntimeException { }
}
