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
}
