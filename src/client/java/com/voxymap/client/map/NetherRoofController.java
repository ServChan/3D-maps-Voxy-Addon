package com.voxymap.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/**
 * Drives the Nether roof removal while the 3D map is open: it removes the bedrock ceiling and the
 * server anti-xray shell above the map so the caverns below can be seen. The patched Voxy shaders discard
 * only whole quads inside the roof band (never a fragment-level slice, which would expose server decoy
 * geometry as a tiled cross-section); this class computes the cut plane and the aperture around the
 * panned view and hands them to {@link NetherRoofState}.
 */
public final class NetherRoofController {
    private static final String NETHER = "minecraft:the_nether";

    private static boolean cuttingSomething;

    private NetherRoofController() {
    }

    /** Updates cheap shader-only view parameters; safe to call whenever pan or zoom changes. */
    public static void updateViewContext(Minecraft minecraft, double viewX, double viewY, double viewZ,
                                         double blocksPerPixel, int viewportMinPixels) {
        if (minecraft == null || minecraft.level == null
                || !NETHER.equals(minecraft.level.dimension().identifier().toString())) {
            NetherRoofState.clearAperture();
            return;
        }
        Level level = minecraft.level;
        NetherRoofPolicy.Aperture aperture = NetherRoofPolicy.aperture(
                level.dimensionType().minY(), level.dimensionType().logicalHeight(),
                viewY, blocksPerPixel, viewportMinPixels);
        NetherRoofState.setAperture(viewX, viewZ, aperture.innerCutoff(), aperture.radius());
    }

    /** Called from the screen tick, never from the per-frame render-state extraction path. */
    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            deactivate();
            return;
        }

        Level level = minecraft.level;
        boolean isNether = NETHER.equals(level.dimension().identifier().toString());

        // This threshold never slices through cached voxels. The shader removes only complete quads
        // wholly in the roof band, so it is safe on remote anti-xray caches as well.
        float netherRoofCutoff = isNether
                ? NetherRoofPolicy.cutoff(
                        level.dimensionType().minY(),
                        level.dimensionType().logicalHeight())
                : NetherRoofState.NO_CUT_VALUE;

        cuttingSomething = netherRoofCutoff != NetherRoofState.NO_CUT_VALUE;
        NetherRoofState.setUniformCutoff(netherRoofCutoff);
    }

    /** Whether the roof is currently being removed from the map. */
    public static boolean isActive() {
        return VoxyMapCameraController.isActive() && cuttingSomething;
    }

    /** Mirrors the shader's roof cut closely enough for CPU cursor picking. */
    static boolean isHiddenForMapPick(int x, int y, int z) {
        if (!cuttingSomething) return false;
        float global = NetherRoofState.uniformCutoff();
        if (global >= NetherRoofState.NO_CUT_VALUE) return false;
        float cutoff = global;
        float radius = NetherRoofState.apertureRadius();
        if (radius > 0.0f) {
            double distance = Math.hypot(x + 0.5 - NetherRoofState.apertureCenterX(),
                    z + 0.5 - NetherRoofState.apertureCenterZ());
            double blend = smoothStep((distance - radius * 0.55) / (radius * 0.45));
            cutoff = (float) (NetherRoofState.apertureInnerCutoff()
                    + (global - NetherRoofState.apertureInnerCutoff()) * blend);
        }
        return y >= cutoff;
    }

    private static double smoothStep(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    public static void deactivate() {
        cuttingSomething = false;
        NetherRoofState.setUniformCutoff(NetherRoofState.NO_CUT_VALUE);
        NetherRoofState.clearAperture();
    }
}
