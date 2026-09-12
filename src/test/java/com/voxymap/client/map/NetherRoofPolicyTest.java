package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetherRoofPolicyTest {
    @Test
    void clearsTheUpperAntiXrayShellInTheStandardNether() {
        assertEquals(96.0f, NetherRoofPolicy.cutoff(0, 128));
    }

    @Test
    void respectsADimensionWithAShiftedMinimum() {
        assertEquals(32.0f, NetherRoofPolicy.cutoff(-64, 128));
    }

    @Test
    void neverCutsBelowTheFloorInADatapackShrunkDimension() {
        // logicalHeight (4) is smaller than the roof margin (32): the naive formula would place the
        // cut plane below minY and remove the whole dimension instead of just its roof.
        assertEquals(0.0f, NetherRoofPolicy.cutoff(0, 4));
    }
}
