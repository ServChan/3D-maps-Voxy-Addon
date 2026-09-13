package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class VoxyMapSettings {
    private static final String VANILLA_SQUAD_WORLD_PREFIX = "vanillaSquadWorld.";
    private static final Pattern VOXY_WORLD_ID = Pattern.compile("[0-9a-f]{32}");
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("voxymap.properties");
    private static final Path BACKUP_PATH = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".bak");
    private static final Path TEMP_PATH = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");

    private static boolean loaded = false;
    private static boolean pauseSingleplayer = true;
    private static double cameraSpeedMultiplier = 1.0;
    private static boolean roomCutawayEnabled = true;
    private static boolean vanillaSquadPlayersEnabled = false;
    private static boolean vanillaSquadFlagsEnabled = false;
    private static final Map<String, VanillaSquadMap> vanillaSquadWorldMaps = new ConcurrentHashMap<>();
    private static boolean ghastScannerRouteEnabled = true;

    private VoxyMapSettings() {
    }

    public static void load() {
        if (loaded) return;
        loaded = true;

        Properties properties = readProperties(CONFIG_PATH);
        if (properties == null) {
            properties = readProperties(BACKUP_PATH);
            if (properties != null) {
                VoxyMapClient.LOGGER.warn("[VoxyMap] Loaded settings from backup after the primary file failed.");
            }
        }
        if (properties == null) properties = new Properties();

        pauseSingleplayer = readBoolean(properties.getProperty("pauseSingleplayer"), pauseSingleplayer);
        cameraSpeedMultiplier = clamp(readDouble(properties.getProperty("cameraSpeedMultiplier"), cameraSpeedMultiplier), 0.25, 4.0);
        roomCutawayEnabled = readBoolean(properties.getProperty("roomCutawayEnabled"), roomCutawayEnabled);
        vanillaSquadPlayersEnabled = readBoolean(properties.getProperty("vanillaSquadPlayersEnabled"), vanillaSquadPlayersEnabled);
        vanillaSquadFlagsEnabled = readBoolean(properties.getProperty("vanillaSquadFlagsEnabled"), vanillaSquadFlagsEnabled);
        vanillaSquadWorldMaps.clear();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(VANILLA_SQUAD_WORLD_PREFIX)) continue;
            String worldId = key.substring(VANILLA_SQUAD_WORLD_PREFIX.length());
            VanillaSquadMap map = VanillaSquadMap.parse(properties.getProperty(key));
            if (validWorldId(worldId) && map != VanillaSquadMap.AUTO) {
                vanillaSquadWorldMaps.put(worldId, map);
            }
        }
        ghastScannerRouteEnabled = readBoolean(properties.getProperty("ghastScannerRouteEnabled"), ghastScannerRouteEnabled);
    }

    public static void save() {
        Properties properties = new Properties();
        properties.setProperty("pauseSingleplayer", Boolean.toString(pauseSingleplayer));
        properties.setProperty("cameraSpeedMultiplier", String.format(Locale.ROOT, "%.2f", cameraSpeedMultiplier));
        properties.setProperty("roomCutawayEnabled", Boolean.toString(roomCutawayEnabled));
        properties.setProperty("vanillaSquadPlayersEnabled", Boolean.toString(vanillaSquadPlayersEnabled));
        properties.setProperty("vanillaSquadFlagsEnabled", Boolean.toString(vanillaSquadFlagsEnabled));
        vanillaSquadWorldMaps.forEach((worldId, map) -> properties.setProperty(
                VANILLA_SQUAD_WORLD_PREFIX + worldId, map.name().toLowerCase(Locale.ROOT)));
        properties.setProperty("ghastScannerRouteEnabled", Boolean.toString(ghastScannerRouteEnabled));

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream output = Files.newOutputStream(TEMP_PATH)) {
                properties.store(output, "VoxyMap client settings");
            }
            // Preserve the last known-good backup if the primary file is corrupt. Blindly copying
            // an unreadable primary here would destroy the only recoverable settings snapshot.
            if (readProperties(CONFIG_PATH, false) != null) {
                Files.copy(CONFIG_PATH, BACKUP_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
            replaceAtomically(TEMP_PATH, CONFIG_PATH);
        } catch (IOException exception) {
            VoxyMapClient.LOGGER.error("[VoxyMap] Could not save settings to {}", CONFIG_PATH, exception);
            try {
                Files.deleteIfExists(TEMP_PATH);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
        }
    }

    public static boolean pauseSingleplayer() {
        load();
        return pauseSingleplayer;
    }

    public static void togglePauseSingleplayer() {
        load();
        pauseSingleplayer = !pauseSingleplayer;
        save();
    }

    public static double cameraSpeedMultiplier() {
        load();
        return cameraSpeedMultiplier;
    }

    public static void changeCameraSpeed(double delta) {
        load();
        cameraSpeedMultiplier = clamp(cameraSpeedMultiplier + delta, 0.25, 4.0);
        save();
    }

    public static boolean roomCutawayEnabled() {
        load();
        return roomCutawayEnabled;
    }

    public static void toggleRoomCutaway() {
        load();
        roomCutawayEnabled = !roomCutawayEnabled;
        save();
    }

    public static boolean vanillaSquadPlayersEnabled() {
        load();
        return vanillaSquadPlayersEnabled;
    }

    public static void toggleVanillaSquadPlayers() {
        load();
        vanillaSquadPlayersEnabled = !vanillaSquadPlayersEnabled;
        save();
    }

    public static boolean vanillaSquadFlagsEnabled() {
        load();
        return vanillaSquadFlagsEnabled;
    }

    public static void toggleVanillaSquadFlags() {
        load();
        vanillaSquadFlagsEnabled = !vanillaSquadFlagsEnabled;
        save();
    }

    public static boolean ghastScannerRouteEnabled() {
        load();
        return ghastScannerRouteEnabled;
    }

    public static void toggleGhastScannerRoute() {
        load();
        ghastScannerRouteEnabled = !ghastScannerRouteEnabled;
        save();
    }

    private static double readDouble(String value, double fallback) {
        if (value == null) return fallback;
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean readBoolean(String value, boolean fallback) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        return fallback;
    }

    private static Properties readProperties(Path path) {
        return readProperties(path, true);
    }

    public static VanillaSquadMap vanillaSquadMap(String worldId) {
        load();
        return validWorldId(worldId)
                ? vanillaSquadWorldMaps.getOrDefault(worldId, VanillaSquadMap.AUTO)
                : VanillaSquadMap.AUTO;
    }

    public static void cycleVanillaSquadMap(String worldId) {
        load();
        if (!validWorldId(worldId)) return;
        VanillaSquadMap[] values = VanillaSquadMap.values();
        VanillaSquadMap current = vanillaSquadWorldMaps.getOrDefault(worldId, VanillaSquadMap.AUTO);
        VanillaSquadMap next = values[(current.ordinal() + 1) % values.length];
        if (next == VanillaSquadMap.AUTO) {
            vanillaSquadWorldMaps.remove(worldId);
        } else {
            vanillaSquadWorldMaps.put(worldId, next);
        }
        save();
    }

    private static boolean validWorldId(String worldId) {
        return worldId != null && VOXY_WORLD_ID.matcher(worldId).matches();
    }

    public enum VanillaSquadMap {
        AUTO(null, "screen.voxymap.settings.map.auto"),
        BUILD("build", "screen.voxymap.settings.map.build"),
        FARM("farm", "screen.voxymap.settings.map.farm"),
        ART("art", "screen.voxymap.settings.map.art");

        private final String mapId;
        private final String translationKey;

        VanillaSquadMap(String mapId, String translationKey) {
            this.mapId = mapId;
            this.translationKey = translationKey;
        }

        public String mapId() {
            return mapId;
        }

        public String translationKey() {
            return translationKey;
        }

        private static VanillaSquadMap parse(String value) {
            if (value != null) {
                try {
                    return valueOf(value.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    // Preserve the safe automatic default for old or manually edited configs.
                }
            }
            return AUTO;
        }
    }

    private static Properties readProperties(Path path, boolean logFailure) {
        if (!Files.exists(path)) return null;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            return properties;
        } catch (IOException | IllegalArgumentException exception) {
            if (logFailure) {
                VoxyMapClient.LOGGER.warn("[VoxyMap] Could not read settings from {}", path, exception);
            }
            return null;
        }
    }

    private static void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
