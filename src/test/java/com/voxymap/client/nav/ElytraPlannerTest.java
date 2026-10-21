package com.voxymap.client.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElytraPlannerTest {
    private static NavGrid hills() {
        NavGrid grid = NavGrid.empty(0, 0, 4, 300, 10);
        for (int x = 0; x < 300; x++) {
            for (int z = 0; z < 10; z++) {
                grid.set(x, z, NavKind.LAND, x == 150 ? 180 : 70);
            }
        }
        return grid;
    }

    @Test
    void flightClearsTheHighestKnownTerrain() {
        Route flight = ElytraPlanner.plan(hills(), 2, 70, 20, 1100, Double.NaN, 20, 320);
        assertNotNull(flight);
        assertTrue(flight.flight());
        assertEquals(180 + ElytraPlanner.CLEARANCE, flight.cruiseY());
        assertTrue(flight.rocketsNeeded() >= 1100 / 60);
        assertEquals(4, flight.points().size());
    }

    @Test
    void shortHopsAreNotFlown() {
        assertNull(ElytraPlanner.plan(hills(), 2, 70, 20, 100, 70, 20, 320));
    }

    @Test
    void cruiseStaysBelowTheBuildLimit() {
        Route flight = ElytraPlanner.plan(hills(), 2, 300, 20, 1100, Double.NaN, 20, 320);
        assertNotNull(flight);
        assertEquals(316, flight.cruiseY());
    }

    @Test
    void flightIsPreferredOnlyWithRocketsAndWhenFaster() {
        Route flight = ElytraPlanner.plan(hills(), 2, 70, 20, 1100, Double.NaN, 20, 320);
        Route ground = Route.direct(2, 70, 20, 1100, 70, 20);
        Route slowGround = new Route(ground.points(), 500.0, 1100.0, false, 0, 0);
        assertTrue(ElytraPlanner.prefer(flight, slowGround, new Capabilities(false, true, true, 64)));
        assertFalse(ElytraPlanner.prefer(flight, slowGround, new Capabilities(false, true, true, 0)));
        assertFalse(ElytraPlanner.prefer(flight, slowGround, Capabilities.ON_FOOT));
        Route quickGround = new Route(ground.points(), 1.0, 1100.0, false, 0, 0);
        assertFalse(ElytraPlanner.prefer(flight, quickGround, new Capabilities(false, true, true, 64)));
        assertTrue(ElytraPlanner.prefer(flight, ground, new Capabilities(false, true, false, 3)));
    }
}
