package com.voxymap.client.map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.voxymap.client.VoxyMapClient;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.world.phys.Vec3;

/**
 * Draws each of the current world's waypoints as a vertical beacon-style beam in the game world
 * itself, not just on the minimap/fullscreen map - closing the map used to leave a placed waypoint
 * with no visible presence in ordinary gameplay at all. Reuses vanilla's own
 * {@link BeaconRenderer#submitBeaconBeam} geometry instead of hand-rolled quads, so the beam's
 * blending, glow and depth behavior are identical to a real beacon's.
 */
public final class WaypointBeaconRenderer {
    // Tall enough to poke through any realistic build limit/mountain and read as "going up forever",
    // matching the topmost segment of a vanilla multi-color beacon beam.
    private static final int BEAM_HEIGHT = 1024;
    // A beam is meant to be visible from far away (that is the point), but an unbounded render
    // distance would submit geometry for every waypoint in the world regardless of relevance -
    // bounded generously past normal render distance instead.
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
            // Never take down the render thread over a cosmetic overlay - log once so a genuine
            // incompatibility (e.g. this Fabric API's render-event shape changing) is discoverable
            // without spamming the log every single frame.
            if (!warnedOnce) {
                warnedOnce = true;
                VoxyMapClient.LOGGER.warn("[VoxyMap] Failed to render waypoint beacons in the world", exception);
            }
        }
    }

    private static void renderBeams(LevelRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !VoxyMapSettings.waypointsEnabled()
                || VoxyMapCameraController.isActive()) {
            return; // Hidden while the fullscreen map's own detached camera is active, like the minimap.
        }

        WorldIdentifier identifier = WorldIdentifier.of(minecraft.level);
        String worldId = identifier == null ? null : identifier.getWorldId();
        if (worldId == null) {
            return;
        }

        Camera camera = minecraft.gameRenderer.mainCamera();
        Vec3 cameraPos = camera.position();
        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector collector = context.submitNodeCollector();
        float animationTime = (float) minecraft.level.getGameTime();

        for (WaypointManager.Waypoint waypoint : WaypointManager.forWorld(worldId)) {
            double dx = waypoint.x() - cameraPos.x;
            double dy = waypoint.y() - cameraPos.y;
            double dz = waypoint.z() - cameraPos.z;
            if (dx * dx + dy * dy + dz * dz > MAX_RENDER_DISTANCE_SQ) {
                continue;
            }

            // Waypoint colors carry an alpha byte for 2D map/minimap blending; the beam shader has its
            // own hardcoded alpha handling and expects a plain opaque RGB, like a vanilla beacon's.
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
