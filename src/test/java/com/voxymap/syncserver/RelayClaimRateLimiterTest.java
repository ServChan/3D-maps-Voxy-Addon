package com.voxymap.syncserver;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayClaimRateLimiterTest {
    @Test
    void limitsEachRemoteAddressIndependently() {
        RelayClaimRateLimiter limiter = new RelayClaimRateLimiter(2, 1, TimeUnit.MINUTES);

        assertTrue(limiter.allow("127.0.0.1"));
        assertTrue(limiter.allow("127.0.0.1"));
        assertFalse(limiter.allow("127.0.0.1"));
        assertTrue(limiter.allow("192.0.2.10"));
    }
}
