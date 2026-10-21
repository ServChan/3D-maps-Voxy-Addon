package com.voxymap.client.nav;

import java.util.List;

public final class ElytraPlanner {
    static final double MIN_DISTANCE = 150.0;
    static final double BLOCKS_PER_ROCKET = 60.0;
    static final double CLIMB_PER_ROCKET = 40.0;
    static final int CLEARANCE = 32;
    static final double CRUISE_SPEED = 30.0;
    static final double CLIMB_SPEED = 15.0;
    static final double RAMP_BLOCKS = 96.0;

    private ElytraPlanner() {
    }

    public static Route plan(NavGrid grid, double startX, double startY, double startZ,
                             double goalX, double goalY, double goalZ, int buildLimitY) {
        double distance = Math.hypot(goalX - startX, goalZ - startZ);
        if (distance < MIN_DISTANCE) {
            return null;
        }
        int highest = (int) Math.ceil(Math.max(startY, Double.isNaN(goalY) ? startY : goalY));
        int samples = Math.max(2, (int) Math.ceil(distance / Math.max(1, grid.cell())));
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            int cellX = grid.cellX(startX + (goalX - startX) * t);
            int cellZ = grid.cellZ(startZ + (goalZ - startZ) * t);
            if (!grid.contains(cellX, cellZ)) {
                continue;
            }
            int index = grid.index(cellX, cellZ);
            if (grid.kind(index) != NavKind.UNKNOWN) {
                highest = Math.max(highest, grid.height(index));
            }
        }
        int cruise = Math.min(buildLimitY - 4, highest + CLEARANCE);
        double climb = Math.max(0.0, cruise - startY);
        int rockets = (int) Math.ceil(distance / BLOCKS_PER_ROCKET + climb / CLIMB_PER_ROCKET);
        double seconds = distance / CRUISE_SPEED + climb / CLIMB_SPEED;
        double rampIn = Math.min(0.2, RAMP_BLOCKS / distance);
        double rampOut = Math.max(0.8, 1.0 - RAMP_BLOCKS / distance);
        double endY = Double.isNaN(goalY) ? cruise : goalY;
        List<Route.Point> points = List.of(
                new Route.Point(startX, startY, startZ, TravelMode.ELYTRA, false),
                new Route.Point(lerp(startX, goalX, rampIn), cruise, lerp(startZ, goalZ, rampIn), TravelMode.ELYTRA, false),
                new Route.Point(lerp(startX, goalX, rampOut), cruise, lerp(startZ, goalZ, rampOut), TravelMode.ELYTRA, false),
                new Route.Point(goalX, endY, goalZ, TravelMode.ELYTRA, false));
        return new Route(points, seconds, distance, false, rockets, cruise);
    }

    public static boolean prefer(Route flight, Route ground, Capabilities caps) {
        if (flight == null || !caps.canFly()) {
            return false;
        }
        return ground == null || ground.direct() || flight.seconds() < ground.seconds();
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
