package com.voxymap.client.map;

public final class WebMapGeometry {
    public static final int TILE_PIXELS = 512;
    public static final int MAX_ZOOM_LIMIT = 8;
    private static final double MAX_SCREEN_COORDINATE = 1.0E6;
    private static final int WORLD_LIMIT = 30_000_000;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private WebMapGeometry() {
    }

    public static int zoomFor(double blocksPerScreenPixel, int maxZoom) {
        int top = clamp(maxZoom, 0, MAX_ZOOM_LIMIT);
        if (!(blocksPerScreenPixel > 0.0) || !Double.isFinite(blocksPerScreenPixel)) {
            return top;
        }
        long coarser = Math.round(Math.log(blocksPerScreenPixel) / Math.log(2.0));
        return (int) Math.max(0L, top - Math.max(0L, coarser));
    }

    public static int tileBlocks(int zoom, int maxZoom) {
        int top = clamp(maxZoom, 0, MAX_ZOOM_LIMIT);
        return TILE_PIXELS << (top - clamp(zoom, 0, top));
    }

    public static int tileIndex(double block, int tileBlocks) {
        double clamped = Math.max(-WORLD_LIMIT, Math.min(WORLD_LIMIT, Double.isFinite(block) ? block : 0.0));
        return Math.floorDiv((int) Math.floor(clamped), tileBlocks);
    }

    public static int screenCoordinate(double value) {
        if (!Double.isFinite(value)) {
            return 0;
        }
        return (int) Math.round(Math.max(-MAX_SCREEN_COORDINATE, Math.min(MAX_SCREEN_COORDINATE, value)));
    }

    public static String dimensionOf(String worldName) {
        if (worldName == null) {
            return null;
        }
        int separator = worldName.indexOf('_');
        return separator <= 0 ? worldName : worldName.substring(0, separator) + ":" + worldName.substring(separator + 1);
    }

    public static String worldNameOf(String dimensionId) {
        return dimensionId == null ? null : dimensionId.replace(':', '_');
    }

    public static boolean isNether(String dimensionId) {
        return "minecraft:the_nether".equals(dimensionId);
    }

    public static boolean isEnd(String dimensionId) {
        return "minecraft:the_end".equals(dimensionId);
    }

    public static double scaleBetween(String fromDimension, String toDimension) {
        if (isEnd(fromDimension) || isEnd(toDimension) || isNether(fromDimension) == isNether(toDimension)) {
            return 1.0;
        }
        return isNether(fromDimension) ? 8.0 : 0.125;
    }

    public static boolean isPng(byte[] data) {
        if (data == null || data.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (data[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    public static double[] clipLine(double x1, double y1, double x2, double y2,
                                    double left, double top, double right, double bottom) {
        if (!Double.isFinite(x1) || !Double.isFinite(y1) || !Double.isFinite(x2) || !Double.isFinite(y2)) {
            return null;
        }
        double dx = x2 - x1;
        double dy = y2 - y1;
        double t0 = 0.0;
        double t1 = 1.0;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x1 - left, right - x1, y1 - top, bottom - y1};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0.0) {
                if (q[i] < 0.0) {
                    return null;
                }
                continue;
            }
            double ratio = q[i] / p[i];
            if (p[i] < 0.0) {
                if (ratio > t1) {
                    return null;
                }
                t0 = Math.max(t0, ratio);
            } else {
                if (ratio < t0) {
                    return null;
                }
                t1 = Math.min(t1, ratio);
            }
        }
        return new double[]{x1 + t0 * dx, y1 + t0 * dy, x1 + t1 * dx, y1 + t1 * dy};
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
