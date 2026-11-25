package com.teamc.isow.backend.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** テスト用の、進められる時計（日本時間） */
public class MutableClock extends Clock {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    private volatile Instant now;

    public MutableClock(Instant now) {
        this.now = now;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
        return now;
    }
}
