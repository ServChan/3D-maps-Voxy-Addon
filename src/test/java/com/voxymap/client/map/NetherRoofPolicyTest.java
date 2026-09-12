package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetherRoofPolicyTest {
    @Test
    void cutsOnlyTheTopRoofLayerInTheStandardNether() {
        assertEquals(120.0f, NetherRoofPolicy.cutoff(0, 128));
    }

    @Test
    void respectsADimensionWithAShiftedMinimum() {
        assertEquals(56.0f, NetherRoofPolicy.cutoff(-64, 128));
    }

    @Test
    void neverCutsBelowTheFloorInADatapackShrunkDimension() {
        // logicalHeight (4) is smaller than the roof margin (8): the naive formula would place the
        // cut plane below minY and remove the whole dimension instead of just its roof.
        assertEquals(0.0f, NetherRoofPolicy.cutoff(0, 4));
    }
}
