package com.dfs.metadata;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A clock a test can move forward, so expiry is tested by advancing time rather
 * than by waiting for it.
 */
final class MutableClock extends Clock {

    private final AtomicReference<Instant> now;

    MutableClock(Instant start) {
        this.now = new AtomicReference<>(start);
    }

    void advance(Duration by) {
        now.updateAndGet(instant -> instant.plus(by));
    }

    @Override
    public Instant instant() {
        return now.get();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
