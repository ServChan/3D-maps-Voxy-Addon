package com.voxymap.client.nav;

import java.util.List;

public record Route(List<Point> points, double seconds, double length, boolean direct, int rocketsNeeded, int cruiseY) {
    public record Point(double x, double y, double z, TravelMode mode, boolean unknown) {
    }

    public Route {
        points = List.copyOf(points);
    }

    public boolean flight() {
        return !points.isEmpty() && points.getFirst().mode() == TravelMode.ELYTRA;
    }

    public static Route direct(double startX, double startY, double startZ, double goalX, double goalY, double goalZ) {
        double length = Math.hypot(goalX - startX, goalZ - startZ);
        return new Route(List.of(new Point(startX, startY, startZ, TravelMode.WALK, true),
                new Point(goalX, goalY, goalZ, TravelMode.WALK, true)),
                length * TravelMode.WALK.secondsPerBlock(), length, true, 0, 0);
    }
}
