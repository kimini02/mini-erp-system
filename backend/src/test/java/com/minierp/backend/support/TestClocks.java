package com.minierp.backend.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class TestClocks {
    private TestClocks() {
    }

    public static Clock fixed() {
        return Clock.fixed(Instant.parse(System.getProperty("test.clock.instant", "2026-04-06T00:00:00Z")),
                ZoneId.of("Asia/Seoul"));
    }

    public static LocalDate today() {
        return LocalDate.now(fixed());
    }
}
