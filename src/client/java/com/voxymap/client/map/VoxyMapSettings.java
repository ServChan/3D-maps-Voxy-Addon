package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static boolean waypointsEnabled = true;
    private static boolean minimapEnabled = true;
    private static TimeOfDay timeOfDayMode = TimeOfDay.AUTO;
    private static boolean mobsEnabled = true;
    private static boolean minimapRotates = false;
    private static boolean chunkGridEnabled = false;
    private static boolean deathPointsEnabled = true;
    private static boolean fogSuppressionEnabled = true;

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
        waypointsEnabled = readBoolean(properties.getProperty("waypointsEnabled"), waypointsEnabled);
        minimapEnabled = readBoolean(properties.getProperty("minimapEnabled"), minimapEnabled);
        timeOfDayMode = TimeOfDay.parse(properties.getProperty("timeOfDayMode"));
        mobsEnabled = readBoolean(properties.getProperty("mobsEnabled"), mobsEnabled);
        minimapRotates = readBoolean(properties.getProperty("minimapRotates"), minimapRotates);
        chunkGridEnabled = readBoolean(properties.getProperty("chunkGridEnabled"), chunkGridEnabled);
        deathPointsEnabled = readBoolean(properties.getProperty("deathPointsEnabled"), deathPointsEnabled);
        fogSuppressionEnabled = readBoolean(properties.getProperty("fogSuppressionEnabled"), fogSuppressionEnabled);
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
        properties.setProperty("waypointsEnabled", Boolean.toString(waypointsEnabled));
        properties.setProperty("minimapEnabled", Boolean.toString(minimapEnabled));
        properties.setProperty("timeOfDayMode", timeOfDayMode.name().toLowerCase(Locale.ROOT));
        properties.setProperty("mobsEnabled", Boolean.toString(mobsEnabled));
        properties.setProperty("minimapRotates", Boolean.toString(minimapRotates));
        properties.setProperty("chunkGridEnabled", Boolean.toString(chunkGridEnabled));
        properties.setProperty("deathPointsEnabled", Boolean.toString(deathPointsEnabled));
        properties.setProperty("fogSuppressionEnabled", Boolean.toString(fogSuppressionEnabled));

        AtomicPropertiesWriter.save(CONFIG_PATH, BACKUP_PATH, TEMP_PATH, properties, "VoxyMap client settings");
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

    /** Single-button variant of {@link #changeCameraSpeed} for the Mod Menu config screen, which (unlike
     * the in-map settings panel) has no separate -/+ buttons - wraps back to the minimum past the
     * maximum instead of clamping, like {@link #cycleTimeOfDayMode()}. */
    public static void cycleCameraSpeed() {
        load();
        cameraSpeedMultiplier = cameraSpeedMultiplier >= 4.0 - 1e-9 ? 0.25 : clamp(cameraSpeedMultiplier + 0.25, 0.25, 4.0);
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

    public static boolean waypointsEnabled() {
        load();
        return waypointsEnabled;
    }

    public static void toggleWaypointsVisible() {
        load();
        waypointsEnabled = !waypointsEnabled;
        save();
    }

    public static boolean minimapEnabled() {
        load();
        return minimapEnabled;
    }

    public static void toggleMinimap() {
        load();
        minimapEnabled = !minimapEnabled;
        save();
    }

    public static TimeOfDay timeOfDayMode() {
        load();
        return timeOfDayMode;
    }

    public static void cycleTimeOfDayMode() {
        load();
        TimeOfDay[] values = TimeOfDay.values();
        timeOfDayMode = values[(timeOfDayMode.ordinal() + 1) % values.length];
        save();
    }

    public static boolean mobsEnabled() {
        load();
        return mobsEnabled;
    }

    public static void toggleMobs() {
        load();
        mobsEnabled = !mobsEnabled;
        save();
    }

    public static boolean minimapRotates() {
        load();
        return minimapRotates;
    }

    public static void toggleMinimapRotates() {
        load();
        minimapRotates = !minimapRotates;
        save();
    }

    public static boolean chunkGridEnabled() {
        load();
        return chunkGridEnabled;
    }

    public static void toggleChunkGrid() {
        load();
        chunkGridEnabled = !chunkGridEnabled;
        save();
    }

    public static boolean deathPointsEnabled() {
        load();
        return deathPointsEnabled;
    }

    public static void toggleDeathPoints() {
        load();
        deathPointsEnabled = !deathPointsEnabled;
        save();
    }

    /** Whether VoxyMap should suppress environmental/shader-pack fog while the map is open. Defaults
     * to on since the fullscreen map view reads poorly through normal ground-level fog; players who
     * prefer the atmospheric look (or hit a shader incompatibility) can turn it back off. */
    public static boolean fogSuppressionEnabled() {
        load();
        return fogSuppressionEnabled;
    }

    public static void toggleFogSuppression() {
        load();
        fogSuppressionEnabled = !fogSuppressionEnabled;
        save();
    }

    /** Null while the map is closed or the user picked AUTO - in both cases every lighting-forcing
     * mixin ({@code LevelMixin}, {@code ClientClockManagerMixin}) should leave the real day/night cycle
     * untouched. Otherwise, the mode those mixins should pin the map's lighting to. Shared here so the
     * two mixins - one on {@code Level}, one on the unrelated {@code ClientClockManager} - agree on the
     * exact same condition rather than drifting apart. */
    public static TimeOfDay activeMapLightingOverride() {
        if (!VoxyMapCameraController.isActive()) {
            return null;
        }
        TimeOfDay mode = timeOfDayMode();
        return mode == TimeOfDay.AUTO ? null : mode;
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

    /** Governs the lighting forced onto the fullscreen 3D map while it's open (see
     * {@code LevelMixin}) - by default the map always keeps itself readable by forcing midday, no
     * rain/thunder. AUTO instead lets the real world clock and weather show through; DAY/NIGHT pin the
     * map to one lighting look regardless of the actual time, e.g. for a consistent screenshot. */
    public enum TimeOfDay {
        AUTO("screen.voxymap.settings.timeofday.auto"),
        DAY("screen.voxymap.settings.timeofday.day"),
        NIGHT("screen.voxymap.settings.timeofday.night");

        private final String translationKey;

        TimeOfDay(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }

        private static TimeOfDay parse(String value) {
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

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
