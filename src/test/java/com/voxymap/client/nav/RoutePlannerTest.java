package com.voxymap.client.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutePlannerTest {
    private static NavGrid flat(int width, int depth, int cell) {
        NavGrid grid = NavGrid.empty(0, 0, cell, width, depth);
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                grid.set(x, z, NavKind.LAND, 64);
            }
        }
        return grid;
    }

    private static Route plan(NavGrid grid, Capabilities caps, double sx, double sz, double gx, double gz) {
        return RoutePlanner.plan(grid, caps, sx, 64, sz, gx, Double.NaN, gz, () -> false);
    }

    private static boolean passesNear(Route route, double x, double z, double radius) {
        for (int i = 0; i < route.points().size() - 1; i++) {
            Route.Point a = route.points().get(i);
            Route.Point b = route.points().get(i + 1);
            if (RoutePlanner.segmentDistance(x, z, a.x(), a.z(), b.x(), b.z()) <= radius) {
                return true;
            }
        }
        return false;
    }

    @Test
    void openGroundGivesOneStraightWalkingLeg() {
        Route route = plan(flat(40, 40, 1), Capabilities.ON_FOOT, 2.5, 20.5, 37.5, 20.5);
        assertNotNull(route);
        assertEquals(2, route.points().size());
        assertEquals(TravelMode.WALK, route.points().getFirst().mode());
        assertEquals(35.0, route.length(), 1.0);
        assertEquals(35.0 * RoutePlanner.WALK_LAND, route.seconds(), 0.5);
    }

    @Test
    void wallIsPassedThroughItsGap() {
        NavGrid grid = flat(40, 40, 1);
        for (int z = 0; z < 40; z++) {
            if (z != 35) {
                grid.set(20, z, NavKind.BLOCKED, 64);
            }
        }
        Route route = plan(grid, Capabilities.ON_FOOT, 5.5, 5.5, 35.5, 5.5);
        assertNotNull(route);
        assertTrue(passesNear(route, 20.5, 35.5, 1.0));
    }

    @Test
    void cliffHigherThanOneStepIsWalkedAround() {
        NavGrid grid = flat(40, 40, 1);
        for (int z = 0; z < 40; z++) {
            for (int x = 20; x < 40; x++) {
                grid.set(x, z, NavKind.LAND, 70);
            }
        }
        for (int x = 14; x < 20; x++) {
            grid.set(x, 35, NavKind.LAND, 65 + (x - 14));
        }
        Route route = RoutePlanner.plan(grid, Capabilities.ON_FOOT, 5.5, 64, 5.5, 30.5, 70, 5.5, () -> false);
        assertNotNull(route);
        assertTrue(passesNear(route, 17.5, 35.5, 1.5));
    }

    @Test
    void boatCrossesALakeThatWalkersGoAround() {
        NavGrid grid = flat(60, 60, 1);
        for (int z = 0; z < 55; z++) {
            for (int x = 10; x < 50; x++) {
                grid.set(x, z, NavKind.WATER, 63);
            }
        }
        Route walking = plan(grid, Capabilities.ON_FOOT, 5.5, 5.5, 54.5, 5.5);
        Route boating = plan(grid, new Capabilities(true, false, false, 0), 5.5, 5.5, 54.5, 5.5);
        assertNotNull(walking);
        assertNotNull(boating);
        assertTrue(boating.points().stream().anyMatch(point -> point.mode() == TravelMode.BOAT));
        assertTrue(boating.seconds() < walking.seconds());
        assertFalse(walking.points().stream().anyMatch(point -> point.mode() == TravelMode.BOAT));
    }

    @Test
    void unexploredCellsAreMarkedUnknown() {
        NavGrid grid = flat(30, 10, 1);
        for (int z = 0; z < 10; z++) {
            for (int x = 10; x < 20; x++) {
                grid.set(x, z, NavKind.UNKNOWN, 0);
            }
        }
        Route route = plan(grid, Capabilities.ON_FOOT, 2.5, 5.5, 27.5, 5.5);
        assertNotNull(route);
        assertTrue(route.points().stream().anyMatch(Route.Point::unknown));
        assertTrue(route.points().stream().anyMatch(point -> !point.unknown()));
    }

    @Test
    void enclosedGoalHasNoRoute() {
        NavGrid grid = flat(30, 30, 1);
        for (int i = 10; i <= 20; i++) {
            grid.set(i, 10, NavKind.BLOCKED, 64);
            grid.set(i, 20, NavKind.BLOCKED, 64);
            grid.set(10, i, NavKind.BLOCKED, 64);
            grid.set(20, i, NavKind.BLOCKED, 64);
        }
        assertNull(plan(grid, Capabilities.ON_FOOT, 2.5, 2.5, 15.5, 15.5));
    }

    @Test
    void stepLimitsGrowWithCellSize() {
        assertEquals(1, RoutePlanner.maxRise(1));
        assertEquals(4, RoutePlanner.maxRise(4));
        assertEquals(3, RoutePlanner.maxDrop(1));
        assertEquals(8, RoutePlanner.maxDrop(4));
    }

    @Test
    void cancelledSearchStops() {
        assertNull(RoutePlanner.plan(flat(400, 400, 1), Capabilities.ON_FOOT, 2.5, 64, 2.5, 397.5, Double.NaN, 397.5,
                () -> true));
    }
}
