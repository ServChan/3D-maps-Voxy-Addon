package com.voxymap.client.map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voxymap.client.VoxyMapClient;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.world.phys.Vec3;

public final class WaypointBeaconRenderer {

    private static final int BEAM_HEIGHT = 1024;

    private static final double MAX_RENDER_DISTANCE_SQ = 512.0 * 512.0;

    private static boolean warnedOnce = false;

    private WaypointBeaconRenderer() {
    }

    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(WaypointBeaconRenderer::render);
    }

    private static void render(LevelRenderContext context) {
        try {
            renderBeams(context);
        } catch (RuntimeException exception) {

            if (!warnedOnce) {
                warnedOnce = true;
                VoxyMapClient.LOGGER.warn("Could not draw waypoint beacons in the world", exception);
            }
        }
    }

    private static void renderBeams(LevelRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !VoxyMapSettings.waypointsEnabled()
                || !VoxyMapSettings.waypointBeams()
                || VoxyMapCameraController.isActive()) {
            return;
        }

        String worldId = MinimapRenderer.currentWorldId(minecraft);
        if (worldId == null) {
            return;
        }

        Camera camera = minecraft.gameRenderer.mainCamera();
        Vec3 cameraPos = camera.position();
        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector collector = context.submitNodeCollector();
        float animationTime = Math.floorMod(minecraft.level.getGameTime(), 40L)
                + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        VoxyMapSettings.WaypointRange range = VoxyMapSettings.waypointRange();
        double maxDistanceSq = range.unlimited() ? MAX_RENDER_DISTANCE_SQ
                : Math.min(MAX_RENDER_DISTANCE_SQ, (double) range.blocks() * range.blocks());

        for (WaypointManager.Waypoint waypoint : WaypointManager.visibleForWorld(worldId, WaypointDisplay.Surface.WORLD)) {
            double dx = waypoint.x() - cameraPos.x;
            double dy = waypoint.y() - cameraPos.y;
            double dz = waypoint.z() - cameraPos.z;
            if (dx * dx + dy * dy + dz * dz > maxDistanceSq) {
                continue;
            }

            int color = 0xFF000000 | (waypoint.color() & 0x00FFFFFF);
            poseStack.pushPose();
            poseStack.translate(dx, dy, dz);
            BeaconRenderer.submitBeaconBeam(poseStack, collector, BeaconRenderer.BEAM_LOCATION,
                    1.0f, animationTime, 0, BEAM_HEIGHT, color,
                    BeaconRenderer.SOLID_BEAM_RADIUS, BeaconRenderer.BEAM_GLOW_RADIUS);
            poseStack.popPose();
        }
    }
}
