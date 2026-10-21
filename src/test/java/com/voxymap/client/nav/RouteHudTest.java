package com.voxymap.client.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteHudTest {
    @Test
    void arrowAngleIsRelativeToWhereThePlayerLooks() {
        assertEquals(0.0, RouteHud.screenAngle(0, 10, 0.0f), 1.0e-9);
        assertEquals(Math.PI / 2, RouteHud.screenAngle(-10, 0, 0.0f), 1.0e-9);
        assertEquals(-Math.PI / 2, RouteHud.screenAngle(10, 0, 0.0f), 1.0e-9);
        assertEquals(0.0, RouteHud.screenAngle(-10, 0, 90.0f), 1.0e-9);
    }

    @Test
    void distancesAreRoundedForReading() {
        assertEquals(42L, RouteText.roundedMeters(42.4));
        assertEquals(350L, RouteText.roundedMeters(347.0));
    }
}
