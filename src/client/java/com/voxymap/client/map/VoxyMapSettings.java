package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;
import com.voxymap.client.integration.VanillaSquadServer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

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

    public static final double CAMERA_SPEED_MIN = 0.25;
    public static final double CAMERA_SPEED_MAX = 4.0;
    public static final double CAMERA_SPEED_STEP = 0.25;

    private static boolean loaded = false;
    private static boolean pauseSingleplayer = true;
    private static double cameraSpeedMultiplier = 1.0;
    private static boolean vanillaSquadPlayersEnabled = false;
    private static boolean vanillaSquadFlagsEnabled = false;
    private static final Map<String, VanillaSquadMap> vanillaSquadWorldMaps = new ConcurrentHashMap<>();
    private static boolean waypointsEnabled = true;
    private static boolean minimapEnabled = true;
    private static TimeOfDay timeOfDayMode = TimeOfDay.AUTO;
    private static boolean mobsEnabled = true;
    private static boolean minimapRotates = false;
    private static boolean chunkGridEnabled = false;
    private static boolean deathPointsEnabled = true;
    private static boolean viewDirectionEnabled = true;
    private static ScreenshotContent screenshotContent = ScreenshotContent.NO_INTERFACE;
    private static FogSuppression fogSuppression = FogSuppression.ALWAYS;

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
        waypointsEnabled = readBoolean(properties.getProperty("waypointsEnabled"), waypointsEnabled);
        minimapEnabled = readBoolean(properties.getProperty("minimapEnabled"), minimapEnabled);
        timeOfDayMode = TimeOfDay.parse(properties.getProperty("timeOfDayMode"));
        mobsEnabled = readBoolean(properties.getProperty("mobsEnabled"), mobsEnabled);
        minimapRotates = readBoolean(properties.getProperty("minimapRotates"), minimapRotates);
        chunkGridEnabled = readBoolean(properties.getProperty("chunkGridEnabled"), chunkGridEnabled);
        deathPointsEnabled = readBoolean(properties.getProperty("deathPointsEnabled"), deathPointsEnabled);
        viewDirectionEnabled = readBoolean(properties.getProperty("viewDirectionEnabled"), viewDirectionEnabled);
        screenshotContent = ScreenshotContent.parse(properties.getProperty("screenshotContent"));
        fogSuppression = FogSuppression.parse(properties.getProperty("fogSuppressionMode"),
                properties.getProperty("fogSuppressionEnabled"));
    }

    public static void save() {
        Properties properties = new Properties();
        properties.setProperty("pauseSingleplayer", Boolean.toString(pauseSingleplayer));
        properties.setProperty("cameraSpeedMultiplier", String.format(Locale.ROOT, "%.2f", cameraSpeedMultiplier));
        properties.setProperty("vanillaSquadPlayersEnabled", Boolean.toString(vanillaSquadPlayersEnabled));
        properties.setProperty("vanillaSquadFlagsEnabled", Boolean.toString(vanillaSquadFlagsEnabled));
        vanillaSquadWorldMaps.forEach((worldId, map) -> properties.setProperty(
                VANILLA_SQUAD_WORLD_PREFIX + worldId, map.name().toLowerCase(Locale.ROOT)));
        properties.setProperty("waypointsEnabled", Boolean.toString(waypointsEnabled));
        properties.setProperty("minimapEnabled", Boolean.toString(minimapEnabled));
        properties.setProperty("timeOfDayMode", timeOfDayMode.name().toLowerCase(Locale.ROOT));
        properties.setProperty("mobsEnabled", Boolean.toString(mobsEnabled));
        properties.setProperty("minimapRotates", Boolean.toString(minimapRotates));
        properties.setProperty("chunkGridEnabled", Boolean.toString(chunkGridEnabled));
        properties.setProperty("deathPointsEnabled", Boolean.toString(deathPointsEnabled));
        properties.setProperty("viewDirectionEnabled", Boolean.toString(viewDirectionEnabled));
        properties.setProperty("screenshotContent", screenshotContent.name().toLowerCase(Locale.ROOT));
        properties.setProperty("fogSuppressionMode", fogSuppression.name().toLowerCase(Locale.ROOT));

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

    /**
     * Sets the camera speed directly (the Mod Menu slider), snapped to the same {@value #CAMERA_SPEED_STEP}
     * steps the in-map -/+ buttons move in. Does nothing, and does not rewrite the config file, when the
     * snapped value is unchanged - a slider drag reports every pixel of movement.
     */
    public static void setCameraSpeed(double multiplier) {
        load();
        double snapped = clamp(Math.round(multiplier / CAMERA_SPEED_STEP) * CAMERA_SPEED_STEP,
                CAMERA_SPEED_MIN, CAMERA_SPEED_MAX);
        if (Math.abs(snapped - cameraSpeedMultiplier) < 1e-9) {
            return;
        }
        cameraSpeedMultiplier = snapped;
        save();
    }

    public static boolean vanillaSquadPlayersEnabled() {
        load();
        return vanillaSquadPlayersEnabled;
    }

    /** Enabled in the settings AND connected to VanillaSquad; the overlay and its fetching key off this. */
    public static boolean vanillaSquadPlayersActive() {
        return vanillaSquadPlayersEnabled() && VanillaSquadServer.isConnected();
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

    /** Enabled in the settings AND connected to VanillaSquad; the overlay and its fetching key off this. */
    public static boolean vanillaSquadFlagsActive() {
        return vanillaSquadFlagsEnabled() && VanillaSquadServer.isConnected();
    }

    public static void toggleVanillaSquadFlags() {
        load();
        vanillaSquadFlagsEnabled = !vanillaSquadFlagsEnabled;
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
    /** Whether the map draws the local player's field of view and look direction. */
    public static boolean viewDirectionEnabled() {
        load();
        return viewDirectionEnabled;
    }

    public static void toggleViewDirection() {
        load();
        viewDirectionEnabled = !viewDirectionEnabled;
        save();
    }

    public static ScreenshotContent screenshotContent() {
        load();
        return screenshotContent;
    }

    public static void cycleScreenshotContent() {
        load();
        ScreenshotContent[] values = ScreenshotContent.values();
        screenshotContent = values[(screenshotContent.ordinal() + 1) % values.length];
        save();
    }

    public static FogSuppression fogSuppressionMode() {
        load();
        return fogSuppression;
    }

    public static void cycleFogSuppression() {
        load();
        FogSuppression[] values = FogSuppression.values();
        fogSuppression = values[(fogSuppression.ordinal() + 1) % values.length];
        save();
    }

    /** Whether shader-pack fog should be removed from the map right now, given the current dimension. */
    public static boolean fogSuppressionActive() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean nether = minecraft != null && minecraft.level != null
                && "minecraft:the_nether".equals(minecraft.level.dimension().identifier().toString());
        return fogSuppressionMode().appliesIn(nether);
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
    /** What the screenshot key keeps on the map. Declaration order is the order the setting cycles through. */
    public enum ScreenshotContent {
        NO_INTERFACE("screen.voxymap.settings.screenshot.clean"),
        WITH_MARKERS("screen.voxymap.settings.screenshot.markers");

        private final String translationKey;

        ScreenshotContent(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }

        static ScreenshotContent parse(String value) {
            if (value != null) {
                try {
                    return valueOf(value.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    // Keep the clean default for hand-edited configs.
                }
            }
            return NO_INTERFACE;
        }
    }

    /** Where the map removes shader-pack fog. Declaration order is the order the setting cycles through. */
    public enum FogSuppression {
        ALWAYS("screen.voxymap.settings.on"),
        NETHER_ONLY("screen.voxymap.settings.fogsuppression.nether"),
        OFF("screen.voxymap.settings.off");

        private final String translationKey;

        FogSuppression(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }

        /** Whether fog is removed in the Nether ({@code nether}) or in every other dimension. */
        public boolean appliesIn(boolean nether) {
            return switch (this) {
                case ALWAYS -> true;
                case NETHER_ONLY -> nether;
                case OFF -> false;
            };
        }

        /**
         * @param mode   the stored {@code fogSuppressionMode}, or null when absent
         * @param legacy the old on/off {@code fogSuppressionEnabled} value, used only when there is no mode yet
         */
        static FogSuppression parse(String mode, String legacy) {
            if (mode != null) {
                try {
                    return valueOf(mode.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    // Fall through to the legacy value / default for hand-edited configs.
                }
            }
            if (legacy != null) {
                return "false".equalsIgnoreCase(legacy.trim()) ? OFF : ALWAYS;
            }
            return ALWAYS;
        }
    }

    /**
     * Fixed lighting for the map. The world clock is pinned to {@link #clockTicks()}: 0 is sunrise,
     * 6000 noon, 12000 sunset and 18000 midnight. Morning and evening sit 1000 ticks (about 15 degrees of
     * sun height) after sunrise and before sunset, so the sun is low and warm but the terrain is still lit.
     * Declaration order is the order the settings button cycles through.
     */
    public enum TimeOfDay {
        AUTO("screen.voxymap.settings.timeofday.auto", -1L, false),
        MORNING("screen.voxymap.settings.timeofday.morning", 1000L, false),
        DAY("screen.voxymap.settings.timeofday.day", 6000L, false),
        EVENING("screen.voxymap.settings.timeofday.evening", 11000L, false),
        NIGHT("screen.voxymap.settings.timeofday.night", 18000L, true);

        private final String translationKey;
        private final long clockTicks;
        private final boolean dark;

        TimeOfDay(String translationKey, long clockTicks, boolean dark) {
            this.translationKey = translationKey;
            this.clockTicks = clockTicks;
            this.dark = dark;
        }

        public String translationKey() {
            return translationKey;
        }

        /** World clock value to pin, or -1 for {@link #AUTO}, which leaves the real clock alone. */
        public long clockTicks() {
            return clockTicks;
        }

        /** Whether Minecraft should treat the sky as dark (mob spawning light, sky darkening). */
        public boolean isDark() {
            return dark;
        }

        /** The value of {@code Level#getSkyDarken()} for this lighting: 11 at night, 0 otherwise. */
        public int skyDarken() {
            return dark ? 11 : 0;
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
