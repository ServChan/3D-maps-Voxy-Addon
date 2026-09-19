package com.voxymap.client.map;

/**
 * The Nether roof-removal parameters the patched Voxy shaders read each frame: the global cut plane and
 * the inspection aperture around the panned map centre (see {@link NetherRoofPolicy}). Plain state, set
 * from the screen tick by {@link NetherRoofController} and read by the render mixin.
 */
public final class NetherRoofState {
    public static final float NO_CUT_VALUE = 1.0e8f;

    private static float uniformCutoff = NO_CUT_VALUE;
    private static float apertureCenterX;
    private static float apertureCenterZ;
    private static float apertureInnerCutoff = NO_CUT_VALUE;
    private static float apertureRadius;

    private NetherRoofState() {
    }

    public static void setUniformCutoff(float cutoff) {
        uniformCutoff = cutoff;
    }

    public static void setAperture(double centerX, double centerZ, float innerCutoff, float radius) {
        apertureCenterX = (float) centerX;
        apertureCenterZ = (float) centerZ;
        apertureInnerCutoff = innerCutoff;
        apertureRadius = radius;
    }

    public static void clearAperture() {
        apertureInnerCutoff = NO_CUT_VALUE;
        apertureRadius = 0.0f;
    }

    /** Global horizontal cut plane: whole quads at or above it are discarded; {@link #NO_CUT_VALUE} means none. */
    public static float uniformCutoff() {
        return uniformCutoff;
    }

    public static float apertureCenterX() {
        return apertureCenterX;
    }

    public static float apertureCenterZ() {
        return apertureCenterZ;
    }

    public static float apertureInnerCutoff() {
        return apertureInnerCutoff;
    }

    public static float apertureRadius() {
        return apertureRadius;
    }
}
