package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeOfDayTest {
    private static final VoxyMapSettings.TimeOfDay[] MODES = VoxyMapSettings.TimeOfDay.values();

    @Test
    void cyclesFromAutoThroughTheDayInOrder() {
        assertEquals(java.util.List.of(
                        VoxyMapSettings.TimeOfDay.AUTO,
                        VoxyMapSettings.TimeOfDay.MORNING,
                        VoxyMapSettings.TimeOfDay.DAY,
                        VoxyMapSettings.TimeOfDay.EVENING,
                        VoxyMapSettings.TimeOfDay.NIGHT),
                java.util.List.of(MODES));
    }

    @Test
    void fixedModesPinAnIncreasingClockAndOnlyNightIsDark() {
        long previous = -1;
        for (VoxyMapSettings.TimeOfDay mode : MODES) {
            if (mode == VoxyMapSettings.TimeOfDay.AUTO) {
                assertEquals(-1L, mode.clockTicks(), "AUTO must not pin the clock");
                continue;
            }
            assertTrue(mode.clockTicks() > previous, mode + " must come later in the day");
            previous = mode.clockTicks();
            assertEquals(mode == VoxyMapSettings.TimeOfDay.NIGHT, mode.isDark(), mode.name());
            assertEquals(mode.isDark() ? 11 : 0, mode.skyDarken(), mode.name());
        }
    }

    @Test
    void morningAndEveningAreDaylightOnBothSidesOfNoon() {
        assertTrue(VoxyMapSettings.TimeOfDay.MORNING.clockTicks() > 0
                && VoxyMapSettings.TimeOfDay.MORNING.clockTicks() < 6000);
        assertTrue(VoxyMapSettings.TimeOfDay.EVENING.clockTicks() > 6000
                && VoxyMapSettings.TimeOfDay.EVENING.clockTicks() < 12000);
        assertFalse(VoxyMapSettings.TimeOfDay.MORNING.isDark());
        assertFalse(VoxyMapSettings.TimeOfDay.EVENING.isDark());
    }
}
