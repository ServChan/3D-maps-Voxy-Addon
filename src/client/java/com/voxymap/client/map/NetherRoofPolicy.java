package com.voxymap.client.map;

public final class NetherRoofPolicy {
    static final float ROOF_MARGIN = 32.0f;
    static final float MIN_APERTURE_Y_FROM_FLOOR = 48.0f;
    static final float CLOSE_ZOOM_BLOCKS_PER_PIXEL = 0.55f;
    static final float WIDE_ZOOM_BLOCKS_PER_PIXEL = 1.0f;
    static final float CLOSE_ZOOM_HEADROOM = 16.0f;
    static final float WIDE_ZOOM_HEADROOM = 46.0f;

    private NetherRoofPolicy() {
    }

    public static float cutoff(int minY, int logicalHeight) {

        return Math.max(minY, minY + logicalHeight - ROOF_MARGIN);
    }

    public static Aperture aperture(int minY, int logicalHeight, double viewY,
                                    double blocksPerPixel, int viewportMinPixels) {
        float roofCutoff = cutoff(minY, logicalHeight);
        double safeViewY = Double.isFinite(viewY) ? viewY : minY + MIN_APERTURE_Y_FROM_FLOOR;
        double safeBlocksPerPixel = Double.isFinite(blocksPerPixel) && blocksPerPixel > 0.0
                ? blocksPerPixel : WIDE_ZOOM_BLOCKS_PER_PIXEL;
        int safeViewportMinPixels = Math.max(1, viewportMinPixels);
        double zoomBlend = clamp((safeBlocksPerPixel - CLOSE_ZOOM_BLOCKS_PER_PIXEL)
                / (WIDE_ZOOM_BLOCKS_PER_PIXEL - CLOSE_ZOOM_BLOCKS_PER_PIXEL), 0.0, 1.0);
        double headroom = CLOSE_ZOOM_HEADROOM
                + (WIDE_ZOOM_HEADROOM - CLOSE_ZOOM_HEADROOM) * smoothStep(zoomBlend);
        float apertureFloor = Math.min(roofCutoff, minY + MIN_APERTURE_Y_FROM_FLOOR);
        float innerCutoff = (float) clamp(safeViewY + headroom, apertureFloor, roofCutoff);
        float radius = (float) clamp(safeViewportMinPixels * safeBlocksPerPixel * 0.62, 96.0, 768.0);
        return new Aperture(innerCutoff, radius);
    }

    private static double smoothStep(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Aperture(float innerCutoff, float radius) {
    }
}
