package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapPlacementTest {
    @Test
    void cornersSitInsideTheMargin() {
        assertEquals(8, MinimapPlacement.place(400, 300, 128, 30, VoxyMapSettings.MinimapCorner.TOP_LEFT, 0, 0, 0).x());
        MinimapPlacement.Placement topRight = MinimapPlacement.place(400, 300, 128, 30,
                VoxyMapSettings.MinimapCorner.TOP_RIGHT, 0, 0, 26);
        assertEquals(400 - 8 - 128, topRight.x());
        assertEquals(8 + 26, topRight.y());
        assertTrue(topRight.anchorRight());
        MinimapPlacement.Placement bottomRight = MinimapPlacement.place(400, 300, 128, 30,
                VoxyMapSettings.MinimapCorner.BOTTOM_RIGHT, 0, 0, 0);
        assertEquals(300 - 8 - 128 - 30, bottomRight.y());
        assertFalse(bottomRight.infoAbove());
    }

    @Test
    void customPositionsSpanTheFreeSpaceAndRoundTrip() {
        MinimapPlacement.Placement middle = MinimapPlacement.place(400, 300, 100, 0,
                VoxyMapSettings.MinimapCorner.CUSTOM, 0.5, 0.5, 0);
        assertEquals(8 + 142, middle.x());
        assertEquals(8 + 92, middle.y());
        assertEquals(0.5, MinimapPlacement.fraction(middle.x(), 400, 100), 1e-9);
        assertEquals(0.0, MinimapPlacement.fraction(-50, 400, 100));
        assertEquals(1.0, MinimapPlacement.fraction(5000, 400, 100));
    }

    @Test
    void infoLinesMoveAboveAMinimapDraggedToTheBottom() {
        MinimapPlacement.Placement bottom = MinimapPlacement.place(400, 300, 100, 40,
                VoxyMapSettings.MinimapCorner.CUSTOM, 0.0, 1.0, 0);
        assertTrue(bottom.infoAbove());
    }

    @Test
    void nightDarkensTheMinimapButNeverBlacksItOut() {
        assertEquals(1.0, MinimapPlacement.nightBrightness(0));
        assertEquals(MinimapPlacement.MIN_NIGHT_BRIGHTNESS, MinimapPlacement.nightBrightness(11), 1e-9);
        assertEquals(MinimapPlacement.MIN_NIGHT_BRIGHTNESS, MinimapPlacement.nightBrightness(40), 1e-9);
    }
}
