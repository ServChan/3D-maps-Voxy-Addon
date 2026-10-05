package com.voxymap.client.gui;

import com.voxymap.client.map.VoxyMapSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void shapedIconsAreSymmetricAndNeverEmpty() {
        for (VoxyMapSettings.WaypointIconShape shape : VoxyMapSettings.WaypointIconShape.values()) {
            for (int size = 9; size <= 21; size++) {
                for (int row = 0; row < size; row++) {
                    int[] span = WaypointIcons.rowSpan(shape, size, row);
                    assertEquals(size, span[0] + span[1], shape + " " + size + " " + row);
                    assertTrue(span[1] > span[0], shape + " " + size + " " + row);
                    assertArrayEquals(span, WaypointIcons.rowSpan(shape, size, size - 1 - row));
                }
                assertArrayEquals(new int[]{0, size}, WaypointIcons.rowSpan(shape, size, size / 2));
            }
        }
    }

    @Test
    void roundShapesNarrowTowardsTheEdges() {
        assertArrayEquals(new int[]{0, 11}, WaypointIcons.rowSpan(VoxyMapSettings.WaypointIconShape.SQUARE, 11, 0));
        assertArrayEquals(new int[]{3, 8}, WaypointIcons.rowSpan(VoxyMapSettings.WaypointIconShape.CIRCLE, 11, 0));
        assertArrayEquals(new int[]{5, 6}, WaypointIcons.rowSpan(VoxyMapSettings.WaypointIconShape.DIAMOND, 11, 0));
    }
}
