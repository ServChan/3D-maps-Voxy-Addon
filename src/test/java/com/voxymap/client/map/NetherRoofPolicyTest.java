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

    @Test
    void closeViewOpensDeeperAroundTheInspectedHeight() {
        NetherRoofPolicy.Aperture aperture = NetherRoofPolicy.aperture(0, 128, 50.0, 0.55, 1095);

        assertEquals(66.0f, aperture.innerCutoff());
        assertEquals(373.395f, aperture.radius(), 0.01f);
    }

    @Test
    void wideViewPreservesTheExistingSuccessfulRoofThreshold() {
        NetherRoofPolicy.Aperture aperture = NetherRoofPolicy.aperture(0, 128, 50.0, 1.0, 1095);

        assertEquals(96.0f, aperture.innerCutoff());
        assertEquals(678.9f, aperture.radius(), 0.01f);
    }

    @Test
    void apertureIsClampedAwayFromTheDimensionFloorAndRoof() {
        assertEquals(48.0f, NetherRoofPolicy.aperture(0, 128, -40.0, 0.1, 200).innerCutoff());
        assertEquals(96.0f, NetherRoofPolicy.aperture(0, 128, 120.0, 0.1, 200).innerCutoff());
        assertEquals(96.0f, NetherRoofPolicy.aperture(0, 128, 50.0, 0.1, 200).radius());
        assertEquals(0.0f, NetherRoofPolicy.aperture(0, 4, -40.0, 0.1, 200).innerCutoff());
    }

    @Test
    void invalidViewInputsStillProduceFiniteSafeShaderUniforms() {
        NetherRoofPolicy.Aperture aperture = NetherRoofPolicy.aperture(
                0, 128, Double.NaN, Double.POSITIVE_INFINITY, -1);

        assertEquals(94.0f, aperture.innerCutoff());
        assertEquals(96.0f, aperture.radius());
    }
}
