package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FogSuppressionTest {
    private static final VoxyMapSettings.FogSuppression ALWAYS = VoxyMapSettings.FogSuppression.ALWAYS;
    private static final VoxyMapSettings.FogSuppression NETHER_ONLY = VoxyMapSettings.FogSuppression.NETHER_ONLY;
    private static final VoxyMapSettings.FogSuppression OFF = VoxyMapSettings.FogSuppression.OFF;

    @Test
    void appliesInTheRightDimensions() {
        assertTrue(ALWAYS.appliesIn(true));
        assertTrue(ALWAYS.appliesIn(false));

        assertTrue(NETHER_ONLY.appliesIn(true));
        assertFalse(NETHER_ONLY.appliesIn(false), "the Overworld and End keep their normal fog");

        assertFalse(OFF.appliesIn(true));
        assertFalse(OFF.appliesIn(false));
    }

    @Test
    void cyclesThroughOnNetherOnlyAndOff() {
        assertEquals(java.util.List.of(ALWAYS, NETHER_ONLY, OFF),
                java.util.List.of(VoxyMapSettings.FogSuppression.values()));
    }

    @Test
    void readsTheStoredModeCaseInsensitively() {
        assertEquals(NETHER_ONLY, VoxyMapSettings.FogSuppression.parse("nether_only", null));
        assertEquals(NETHER_ONLY, VoxyMapSettings.FogSuppression.parse(" NETHER_ONLY ", "false"),
                "a stored mode wins over the legacy on/off value");
        assertEquals(OFF, VoxyMapSettings.FogSuppression.parse("off", "true"));
    }

    @Test
    void migratesTheOldOnOffSetting() {
        assertEquals(ALWAYS, VoxyMapSettings.FogSuppression.parse(null, "true"));
        assertEquals(OFF, VoxyMapSettings.FogSuppression.parse(null, "false"));
        assertEquals(OFF, VoxyMapSettings.FogSuppression.parse(null, " FALSE "));
    }

    @Test
    void fallsBackToOnForMissingOrGarbledValues() {
        assertEquals(ALWAYS, VoxyMapSettings.FogSuppression.parse(null, null));
        assertEquals(ALWAYS, VoxyMapSettings.FogSuppression.parse("sometimes", null));
        assertEquals(OFF, VoxyMapSettings.FogSuppression.parse("sometimes", "false"),
                "an unreadable mode still honours the legacy value instead of silently resetting it");
    }
}
