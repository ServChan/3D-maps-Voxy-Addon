package com.voxymap.client.map;

/** Horizontal selection for a terrain share. Vertical sections are intentionally left unrestricted:
 * selecting a rectangle on the map sends the complete explored column between its two corners. */
public record MapShareBounds(int minSectionX, int maxSectionX, int minSectionZ, int maxSectionZ) {
    private static final int SECTION_SIZE = 32;

    public MapShareBounds {
        if (minSectionX > maxSectionX || minSectionZ > maxSectionZ) {
            throw new IllegalArgumentException("Map share bounds must be normalized");
        }
    }

    public static MapShareBounds fromBlockCoordinates(double x1, double z1, double x2, double z2) {
        int sx1 = Math.floorDiv((int) Math.floor(x1), SECTION_SIZE);
        int sz1 = Math.floorDiv((int) Math.floor(z1), SECTION_SIZE);
        int sx2 = Math.floorDiv((int) Math.floor(x2), SECTION_SIZE);
        int sz2 = Math.floorDiv((int) Math.floor(z2), SECTION_SIZE);
        return new MapShareBounds(Math.min(sx1, sx2), Math.max(sx1, sx2),
                Math.min(sz1, sz2), Math.max(sz1, sz2));
    }

    public boolean containsSection(int sectionX, int sectionZ) {
        return sectionX >= minSectionX && sectionX <= maxSectionX
                && sectionZ >= minSectionZ && sectionZ <= maxSectionZ;
    }

    public int minBlockX() {
        return minSectionX * SECTION_SIZE;
    }

    public int maxBlockXExclusive() {
        return (maxSectionX + 1) * SECTION_SIZE;
    }

    public int minBlockZ() {
        return minSectionZ * SECTION_SIZE;
    }

    public int maxBlockZExclusive() {
        return (maxSectionZ + 1) * SECTION_SIZE;
    }

    public int widthBlocks() {
        return maxBlockXExclusive() - minBlockX();
    }

    public int depthBlocks() {
        return maxBlockZExclusive() - minBlockZ();
    }
}
