package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetherSlicePolicyTest {
    @Test
    void followsPlayerHeightWithEnoughHeadroomToKeepNearbyTerrainReadable() {
        assertEquals(58.0f, NetherSlicePolicy.cutoff(0, 128, 50.9));
    }

    @Test
    void neverLeavesTheBedrockRoofVisibleForPlayersNearOrAboveIt() {
        assertEquals(120.0f, NetherSlicePolicy.cutoff(0, 128, 117.0));
        assertEquals(120.0f, NetherSlicePolicy.cutoff(0, 128, 200.0));
    }

    @Test
    void respectsShiftedDimensionMinimumsAndNeverCutsBelowTheWorld() {
        assertEquals(-56.0f, NetherSlicePolicy.cutoff(-64, 128, -64.0));
        assertEquals(-64.0f, NetherSlicePolicy.cutoff(-64, 128, -100.0));
    }
}
