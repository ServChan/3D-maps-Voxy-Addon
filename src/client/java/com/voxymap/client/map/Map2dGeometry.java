package com.voxymap.client.map;

public final class Map2dGeometry {
    public static final int TILE_PIXELS = 256;
    public static final int MAX_LEVEL = 6;
    public static final int VOXY_MAX_LEVEL = 4;
    public static final double MAX_BLOCKS_PER_PIXEL = 64.0;
    static final int SECTION_VOXELS = 32;
    static final int SECTIONS_PER_TILE = TILE_PIXELS / SECTION_VOXELS;
    public static final int SURFACE = Integer.MIN_VALUE;

    private Map2dGeometry() {
    }

    public static int levelFor(double blocksPerPixel) {
        if (!(blocksPerPixel > 1.0)) {
            return 0;
        }
        int level = (int) Math.floor(Math.log(blocksPerPixel) / Math.log(2.0) + 1.0e-9);
        return Math.max(0, Math.min(MAX_LEVEL, level));
    }

    public static int tileBlocks(int level) {
        return TILE_PIXELS << level;
    }

    public static int tileCoordinate(double block, int level) {
        return Math.floorDiv((int) Math.floor(block), tileBlocks(level));
    }

    static int sectionBlocks(int level) {
        return SECTION_VOXELS << level;
    }

    static int voxelBlocks(int level) {
        return 1 << level;
    }

    static int lowestSection(int minY, int level) {
        return Math.floorDiv(minY, sectionBlocks(level));
    }

    static int highestSection(int maxYExclusive, int level) {
        return Math.floorDiv(maxYExclusive - 1, sectionBlocks(level));
    }

    static int caveSolidVoxels(int level) {
        return Math.max(1, MinimapTileCache.CAVE_SOLID_SKIP_BLOCKS >> level);
    }

    public static int caveSlice(double viewY) {
        return Math.floorDiv((int) Math.floor(viewY), 16) * 16 + 8;
    }

    static double slopeFactor(int height, int northHeight, int westHeight, int level) {
        double rise = ((height - northHeight) + (height - westHeight)) / (double) voxelBlocks(level);
        double factor = 1.0 + rise * MapColorMath.SLOPE_STEP;
        return Math.max(MapColorMath.MIN_SLOPE_FACTOR, Math.min(MapColorMath.MAX_SLOPE_FACTOR, factor));
    }

    static int average(int a, int b, int c, int d) {
        int count = 0;
        int red = 0;
        int green = 0;
        int blue = 0;
        for (int pixel : new int[]{a, b, c, d}) {
            if ((pixel >>> 24) == 0) {
                continue;
            }
            count++;
            red += (pixel >>> 16) & 0xFF;
            green += (pixel >>> 8) & 0xFF;
            blue += pixel & 0xFF;
        }
        if (count == 0) {
            return 0;
        }
        return 0xFF000000 | (red / count) << 16 | (green / count) << 8 | blue / count;
    }

    static int[] downsample(int[] northWest, int[] northEast, int[] southWest, int[] southEast) {
        int[] result = new int[TILE_PIXELS * TILE_PIXELS];
        int half = TILE_PIXELS / 2;
        int[][] quarters = {northWest, northEast, southWest, southEast};
        for (int quarter = 0; quarter < 4; quarter++) {
            int[] source = quarters[quarter];
            if (source == null) {
                continue;
            }
            int offsetX = (quarter & 1) * half;
            int offsetY = (quarter >> 1) * half;
            for (int y = 0; y < half; y++) {
                for (int x = 0; x < half; x++) {
                    int sx = x * 2;
                    int sy = y * 2;
                    result[(offsetY + y) * TILE_PIXELS + offsetX + x] = average(
                            source[sy * TILE_PIXELS + sx], source[sy * TILE_PIXELS + sx + 1],
                            source[(sy + 1) * TILE_PIXELS + sx], source[(sy + 1) * TILE_PIXELS + sx + 1]);
                }
            }
        }
        return result;
    }

    static boolean empty(int[] pixels) {
        for (int pixel : pixels) {
            if ((pixel >>> 24) != 0) {
                return false;
            }
        }
        return true;
    }

    public static double screenX(double worldX, double centerX, double blocksPerPixel, int width) {
        return width / 2.0 + (worldX - centerX) / blocksPerPixel;
    }

    public static double screenY(double worldZ, double centerZ, double blocksPerPixel, int height) {
        return height / 2.0 + (worldZ - centerZ) / blocksPerPixel;
    }

    public static double worldX(double screenX, double centerX, double blocksPerPixel, int width) {
        return centerX + (screenX - width / 2.0) * blocksPerPixel;
    }

    public static double worldZ(double screenY, double centerZ, double blocksPerPixel, int height) {
        return centerZ + (screenY - height / 2.0) * blocksPerPixel;
    }
}
