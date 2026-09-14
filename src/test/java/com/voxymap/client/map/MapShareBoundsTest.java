package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapShareBoundsTest {
    @Test
    void normalizesCornersAndRoundsOutwardToWholeVoxySections() {
        MapShareBounds bounds = MapShareBounds.fromBlockCoordinates(65.2, -1.0, -0.1, 63.9);

        assertTrue(bounds.containsSection(-1, -1));
        assertTrue(bounds.containsSection(2, 1));
        assertFalse(bounds.containsSection(3, 1));
        assertFalse(bounds.containsSection(2, 2));
        assertTrue(bounds.widthBlocks() == 128);
        assertTrue(bounds.depthBlocks() == 96);
    }
}
