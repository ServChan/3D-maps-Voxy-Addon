package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointSyncClientTest {
    @Test
    void asciiPasswordsUseThePlainHeader() {
        assertTrue(WaypointSyncClient.asciiPassword("secret-123"));
        assertTrue(WaypointSyncClient.asciiPassword(null));
    }

    @Test
    void nonAsciiPasswordsAreSentAsBase64OfUtf8() {
        String password = "пароль";

        assertFalse(WaypointSyncClient.asciiPassword(password));
        String encoded = WaypointSyncClient.base64Password(password);
        assertTrue(encoded.chars().allMatch(c -> c < 0x80));
        assertEquals(password, new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8));
    }

    @Test
    void aFreshStateIsImmediatelyDueDespiteThePositiveClock() {
        long never = Long.MIN_VALUE / 2;
        assertTrue(WaypointSyncClient.due(System.nanoTime(), never, 4_000_000_000L));
        assertTrue(WaypointSyncClient.due(Long.MAX_VALUE / 4, never, 30_000_000_000L));
    }

    @Test
    void rejectionsThatCanNeverSucceedAreDroppedAndOthersRetried() {
        assertEquals(WaypointSyncClient.Outcome.DONE, WaypointSyncClient.classify(200, false));
        assertEquals(WaypointSyncClient.Outcome.DONE, WaypointSyncClient.classify(404, true));
        assertEquals(WaypointSyncClient.Outcome.REJECTED, WaypointSyncClient.classify(403, false));
        assertEquals(WaypointSyncClient.Outcome.REJECTED, WaypointSyncClient.classify(400, false));
        assertEquals(WaypointSyncClient.Outcome.REJECTED, WaypointSyncClient.classify(413, false));
        assertEquals(WaypointSyncClient.Outcome.RETRY_LATER, WaypointSyncClient.classify(401, false));
        assertEquals(WaypointSyncClient.Outcome.RETRY_LATER, WaypointSyncClient.classify(429, false));
        assertEquals(WaypointSyncClient.Outcome.RETRY_LATER, WaypointSyncClient.classify(503, false));
    }
}
