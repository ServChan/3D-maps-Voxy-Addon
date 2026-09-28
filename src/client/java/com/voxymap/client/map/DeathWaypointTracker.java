package com.voxymap.client.map;

import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class DeathWaypointTracker {
    private static final int DEATH_COLOR = 0xFF8B2020;

    private static final int MAX_DEATH_WAYPOINTS = 5;

    private static float lastHealth = -1.0f;
    private static WorldIdentifier lastWorldId;

    private DeathWaypointTracker() {
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            lastHealth = -1.0f;
            lastWorldId = null;
            return;
        }

        WorldIdentifier worldId = WorldIdentifier.of(minecraft.level);
        if (worldId == null) {
            lastHealth = -1.0f;
            lastWorldId = null;
            return;
        }
        if (!worldId.equals(lastWorldId)) {

            lastWorldId = worldId;
            lastHealth = minecraft.player.getHealth();
            return;
        }

        float health = minecraft.player.getHealth();
        if (VoxyMapSettings.deathPointsEnabled() && lastHealth > 0.0f && health <= 0.0f) {
            String name = Component.translatable("screen.voxymap.waypoint.death_name").getString();
            List<WaypointManager.Waypoint> previousDeaths = new ArrayList<>();
            for (WaypointManager.Waypoint wp : WaypointManager.forWorld(worldId.getWorldId())) {
                if (!wp.shared() && name.equals(wp.name())) {
                    previousDeaths.add(wp);
                }
            }

            while (previousDeaths.size() >= MAX_DEATH_WAYPOINTS) {
                WaypointManager.Waypoint oldest = previousDeaths.remove(0);
                WaypointManager.remove(worldId.getWorldId(), oldest.id());
            }
            WaypointManager.add(worldId.getWorldId(), name,
                    minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(),
                    DEATH_COLOR, false);
        }
        lastHealth = health;
    }
}
