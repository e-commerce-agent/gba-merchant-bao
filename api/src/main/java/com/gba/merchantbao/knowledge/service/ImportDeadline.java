package com.gba.merchantbao.knowledge.service;

import java.time.Duration;

/** One monotonic budget, shared by every embedding batch and Milvus operation. */
public final class ImportDeadline {
    private final long end;
    private final java.util.function.BooleanSupplier cancelled;
    public ImportDeadline() { this(Duration.ofSeconds(60)); }
    /** Chat supplies only the time left on its already committed run deadline. */
    public ImportDeadline(Duration remaining) { this(remaining, () -> false); }
    public ImportDeadline(Duration remaining, java.util.function.BooleanSupplier cancelled) {
        end = System.nanoTime() + remaining.toNanos(); this.cancelled = cancelled;
    }
    public Duration remaining() {
        long nanos = end - System.nanoTime();
        if (nanos <= 0 || Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new ImportTimeout();
        return Duration.ofNanos(nanos);
    }
    public static final class ImportTimeout extends RuntimeException { }
}
