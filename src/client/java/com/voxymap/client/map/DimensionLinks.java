package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class DimensionLinks {
    public record Foreign(WaypointManager.Waypoint original, WaypointManager.Waypoint local, String sourceWorldId,
                          WaypointText.Dimension source) {
    }

    private static Object lastConnection;
    private static WaypointText.Dimension lastDimension;
    private static String lastWorldId;

    private static String cachedSourceId;
    private static WaypointText.Dimension cachedTarget;
    private static List<WaypointManager.Waypoint> cachedSource;
    private static List<Foreign> cachedForeign = List.of();
    private static int cachedPrefsVersion = -1;
    private static boolean failureLogged;

    private DimensionLinks() {
    }

    public static void tick(Minecraft minecraft) {
        Object connection = minecraft.getConnection();
        if (minecraft.level == null || connection == null) {
            lastConnection = null;
            lastDimension = null;
            lastWorldId = null;
            return;
        }
        String worldId = MinimapRenderer.currentWorldId(minecraft);
        WaypointText.Dimension dimension = WaypointChatShare.currentDimension(minecraft);
        if (worldId == null) {
            return;
        }
        if (connection == lastConnection && lastWorldId != null && dimension != lastDimension) {
            if (lastDimension == WaypointText.Dimension.OVERWORLD && dimension == WaypointText.Dimension.NETHER) {
                VoxyMapSettings.linkDimensions(lastWorldId, worldId);
            } else if (lastDimension == WaypointText.Dimension.NETHER && dimension == WaypointText.Dimension.OVERWORLD) {
                VoxyMapSettings.linkDimensions(worldId, lastWorldId);
            }
        }
        lastConnection = connection;
        lastDimension = dimension;
        lastWorldId = worldId;
    }

    public static List<Foreign> foreignWaypoints(Minecraft minecraft) {
        if (!VoxyMapSettings.otherDimensionWaypoints() || !VoxyMapSettings.waypointsEnabled() || minecraft.level == null) {
            return List.of();
        }
        WaypointText.Dimension here = WaypointChatShare.currentDimension(minecraft);
        WaypointText.Dimension source = here == WaypointText.Dimension.NETHER ? WaypointText.Dimension.OVERWORLD
                : here == WaypointText.Dimension.OVERWORLD ? WaypointText.Dimension.NETHER : null;
        if (source == null) {
            return List.of();
        }
        String sourceId = counterpartWorldId(minecraft.level, MinimapRenderer.currentWorldId(minecraft), here);
        if (sourceId == null) {
            return List.of();
        }
        List<WaypointManager.Waypoint> sourceWaypoints = WaypointManager.forWorld(sourceId);
        int prefsVersion = WaypointPrefs.version();
        if (sourceWaypoints == cachedSource && sourceId.equals(cachedSourceId) && here == cachedTarget
                && prefsVersion == cachedPrefsVersion) {
            return cachedForeign;
        }
        double factor = WaypointText.scaleFactor(source, here);
        List<Foreign> converted = new ArrayList<>(sourceWaypoints.size());
        for (WaypointManager.Waypoint waypoint : sourceWaypoints) {
            if (WaypointPrefs.hidden(waypoint.id())) {
                continue;
            }
            converted.add(new Foreign(waypoint, new WaypointManager.Waypoint(waypoint.id(), waypoint.name(),
                    waypoint.x() * factor, waypoint.y(), waypoint.z() * factor, waypoint.color(), waypoint.shared()),
                    sourceId, source));
        }
        cachedSource = sourceWaypoints;
        cachedSourceId = sourceId;
        cachedTarget = here;
        cachedPrefsVersion = prefsVersion;
        cachedForeign = List.copyOf(converted);
        return cachedForeign;
    }

    static String counterpartWorldId(Level level, String worldId, WaypointText.Dimension here) {
        if (worldId == null || here == null) {
            return null;
        }
        String linked = here == WaypointText.Dimension.NETHER ? VoxyMapSettings.linkedOverworld(worldId)
                : here == WaypointText.Dimension.OVERWORLD ? VoxyMapSettings.linkedNether(worldId) : null;
        if (linked != null) {
            return linked;
        }
        ResourceKey<Level> target = here == WaypointText.Dimension.NETHER ? Level.OVERWORLD
                : here == WaypointText.Dimension.OVERWORLD ? Level.NETHER : null;
        if (target == null) {
            return null;
        }
        try {
            WorldIdentifier identifier = WorldIdentifier.of(level);
            return identifier == null ? null : new WorldIdentifier(target, identifier.biomeSeed, null).getWorldId();
        } catch (RuntimeException | LinkageError exception) {
            if (!failureLogged) {
                failureLogged = true;
                VoxyMapClient.LOGGER.warn("[VoxyMap] Could not work out the paired dimension's world id", exception);
            }
            return null;
        }
    }
}
