package com.voxymap.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

public final class NetherRoofController {
    private static final String NETHER = "minecraft:the_nether";

    private static boolean cuttingSomething;

    private NetherRoofController() {
    }

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

    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            deactivate();
            return;
        }

        Level level = minecraft.level;
        boolean isNether = NETHER.equals(level.dimension().identifier().toString());

        float netherRoofCutoff = isNether
                ? NetherRoofPolicy.cutoff(
                        level.dimensionType().minY(),
                        level.dimensionType().logicalHeight())
                : NetherRoofState.NO_CUT_VALUE;

        cuttingSomething = netherRoofCutoff != NetherRoofState.NO_CUT_VALUE;
        NetherRoofState.setUniformCutoff(netherRoofCutoff);
    }

    public static boolean isActive() {
        return VoxyMapCameraController.isActive() && cuttingSomething;
    }

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
