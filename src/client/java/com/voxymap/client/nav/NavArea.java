package com.voxymap.client.nav;

public record NavArea(int level, int originX, int originZ, int width, int depth) {
    static final int MAX_CELLS = 1_600_000;
    static final int MAX_LEVEL = 4;
    static final double MIN_MARGIN = 96.0;
    static final double MARGIN_SHARE = 0.3;
    static final double FINE_DISTANCE = 400.0;
    static final double MEDIUM_DISTANCE = 1500.0;

    public int cell() {
        return 1 << level;
    }

    public long cells() {
        return (long) width * depth;
    }

    public static NavArea around(double startX, double startZ, double goalX, double goalZ) {
        double distance = Math.hypot(goalX - startX, goalZ - startZ);
        double margin = Math.max(MIN_MARGIN, distance * MARGIN_SHARE);
        double minX = Math.min(startX, goalX) - margin;
        double maxX = Math.max(startX, goalX) + margin;
        double minZ = Math.min(startZ, goalZ) - margin;
        double maxZ = Math.max(startZ, goalZ) + margin;
        int level = distance < FINE_DISTANCE ? 0 : distance < MEDIUM_DISTANCE ? 1 : 2;
        for (; level <= MAX_LEVEL; level++) {
            NavArea area = of(level, minX, minZ, maxX, maxZ);
            if (area.cells() <= MAX_CELLS) {
                return area;
            }
        }
        return null;
    }

    private static NavArea of(int level, double minX, double minZ, double maxX, double maxZ) {
        int cell = 1 << level;
        int originX = Math.floorDiv((int) Math.floor(minX), cell) * cell;
        int originZ = Math.floorDiv((int) Math.floor(minZ), cell) * cell;
        int width = (int) Math.ceil((maxX - originX) / cell) + 1;
        int depth = (int) Math.ceil((maxZ - originZ) / cell) + 1;
        return new NavArea(level, originX, originZ, width, depth);
    }
}
