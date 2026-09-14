package com.voxymap.client.map;

import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Drops a waypoint at the player's position the moment their health crosses to zero - an automatic
 * death point, Xaero-style. Tracked by watching the health transition rather than any death event: there
 * is no client-side "you died" callback, and by the time the death screen shows, the player entity may
 * already have respawned elsewhere, so this has to catch the exact tick health first reaches zero, while
 * the entity is still standing where it died.
 */
public final class DeathWaypointTracker {
    private static final int DEATH_COLOR = 0xFF8B2020;

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
            // A fresh world/dimension - seed the baseline instead of comparing against a stale health
            // value from wherever the player was tracked last, which could otherwise fire a false death.
            lastWorldId = worldId;
            lastHealth = minecraft.player.getHealth();
            return;
        }

        float health = minecraft.player.getHealth();
        if (VoxyMapSettings.deathPointsEnabled() && lastHealth > 0.0f && health <= 0.0f) {
            String name = Component.translatable("screen.voxymap.waypoint.death_name").getString();
            WaypointManager.add(worldId.getWorldId(), name,
                    minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(),
                    DEATH_COLOR, false);
        }
        lastHealth = health;
    }
}
