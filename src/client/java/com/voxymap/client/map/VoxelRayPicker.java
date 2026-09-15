package com.voxymap.client.map;

import net.minecraft.core.Direction;

/** Deterministic grid traversal used to pick the first occupied voxel under the map cursor. */
final class VoxelRayPicker {
    // A diagonal ray crosses up to three voxel boundaries per block of travelled distance.
    private static final int MAX_STEPS = 131_072;

    private VoxelRayPicker() {
    }

    @FunctionalInterface
    interface Occupancy {
        boolean isOccupied(int x, int y, int z);
    }

    record Hit(double x, double y, double z, int blockX, int blockY, int blockZ, Direction face) {
    }

    static Hit pick(double originX, double originY, double originZ,
                    double directionX, double directionY, double directionZ,
                    double maxDistance, Occupancy occupancy) {
        double length = Math.sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ);
        if (!(length > 0.0) || !Double.isFinite(length) || !(maxDistance > 0.0)) {
            return null;
        }
        double dx = directionX / length;
        double dy = directionY / length;
        double dz = directionZ / length;
        int x = floor(originX);
        int y = floor(originY);
        int z = floor(originZ);
        int stepX = sign(dx);
        int stepY = sign(dy);
        int stepZ = sign(dz);
        double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double deltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        double nextX = firstBoundary(originX, x, dx, stepX);
        double nextY = firstBoundary(originY, y, dy, stepY);
        double nextZ = firstBoundary(originZ, z, dz, stepZ);
        double distance = 0.0;
        Direction enteredThrough = null;

        for (int steps = 0; steps < MAX_STEPS && distance <= maxDistance; steps++) {
            if (occupancy.isOccupied(x, y, z)) {
                return new Hit(originX + dx * distance, originY + dy * distance, originZ + dz * distance,
                        x, y, z, enteredThrough);
            }
            if (nextX <= nextY && nextX <= nextZ) {
                distance = nextX;
                nextX += deltaX;
                x += stepX;
                enteredThrough = stepX > 0 ? Direction.WEST : Direction.EAST;
            } else if (nextY <= nextZ) {
                distance = nextY;
                nextY += deltaY;
                y += stepY;
                enteredThrough = stepY > 0 ? Direction.DOWN : Direction.UP;
            } else {
                distance = nextZ;
                nextZ += deltaZ;
                z += stepZ;
                enteredThrough = stepZ > 0 ? Direction.NORTH : Direction.SOUTH;
            }
        }
        return null;
    }

    private static double firstBoundary(double origin, int cell, double direction, int step) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        double boundary = step > 0 ? cell + 1.0 : cell;
        return (boundary - origin) / direction;
    }

    private static int floor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static int sign(double value) {
        return value > 0.0 ? 1 : value < 0.0 ? -1 : 0;
    }
}
