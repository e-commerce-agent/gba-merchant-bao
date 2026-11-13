package com.gba.merchantbao.test.support;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableAuthClock extends Clock {
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    public void advance(Duration duration) { now.updateAndGet(t -> t.plus(duration)); }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now.get(); }
}
