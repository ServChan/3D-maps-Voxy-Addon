package com.voxymap.client.map;

import java.util.ArrayList;
import java.util.List;

/**
 * Re-implements HappyGhastScanner's expanding square-spiral waypoint formula so its scan route can be
 * drawn on the map without a compile-time dependency on that mod. The live center/step/segment come
 * from {@link com.voxymap.client.integration.HappyGhastScannerBridge}; this class only knows the pure
 * shape of the spiral.
 */
public final class SpiralRouteGeometry {
    private SpiralRouteGeometry() {
    }

    public record Waypoint(double x, double z, int lap) {
    }

    /** The corner the spiral reaches at the end of {@code segment} (0-based; 4 segments per lap). */
    public static Waypoint waypointAt(double centerX, double centerZ, double step, int segment) {
        int lap = (segment / 4) + 1;
        int side = segment % 4;
        double x;
        double z;
        switch (side) {
            case 0 -> { // East
                x = centerX + lap * step;
                z = centerZ - (lap - 1) * step;
            }
            case 1 -> { // South
                x = centerX + lap * step;
                z = centerZ + lap * step;
            }
            case 2 -> { // West
                x = centerX - lap * step;
                z = centerZ + lap * step;
            }
            default -> { // North
                x = centerX - lap * step;
                z = centerZ - lap * step;
            }
        }
        return new Waypoint(x, z, lap);
    }

    /**
     * The spiral's corner points across segments {@code [firstSegment, lastSegment]} (inclusive),
     * prefixed by the point one segment earlier (or the center itself, when the window starts at
     * segment 0) so the first leg has a starting point to draw a line from.
     */
    public static List<Waypoint> pathSegment(double centerX, double centerZ, double step,
                                              int firstSegment, int lastSegment) {
        int first = Math.max(0, firstSegment);
        int last = Math.max(first, lastSegment);
        List<Waypoint> points = new ArrayList<>(last - first + 2);
        points.add(first == 0 ? new Waypoint(centerX, centerZ, 0) : waypointAt(centerX, centerZ, step, first - 1));
        for (int segment = first; segment <= last; segment++) {
            points.add(waypointAt(centerX, centerZ, step, segment));
        }
        return points;
    }
}
