package com.voxymap.client.nav;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RouteProgressTest {
    private static final Route LAKE = new Route(List.of(
            new Route.Point(0, 64, 0, TravelMode.WALK, false),
            new Route.Point(100, 64, 0, TravelMode.BOAT, false),
            new Route.Point(100, 64, 50, TravelMode.WALK, false),
            new Route.Point(150, 64, 50, TravelMode.WALK, false)),
            100 * 0.2 + 50 * 0.125 + 50 * 0.2, 200, false, 0, 0);

    @Test
    void locatesThePlayerOnTheNearestLeg() {
        RouteProgress.State state = RouteProgress.locate(LAKE, 40, 3, 0);
        assertEquals(0, state.segment());
        assertEquals(40.0, state.along(), 1.0e-9);
        assertEquals(3.0, state.offRoute(), 1.0e-9);
        assertEquals(160.0, state.remaining(), 1.0e-9);
    }

    @Test
    void announcesTheNextChangeOfTravel() {
        RouteProgress.State state = RouteProgress.locate(LAKE, 40, 0, 0);
        assertNotNull(state.next());
        assertEquals(TravelMode.BOAT, state.next().to());
        assertEquals(60.0, state.next().distance(), 1.0e-9);
        RouteProgress.State onWater = RouteProgress.locate(LAKE, 100, 20, 0);
        assertEquals(1, onWater.segment());
        assertEquals(TravelMode.WALK, onWater.next().to());
        assertEquals(30.0, onWater.next().distance(), 1.0e-9);
        RouteProgress.State last = RouteProgress.locate(LAKE, 120, 50, 1);
        assertNull(last.next());
    }

    @Test
    void remainingTimeFollowsTheRouteEstimate() {
        RouteProgress.State start = RouteProgress.locate(LAKE, 0, 0, 0);
        assertEquals(LAKE.seconds(), start.remainingSeconds(), 1.0e-6);
    }

    @Test
    void pointAheadWalksAcrossCorners() {
        assertArrayEquals(new double[]{100, 64, 10}, RouteProgress.pointAhead(LAKE, 0, 90, 20), 1.0e-9);
        assertArrayEquals(new double[]{150, 64, 50}, RouteProgress.pointAhead(LAKE, 2, 40, 500), 1.0e-9);
    }

    @Test
    void farAwayPlayerIsFoundOnAnyLeg() {
        RouteProgress.State state = RouteProgress.locate(LAKE, 150, 120, 0);
        assertEquals(2, state.segment());
    }
}
