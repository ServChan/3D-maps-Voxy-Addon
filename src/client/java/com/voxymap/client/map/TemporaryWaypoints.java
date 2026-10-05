package com.voxymap.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class TemporaryWaypoints {
    private static final double REACH_BLOCKS = 512.0;
    private static final int COLOR = 0xFFFFC94D;

    private TemporaryWaypoints() {
    }

    public static Vec3 lookTarget(Minecraft minecraft) {
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(REACH_BLOCKS));
        BlockHitResult hit = minecraft.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return Vec3.atBottomCenterOf(hit.getBlockPos().above());
    }

    public static void placeAtLookTarget(Minecraft minecraft) {
        String worldId = MinimapRenderer.currentWorldId(minecraft);
        Vec3 target = lookTarget(minecraft);
        if (worldId == null) {
            return;
        }
        if (target == null) {
            minecraft.gui.hud.setOverlayMessage(Component.translatable("message.voxymap.waypoint.nothing_in_sight"), false);
            return;
        }
        String name = Component.translatable("screen.voxymap.waypoint.temporary_name").getString();
        if (WaypointManager.addTemporary(worldId, name, target.x, target.y, target.z, COLOR) != null) {
            minecraft.gui.hud.setOverlayMessage(Component.translatable("message.voxymap.waypoint.temporary_placed",
                    (int) Math.floor(target.x), (int) Math.floor(target.y), (int) Math.floor(target.z)), false);
        }
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        String worldId = MinimapRenderer.currentWorldId(minecraft);
        if (worldId == null) {
            return;
        }
        List<WaypointManager.Waypoint> arrived = WaypointManager.removeArrived(worldId,
                minecraft.player.getX(), minecraft.player.getZ());
        if (!arrived.isEmpty()) {
            minecraft.gui.hud.setOverlayMessage(Component.translatable("message.voxymap.waypoint.arrived",
                    arrived.getFirst().name()), false);
        }
    }
}
