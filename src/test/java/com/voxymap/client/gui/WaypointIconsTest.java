package com.voxymap.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaypointIconsTest {
    @Test
    void initialsAreDarkOnBrightColoursAndLightOnDarkOnes() {
        assertEquals(0xFF0A0A12, WaypointIcons.textColorFor(0xFFFFC94D));
        assertEquals(0xFFFFFFFF, WaypointIcons.textColorFor(0xFF2040A0));
    }

    @Test
    void otherDimensionIconsKeepTheirColourButFade() {
        assertEquals(0x99112233, WaypointIcons.withAlpha(0xFF112233, WaypointIcons.OTHER_DIMENSION_ALPHA));
        assertEquals(0x00112233, WaypointIcons.withAlpha(0xFF112233, 0));
    }
}
