package com.voxymap.client.gui;

final class MapProjection {
    private static final double NEAR_PLANE = 0.1;
    private static final double MAX_PROJECTED_COORDINATE = 1.0E6;

    private MapProjection() {
    }

    static Point project(double worldX, double worldY, double worldZ,
                         double cameraX, double cameraY, double cameraZ,
                         double forwardX, double forwardY, double forwardZ,
                         double rightX, double rightZ,
                         double upX, double upY, double upZ,
                         double tanX, double tanY,
                         int viewportWidth, int viewportHeight) {
        double[] exact = projectExact(worldX, worldY, worldZ, cameraX, cameraY, cameraZ, forwardX, forwardY, forwardZ,
                rightX, rightZ, upX, upY, upZ, tanX, tanY, viewportWidth, viewportHeight);
        if (exact == null) return null;
        double screenX = clamp(exact[0], -MAX_PROJECTED_COORDINATE, MAX_PROJECTED_COORDINATE);
        double screenY = clamp(exact[1], -MAX_PROJECTED_COORDINATE, MAX_PROJECTED_COORDINATE);
        return new Point((int) Math.round(screenX), (int) Math.round(screenY));
    }

    static double[] projectExact(double worldX, double worldY, double worldZ,
                                 double cameraX, double cameraY, double cameraZ,
                                 double forwardX, double forwardY, double forwardZ,
                                 double rightX, double rightZ,
                                 double upX, double upY, double upZ,
                                 double tanX, double tanY,
                                 int viewportWidth, int viewportHeight) {
        double relativeX = worldX - cameraX;
        double relativeY = worldY - cameraY;
        double relativeZ = worldZ - cameraZ;
        double depth = relativeX * forwardX + relativeY * forwardY + relativeZ * forwardZ;
        if (depth <= NEAR_PLANE || !Double.isFinite(depth)) return null;

        double horizontal = relativeX * rightX + relativeZ * rightZ;
        double vertical = relativeX * upX + relativeY * upY + relativeZ * upZ;
        double screenX = viewportWidth * 0.5 * (1.0 - horizontal / (depth * tanX));
        double screenY = viewportHeight * 0.5 * (1.0 - vertical / (depth * tanY));
        if (!Double.isFinite(screenX) || !Double.isFinite(screenY)) return null;
        return new double[]{screenX, screenY};
    }

    static double[] clipLine(double x1, double y1, double x2, double y2,
                             int viewportWidth, int viewportHeight) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double t0 = 0.0;
        double t1 = 1.0;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x1 + 2.0, viewportWidth + 2.0 - x1, y1 + 2.0, viewportHeight + 2.0 - y1};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0.0) {
                if (q[i] < 0.0) return null;
                continue;
            }
            double ratio = q[i] / p[i];
            if (p[i] < 0.0) {
                if (ratio > t1) return null;
                t0 = Math.max(t0, ratio);
            } else {
                if (ratio < t0) return null;
                t1 = Math.min(t1, ratio);
            }
        }
        double clippedX1 = x1 + t0 * dx;
        double clippedY1 = y1 + t0 * dy;
        double clippedX2 = x1 + t1 * dx;
        double clippedY2 = y1 + t1 * dy;
        double maxSpan = 2.0 * Math.hypot(viewportWidth, viewportHeight) + 64.0;
        if (Math.abs(clippedX2 - clippedX1) > maxSpan || Math.abs(clippedY2 - clippedY1) > maxSpan) {
            return null;
        }
        return new double[]{clippedX1, clippedY1, clippedX2, clippedY2};
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    record Point(int x, int y) {
    }
}
