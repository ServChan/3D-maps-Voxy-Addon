package com.voxymap.client.nav;

import java.util.List;

public final class RouteProgress {
    static final int SEARCH_BACK = 2;
    static final int SEARCH_AHEAD = 64;
    static final double FULL_SEARCH_BLOCKS = 48.0;

    public record Transition(TravelMode to, boolean unknown, double distance) {
    }

    public record State(int segment, double along, double offRoute, double remaining, double remainingSeconds,
                        Transition next) {
    }

    private RouteProgress() {
    }

    public static State locate(Route route, double x, double z, int hint) {
        List<Route.Point> points = route.points();
        int segments = points.size() - 1;
        if (segments < 1) {
            return new State(0, 0.0, 0.0, 0.0, 0.0, null);
        }
        int from = Math.max(0, Math.min(segments - 1, hint) - SEARCH_BACK);
        int to = Math.min(segments - 1, Math.max(0, hint) + SEARCH_AHEAD);
        double[] best = nearest(points, x, z, from, to);
        if (best[1] > FULL_SEARCH_BLOCKS && (from > 0 || to < segments - 1)) {
            best = nearest(points, x, z, 0, segments - 1);
        }
        int segment = (int) best[0];
        double along = best[2];
        double segmentLength = length(points.get(segment), points.get(segment + 1));
        double remaining = Math.max(0.0, segmentLength - along);
        double modeSeconds = remaining * points.get(segment).mode().secondsPerBlock();
        for (int i = segment + 1; i < segments; i++) {
            double part = length(points.get(i), points.get(i + 1));
            remaining += part;
            modeSeconds += part * points.get(i).mode().secondsPerBlock();
        }
        double scale = calibration(route);
        Transition next = null;
        Route.Point current = points.get(segment);
        double ahead = Math.max(0.0, segmentLength - along);
        for (int i = segment + 1; i < segments; i++) {
            Route.Point point = points.get(i);
            if (point.mode() != current.mode() || point.unknown() != current.unknown()) {
                next = new Transition(point.mode(), point.unknown(), ahead);
                break;
            }
            ahead += length(point, points.get(i + 1));
        }
        return new State(segment, along, best[1], remaining, modeSeconds * scale, next);
    }

    public static double[] pointAhead(Route route, int segment, double along, double distance) {
        List<Route.Point> points = route.points();
        double left = along + distance;
        for (int i = Math.max(0, segment); i < points.size() - 1; i++) {
            Route.Point a = points.get(i);
            Route.Point b = points.get(i + 1);
            double part = length(a, b);
            if (left <= part || i == points.size() - 2) {
                double t = part <= 0.0 ? 1.0 : Math.min(1.0, left / part);
                return new double[]{a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t, a.z() + (b.z() - a.z()) * t};
            }
            left -= part;
        }
        Route.Point last = points.getLast();
        return new double[]{last.x(), last.y(), last.z()};
    }

    static double calibration(Route route) {
        double modeSeconds = 0.0;
        List<Route.Point> points = route.points();
        for (int i = 0; i < points.size() - 1; i++) {
            modeSeconds += length(points.get(i), points.get(i + 1)) * points.get(i).mode().secondsPerBlock();
        }
        return modeSeconds <= 0.0 || route.seconds() <= 0.0 ? 1.0 : route.seconds() / modeSeconds;
    }

    private static double[] nearest(List<Route.Point> points, double x, double z, int from, int to) {
        double bestDistance = Double.POSITIVE_INFINITY;
        int bestSegment = from;
        double bestAlong = 0.0;
        for (int i = from; i <= to; i++) {
            Route.Point a = points.get(i);
            Route.Point b = points.get(i + 1);
            double dx = b.x() - a.x();
            double dz = b.z() - a.z();
            double lengthSquared = dx * dx + dz * dz;
            double t = lengthSquared <= 0.0 ? 0.0
                    : Math.max(0.0, Math.min(1.0, ((x - a.x()) * dx + (z - a.z()) * dz) / lengthSquared));
            double distance = Math.hypot(x - (a.x() + t * dx), z - (a.z() + t * dz));
            if (distance < bestDistance - 1.0e-9) {
                bestDistance = distance;
                bestSegment = i;
                bestAlong = t * Math.sqrt(lengthSquared);
            }
        }
        return new double[]{bestSegment, bestDistance, bestAlong};
    }

    static double length(Route.Point a, Route.Point b) {
        return Math.hypot(b.x() - a.x(), b.z() - a.z());
    }
}
