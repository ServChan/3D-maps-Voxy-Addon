package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapSnapTest {
    @Test
    void snapsToTheNearEdgeWithinTheSnapDistance() {
        MinimapPlacement.Snap snap = MinimapPlacement.snap(MinimapPlacement.MARGIN + 4, 400, 100);
        assertEquals(MinimapPlacement.MARGIN, snap.position());
        assertEquals(MinimapPlacement.MARGIN, snap.guide());
    }

    @Test
    void snapsToTheFarEdgeAndDrawsTheGuideOnTheOuterSide() {
        int end = 400 - MinimapPlacement.MARGIN - 100;
        MinimapPlacement.Snap snap = MinimapPlacement.snap(end - 3, 400, 100);
        assertEquals(end, snap.position());
        assertEquals(end + 100, snap.guide());
    }

    @Test
    void snapsToTheCentre() {
        MinimapPlacement.Snap snap = MinimapPlacement.snap(152, 400, 100);
        assertEquals(150, snap.position());
        assertEquals(200, snap.guide());
        assertTrue(snap.snapped());
    }

    @Test
    void leavesFarPositionsAlone() {
        MinimapPlacement.Snap snap = MinimapPlacement.snap(80, 400, 100);
        assertEquals(80, snap.position());
        assertFalse(snap.snapped());
    }
}
