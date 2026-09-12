package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerViewConeGeometryTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void minecraftYawZeroLooksSouth() {
        assertEquals(0.0, PlayerViewConeGeometry.directionX(0.0), EPSILON);
        assertEquals(1.0, PlayerViewConeGeometry.directionZ(0.0), EPSILON);
    }

    @Test
    void minecraftYawNinetyLooksWest() {
        assertEquals(-1.0, PlayerViewConeGeometry.directionX(90.0), EPSILON);
        assertEquals(0.0, PlayerViewConeGeometry.directionZ(90.0), EPSILON);
    }

    @Test
    void coneEdgesRemainSymmetricAroundTheLookDirection() {
        double leftX = PlayerViewConeGeometry.directionX(-32.0);
        double rightX = PlayerViewConeGeometry.directionX(32.0);
        assertEquals(leftX, -rightX, EPSILON);
        assertEquals(PlayerViewConeGeometry.directionZ(-32.0),
                PlayerViewConeGeometry.directionZ(32.0), EPSILON);
    }
}
