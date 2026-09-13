package com.voxymap.client.map;

/** Chooses the lower edge of the Nether roof/anti-xray shell removed from the map. */
public final class NetherRoofPolicy {
    static final float ROOF_MARGIN = 32.0f;
    static final float MIN_APERTURE_Y_FROM_FLOOR = 48.0f;
    static final float CLOSE_ZOOM_BLOCKS_PER_PIXEL = 0.55f;
    static final float WIDE_ZOOM_BLOCKS_PER_PIXEL = 1.0f;
    static final float CLOSE_ZOOM_HEADROOM = 30.0f;
    static final float WIDE_ZOOM_HEADROOM = 46.0f;

    private NetherRoofPolicy() {
    }

    /**
     * Remote servers can leave several stacked LOD section caps below the literal bedrock layer.
     * Removing complete quads in the upper 32 blocks clears those caps without fragment-slicing the
     * anti-xray volume or flattening the map at the player's current height.
     */
    public static float cutoff(int minY, int logicalHeight) {
        // A datapack could keep the "minecraft:the_nether" id while shrinking logicalHeight below the
        // roof margin. Without this floor the cut plane would fall below minY and remove the entire
        // dimension instead of just its ceiling.
        return Math.max(minY, minY + logicalHeight - ROOF_MARGIN);
    }

    /**
     * Builds a view-dependent inspection aperture. Close views need a deeper opening to keep fine
     * LOD walls from hiding the subject; wide views keep the architectural silhouette. The aperture
     * radius follows the visible world footprint rather than a fixed block radius.
     */
    public static Aperture aperture(int minY, int logicalHeight, double viewY,
                                    double blocksPerPixel, int viewportMinPixels) {
        float roofCutoff = cutoff(minY, logicalHeight);
        double zoomBlend = clamp((blocksPerPixel - CLOSE_ZOOM_BLOCKS_PER_PIXEL)
                / (WIDE_ZOOM_BLOCKS_PER_PIXEL - CLOSE_ZOOM_BLOCKS_PER_PIXEL), 0.0, 1.0);
        double headroom = CLOSE_ZOOM_HEADROOM
                + (WIDE_ZOOM_HEADROOM - CLOSE_ZOOM_HEADROOM) * smoothStep(zoomBlend);
        float apertureFloor = Math.min(roofCutoff, minY + MIN_APERTURE_Y_FROM_FLOOR);
        float innerCutoff = (float) clamp(viewY + headroom, apertureFloor, roofCutoff);
        float radius = (float) clamp(viewportMinPixels * blocksPerPixel * 0.48, 96.0, 768.0);
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
