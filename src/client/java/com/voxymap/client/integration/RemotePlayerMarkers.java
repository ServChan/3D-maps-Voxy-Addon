package com.voxymap.client.integration;

import com.voxymap.client.VoxyMapClient;
import com.voxymap.client.map.VoxyMapSettings;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class RemotePlayerMarkers {
    static final String PROVIDER_CLASS = "com.voxymap.client.playermarkers.VanillaSquadPlayerMapBridge";
    private static final String DEFAULT_MAP_ID = "build";
    private static final Pattern COMPACT_UUID = Pattern.compile("[0-9a-f]{32}");
    private static final Source SOURCE = loadSource();

    private RemotePlayerMarkers() {
    }

    public interface Source {
        void tick(Object worldIdentity, String dimensionId, UUID playerUuid, String playerName,
                  double playerX, double playerZ, List<Observer> observers, String voxyWorldId);

        List<RemotePlayer> playersInDimension(String dimensionId);

        String activeMapId();

        boolean activeMapResolved(Object worldIdentity);
    }

    public record RemotePlayer(String name, String world, double x, double y, double z, String uuid) {
    }

    public record Observer(String uuid, String name, double x, double z) {
    }

    public static boolean available() {
        return SOURCE != null;
    }

    public static void tick(Object worldIdentity, String dimensionId, UUID playerUuid, String playerName,
                            double playerX, double playerZ, List<Observer> observers, String voxyWorldId) {
        if (SOURCE != null) {
            SOURCE.tick(worldIdentity, dimensionId, playerUuid, playerName, playerX, playerZ, observers, voxyWorldId);
        }
    }

    public static List<RemotePlayer> playersInDimension(String dimensionId) {
        return SOURCE == null ? List.of() : SOURCE.playersInDimension(dimensionId);
    }

    public static String activeMapId(String voxyWorldId) {
        if (SOURCE != null) {
            return SOURCE.activeMapId();
        }
        return configuredOrDefaultMapId(voxyWorldId);
    }

    public static String resolvedMapId(Object worldIdentity, String voxyWorldId) {
        String configured = VoxyMapSettings.vanillaSquadMap(voxyWorldId).mapId();
        if (configured != null) {
            return configured;
        }
        return SOURCE != null && worldIdentity != null && SOURCE.activeMapResolved(worldIdentity)
                ? SOURCE.activeMapId() : null;
    }

    public static String configuredOrDefaultMapId(String voxyWorldId) {
        String configured = VoxyMapSettings.vanillaSquadMap(voxyWorldId).mapId();
        return configured == null ? DEFAULT_MAP_ID : configured;
    }

    public static String normalizeUuid(String uuid) {
        if (uuid == null) return null;
        String compact = uuid.replace("-", "").toLowerCase(Locale.ROOT);
        return COMPACT_UUID.matcher(compact).matches() ? compact : null;
    }

    public static UUID toJavaUuid(String uuid) {
        if (uuid == null) {
            return null;
        }
        String hex = uuid.replace("-", "");
        if (hex.length() != 32) {
            return null;
        }
        String dashed = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-"
                + hex.substring(16, 20) + "-" + hex.substring(20);
        try {
            return UUID.fromString(dashed);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Source loadSource() {
        try {
            Class<?> provider = Class.forName(PROVIDER_CLASS, true, RemotePlayerMarkers.class.getClassLoader());
            return (Source) provider.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException absent) {
            VoxyMapClient.LOGGER.info("[VoxyMap] Built without VanillaSquad web-map player markers.");
            return null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            VoxyMapClient.LOGGER.warn("[VoxyMap] Could not start VanillaSquad web-map player markers; they stay off.", exception);
            return null;
        }
    }
}
