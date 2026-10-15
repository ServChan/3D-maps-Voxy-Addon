package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;
import com.voxymap.client.integration.RemotePlayerMarkers;
import com.voxymap.client.integration.VanillaSquadServer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class VoxyMapSettings {
    private static final String VANILLA_SQUAD_WORLD_PREFIX = "vanillaSquadWorld.";
    private static final String SLIME_SEED_PREFIX = "slimeSeed.";
    private static final String NETHER_LINK_PREFIX = "netherLink.";
    private static final String WEB_MAP_LINK_PREFIX = "webMapLink.";
    private static final Pattern VOXY_WORLD_ID = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern WEB_MAP_KEY = Pattern.compile("[a-z0-9_-]{1,32}/[a-z0-9_.-]{1,64}");
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("voxymap.properties");
    private static final Path BACKUP_PATH = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".bak");
    private static final Path TEMP_PATH = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");

    public static final double CAMERA_SPEED_MIN = 0.25;
    public static final double CAMERA_SPEED_MAX = 4.0;
    public static final double CAMERA_SPEED_STEP = 0.25;
    public static final double[] MINIMAP_ZOOM_LEVELS = {0.5, 1.0, 2.0, 4.0};
    private static final int DEFAULT_MINIMAP_ZOOM = 1;

    private static boolean loaded = false;
    private static boolean pauseSingleplayer = true;
    private static double cameraSpeedMultiplier = 1.0;
    private static boolean vanillaSquadPlayersEnabled = false;
    private static boolean vanillaSquadFlagsEnabled = false;
    private static final Map<String, VanillaSquadMap> vanillaSquadWorldMaps = new ConcurrentHashMap<>();
    private static boolean waypointsEnabled = true;
    private static WaypointLabelMode waypointLabelMode = WaypointLabelMode.AIMING;
    private static boolean waypointBeams = true;
    private static WaypointDisplay newWaypointDisplay = WaypointDisplay.EVERYWHERE;
    private static WaypointIconShape waypointIconShape = WaypointIconShape.SQUARE;
    private static WaypointIconSize waypointIconSize = WaypointIconSize.MEDIUM;
    private static WaypointLabelStyle waypointLabelStyle = WaypointLabelStyle.BOX;
    private static WaypointLabelScale waypointLabelScale = WaypointLabelScale.NORMAL;
    private static boolean waypointDistanceShown = true;
    private static WaypointRange waypointRange = WaypointRange.UNLIMITED;
    private static boolean minimapEnabled = true;
    private static TimeOfDay timeOfDayMode = TimeOfDay.AUTO;
    private static final EnumSet<RadarCategory> radarCategories = EnumSet.copyOf(RadarCategory.defaults());
    private static final EnumSet<MinimapInfoLine> infoLines = EnumSet.copyOf(MinimapInfoLine.defaults());
    private static final EnumSet<MapOverlay> mapOverlays = EnumSet.noneOf(MapOverlay.class);
    private static final Map<String, Long> slimeSeeds = new ConcurrentHashMap<>();
    private static final Map<String, String> netherLinks = new ConcurrentHashMap<>();
    private static final Map<String, String> webMapLinks = new ConcurrentHashMap<>();
    private static boolean otherDimensionWaypoints = true;
    private static RadarIcons radarIcons = RadarIcons.HEADS;
    private static boolean minimapRotates = false;
    private static MinimapColors minimapColors = MinimapColors.TEXTURES;
    private static MinimapShape minimapShape = MinimapShape.SQUARE;
    private static MinimapSize minimapSize = MinimapSize.MEDIUM;
    private static MinimapCorner minimapCorner = MinimapCorner.TOP_LEFT;
    private static int minimapZoom = DEFAULT_MINIMAP_ZOOM;
    private static double minimapX = 0.0;
    private static double minimapY = 0.0;
    private static MinimapOpacity minimapOpacity = MinimapOpacity.FULL;
    private static boolean minimapNightShading = true;
    private static CaveMode caveMode = CaveMode.AUTO;
    private static ScreenVisibility minimapScreenVisibility = ScreenVisibility.ALWAYS;
    private static boolean chunkGridEnabled = false;
    private static boolean schematicsEnabled = true;
    private static boolean spatialGuiFlatMap = true;
    private static boolean windowMap3d = true;
    private static boolean deathPointsEnabled = true;
    private static boolean viewDirectionEnabled = true;
    private static ScreenshotContent screenshotContent = ScreenshotContent.NO_INTERFACE;
    private static FogSuppression fogSuppression = FogSuppression.ALWAYS;
    private static MapWeather mapWeather = MapWeather.CLEAR;
    private static MapEntityModels mapEntityModels = MapEntityModels.PLAYERS_AND_MOBS;
    private static boolean voxyInWorld = true;
    private static boolean map2d = false;
    private static boolean uiAnimations = true;
    private static final Set<String> hiddenPlayers = ConcurrentHashMap.newKeySet();
    private static final Properties DEFAULTS = toProperties();
    private static int batchDepth;
    private static boolean savePending;

    private VoxyMapSettings() {
    }

    public static void load() {
        if (loaded) return;
        loaded = true;

        Properties properties = AtomicPropertiesWriter.looksTruncated(CONFIG_PATH) ? null : readProperties(CONFIG_PATH);
        if (properties == null) {
            properties = readProperties(BACKUP_PATH);
            if (properties != null) {
                VoxyMapClient.LOGGER.warn("Loaded settings from backup {}", BACKUP_PATH);
            }
        }
        if (properties == null) properties = new Properties();
        apply(properties);
    }

    private static void apply(Properties properties) {
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
        waypointLabelMode = parseEnum(WaypointLabelMode.class, properties.getProperty("waypointLabelMode"),
                WaypointLabelMode.AIMING);
        waypointBeams = readBoolean(properties.getProperty("waypointBeams"), waypointBeams);
        newWaypointDisplay = parseEnum(WaypointDisplay.class, properties.getProperty("newWaypointDisplay"),
                WaypointDisplay.EVERYWHERE);
        waypointIconShape = parseEnum(WaypointIconShape.class, properties.getProperty("waypointIconShape"),
                WaypointIconShape.SQUARE);
        waypointIconSize = parseEnum(WaypointIconSize.class, properties.getProperty("waypointIconSize"),
                WaypointIconSize.MEDIUM);
        waypointLabelStyle = parseEnum(WaypointLabelStyle.class, properties.getProperty("waypointLabelStyle"),
                WaypointLabelStyle.BOX);
        waypointLabelScale = parseEnum(WaypointLabelScale.class, properties.getProperty("waypointLabelScale"),
                WaypointLabelScale.NORMAL);
        waypointDistanceShown = readBoolean(properties.getProperty("waypointDistanceShown"), waypointDistanceShown);
        waypointRange = parseEnum(WaypointRange.class, properties.getProperty("waypointRange"), WaypointRange.UNLIMITED);
        minimapEnabled = readBoolean(properties.getProperty("minimapEnabled"), minimapEnabled);
        timeOfDayMode = TimeOfDay.parse(properties.getProperty("timeOfDayMode"));
        radarCategories.clear();
        radarCategories.addAll(EnumFlags.parse(RadarCategory.class, properties.getProperty("radarCategories"),
                legacyRadarCategories(properties.getProperty("mobsEnabled"))));
        mapOverlays.clear();
        mapOverlays.addAll(EnumFlags.parse(MapOverlay.class, properties.getProperty("mapOverlays"),
                EnumSet.noneOf(MapOverlay.class)));
        radarIcons = parseEnum(RadarIcons.class, properties.getProperty("radarIcons"), RadarIcons.HEADS);
        otherDimensionWaypoints = readBoolean(properties.getProperty("otherDimensionWaypoints"), otherDimensionWaypoints);
        netherLinks.clear();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(NETHER_LINK_PREFIX)) continue;
            String overworldId = key.substring(NETHER_LINK_PREFIX.length());
            String netherId = properties.getProperty(key).trim();
            if (validWorldId(overworldId) && validWorldId(netherId)) {
                netherLinks.put(overworldId, netherId);
            }
        }
        webMapLinks.clear();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(WEB_MAP_LINK_PREFIX)) continue;
            String webMapKey = key.substring(WEB_MAP_LINK_PREFIX.length());
            String worldId = properties.getProperty(key).trim();
            if (WEB_MAP_KEY.matcher(webMapKey).matches() && validWorldId(worldId)) {
                webMapLinks.put(webMapKey, worldId);
            }
        }
        slimeSeeds.clear();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(SLIME_SEED_PREFIX)) continue;
            String worldId = key.substring(SLIME_SEED_PREFIX.length());
            try {
                if (validWorldId(worldId)) {
                    slimeSeeds.put(worldId, Long.parseLong(properties.getProperty(key).trim()));
                }
            } catch (NumberFormatException ignored) {

            }
        }
        infoLines.clear();
        infoLines.addAll(EnumFlags.parse(MinimapInfoLine.class, properties.getProperty("minimapInfoLines"),
                MinimapInfoLine.defaults()));
        minimapRotates = readBoolean(properties.getProperty("minimapRotates"), minimapRotates);
        minimapColors = parseEnum(MinimapColors.class, properties.getProperty("minimapColors"), MinimapColors.TEXTURES);
        minimapShape = parseEnum(MinimapShape.class, properties.getProperty("minimapShape"), MinimapShape.SQUARE);
        minimapSize = parseEnum(MinimapSize.class, properties.getProperty("minimapSize"), MinimapSize.MEDIUM);
        minimapCorner = parseEnum(MinimapCorner.class, properties.getProperty("minimapCorner"), MinimapCorner.TOP_LEFT);
        minimapZoom = clampZoomIndex((int) readDouble(properties.getProperty("minimapZoom"), DEFAULT_MINIMAP_ZOOM));
        minimapX = clamp(readDouble(properties.getProperty("minimapX"), minimapX), 0.0, 1.0);
        minimapY = clamp(readDouble(properties.getProperty("minimapY"), minimapY), 0.0, 1.0);
        minimapOpacity = parseEnum(MinimapOpacity.class, properties.getProperty("minimapOpacity"), MinimapOpacity.FULL);
        minimapNightShading = readBoolean(properties.getProperty("minimapNightShading"), minimapNightShading);
        caveMode = parseEnum(CaveMode.class, properties.getProperty("caveMode"), CaveMode.AUTO);
        minimapScreenVisibility = parseEnum(ScreenVisibility.class, properties.getProperty("minimapScreenVisibility"),
                ScreenVisibility.ALWAYS);
        chunkGridEnabled = readBoolean(properties.getProperty("chunkGridEnabled"), chunkGridEnabled);
        schematicsEnabled = readBoolean(properties.getProperty("schematicsEnabled"), schematicsEnabled);
        spatialGuiFlatMap = readBoolean(properties.getProperty("spatialGuiFlatMap"), spatialGuiFlatMap);
        windowMap3d = readBoolean(properties.getProperty("windowMap3d"), windowMap3d);
        deathPointsEnabled = readBoolean(properties.getProperty("deathPointsEnabled"), deathPointsEnabled);
        viewDirectionEnabled = readBoolean(properties.getProperty("viewDirectionEnabled"), viewDirectionEnabled);
        screenshotContent = ScreenshotContent.parse(properties.getProperty("screenshotContent"));
        fogSuppression = FogSuppression.parse(properties.getProperty("fogSuppressionMode"),
                properties.getProperty("fogSuppressionEnabled"));
        mapWeather = parseEnum(MapWeather.class, properties.getProperty("mapWeather"), MapWeather.CLEAR);
        mapEntityModels = parseEnum(MapEntityModels.class, properties.getProperty("mapEntityModels"),
                MapEntityModels.PLAYERS_AND_MOBS);
        voxyInWorld = readBoolean(properties.getProperty("voxyInWorld"), voxyInWorld);
        map2d = readBoolean(properties.getProperty("map2d"), map2d);
        uiAnimations = readBoolean(properties.getProperty("uiAnimations"), uiAnimations);
        hiddenPlayers.clear();
        String hidden = properties.getProperty("hiddenPlayers");
        if (hidden != null) {
            for (String part : hidden.split(",")) {
                String uuid = part.trim().toLowerCase(Locale.ROOT);
                if (!uuid.isEmpty()) {
                    try {
                        hiddenPlayers.add(java.util.UUID.fromString(uuid).toString());
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
    }

    public static void save() {
        if (batchDepth > 0) {
            savePending = true;
            return;
        }
        AtomicPropertiesWriter.save(CONFIG_PATH, BACKUP_PATH, TEMP_PATH, toProperties(), "VoxyMap client settings");
    }

    public static void batch(Runnable changes) {
        load();
        batchDepth++;
        try {
            changes.run();
        } finally {
            batchDepth--;
            if (batchDepth == 0 && savePending) {
                savePending = false;
                save();
            }
        }
    }

    public static boolean isDefault(String key) {
        load();
        return java.util.Objects.equals(toProperties().getProperty(key), DEFAULTS.getProperty(key));
    }

    public static void reset(java.util.Collection<String> keys) {
        load();
        Properties properties = toProperties();
        boolean changed = false;
        for (String key : keys) {
            String value = DEFAULTS.getProperty(key);
            if (value != null && !value.equals(properties.getProperty(key))) {
                properties.setProperty(key, value);
                changed = true;
            }
        }
        if (changed) {
            apply(properties);
            save();
        }
    }

    private static Properties toProperties() {
        Properties properties = new Properties();
        properties.setProperty("pauseSingleplayer", Boolean.toString(pauseSingleplayer));
        properties.setProperty("cameraSpeedMultiplier", String.format(Locale.ROOT, "%.2f", cameraSpeedMultiplier));
        properties.setProperty("vanillaSquadPlayersEnabled", Boolean.toString(vanillaSquadPlayersEnabled));
        properties.setProperty("vanillaSquadFlagsEnabled", Boolean.toString(vanillaSquadFlagsEnabled));
        vanillaSquadWorldMaps.forEach((worldId, map) -> properties.setProperty(
                VANILLA_SQUAD_WORLD_PREFIX + worldId, map.name().toLowerCase(Locale.ROOT)));
        properties.setProperty("waypointsEnabled", Boolean.toString(waypointsEnabled));
        properties.setProperty("waypointLabelMode", waypointLabelMode.name().toLowerCase(Locale.ROOT));
        properties.setProperty("waypointBeams", Boolean.toString(waypointBeams));
        properties.setProperty("newWaypointDisplay", newWaypointDisplay.key());
        properties.setProperty("waypointIconShape", waypointIconShape.name().toLowerCase(Locale.ROOT));
        properties.setProperty("waypointIconSize", waypointIconSize.name().toLowerCase(Locale.ROOT));
        properties.setProperty("waypointLabelStyle", waypointLabelStyle.name().toLowerCase(Locale.ROOT));
        properties.setProperty("waypointLabelScale", waypointLabelScale.name().toLowerCase(Locale.ROOT));
        properties.setProperty("waypointDistanceShown", Boolean.toString(waypointDistanceShown));
        properties.setProperty("waypointRange", waypointRange.name().toLowerCase(Locale.ROOT));
        properties.setProperty("minimapEnabled", Boolean.toString(minimapEnabled));
        properties.setProperty("timeOfDayMode", timeOfDayMode.name().toLowerCase(Locale.ROOT));
        properties.setProperty("radarCategories", EnumFlags.format(radarCategories));
        properties.setProperty("minimapInfoLines", EnumFlags.format(infoLines));
        properties.setProperty("mapOverlays", EnumFlags.format(mapOverlays));
        properties.setProperty("radarIcons", radarIcons.name().toLowerCase(Locale.ROOT));
        slimeSeeds.forEach((worldId, seed) -> properties.setProperty(SLIME_SEED_PREFIX + worldId, Long.toString(seed)));
        properties.setProperty("otherDimensionWaypoints", Boolean.toString(otherDimensionWaypoints));
        netherLinks.forEach((overworldId, netherId) -> properties.setProperty(NETHER_LINK_PREFIX + overworldId, netherId));
        webMapLinks.forEach((webMapKey, worldId) -> properties.setProperty(WEB_MAP_LINK_PREFIX + webMapKey, worldId));
        properties.setProperty("minimapRotates", Boolean.toString(minimapRotates));
        properties.setProperty("minimapColors", minimapColors.name().toLowerCase(Locale.ROOT));
        properties.setProperty("minimapShape", minimapShape.name().toLowerCase(Locale.ROOT));
        properties.setProperty("minimapSize", minimapSize.name().toLowerCase(Locale.ROOT));
        properties.setProperty("minimapCorner", minimapCorner.name().toLowerCase(Locale.ROOT));
        properties.setProperty("minimapZoom", Integer.toString(minimapZoom));
        properties.setProperty("minimapX", String.format(Locale.ROOT, "%.4f", minimapX));
        properties.setProperty("minimapY", String.format(Locale.ROOT, "%.4f", minimapY));
        properties.setProperty("minimapOpacity", minimapOpacity.name().toLowerCase(Locale.ROOT));
        properties.setProperty("minimapNightShading", Boolean.toString(minimapNightShading));
        properties.setProperty("caveMode", caveMode.name().toLowerCase(Locale.ROOT));
        properties.setProperty("minimapScreenVisibility", minimapScreenVisibility.name().toLowerCase(Locale.ROOT));
        properties.setProperty("chunkGridEnabled", Boolean.toString(chunkGridEnabled));
        properties.setProperty("schematicsEnabled", Boolean.toString(schematicsEnabled));
        properties.setProperty("spatialGuiFlatMap", Boolean.toString(spatialGuiFlatMap));
        properties.setProperty("windowMap3d", Boolean.toString(windowMap3d));
        properties.setProperty("deathPointsEnabled", Boolean.toString(deathPointsEnabled));
        properties.setProperty("viewDirectionEnabled", Boolean.toString(viewDirectionEnabled));
        properties.setProperty("screenshotContent", screenshotContent.name().toLowerCase(Locale.ROOT));
        properties.setProperty("fogSuppressionMode", fogSuppression.name().toLowerCase(Locale.ROOT));
        properties.setProperty("mapWeather", mapWeather.name().toLowerCase(Locale.ROOT));
        properties.setProperty("mapEntityModels", mapEntityModels.name().toLowerCase(Locale.ROOT));
        properties.setProperty("voxyInWorld", Boolean.toString(voxyInWorld));
        properties.setProperty("map2d", Boolean.toString(map2d));
        properties.setProperty("uiAnimations", Boolean.toString(uiAnimations));
        properties.setProperty("hiddenPlayers", String.join(",", new java.util.TreeSet<>(hiddenPlayers)));
        return properties;
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

    public static boolean vanillaSquadPlayersActive() {
        return RemotePlayerMarkers.available() && vanillaSquadPlayersEnabled() && VanillaSquadServer.isConnected();
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

    public static WaypointLabelMode waypointLabelMode() {
        load();
        return waypointLabelMode;
    }

    public static void cycleWaypointLabelMode() {
        load();
        waypointLabelMode = next(waypointLabelMode);
        save();
    }

    public static boolean waypointBeams() {
        load();
        return waypointBeams;
    }

    public static void toggleWaypointBeams() {
        load();
        waypointBeams = !waypointBeams;
        save();
    }

    public static WaypointDisplay newWaypointDisplay() {
        load();
        return newWaypointDisplay;
    }

    public static void cycleNewWaypointDisplay() {
        load();
        newWaypointDisplay = newWaypointDisplay.next();
        save();
    }

    public static WaypointIconShape waypointIconShape() {
        load();
        return waypointIconShape;
    }

    public static void cycleWaypointIconShape() {
        load();
        waypointIconShape = next(waypointIconShape);
        save();
    }

    public static WaypointIconSize waypointIconSize() {
        load();
        return waypointIconSize;
    }

    public static void cycleWaypointIconSize() {
        load();
        waypointIconSize = next(waypointIconSize);
        save();
    }

    public static WaypointLabelStyle waypointLabelStyle() {
        load();
        return waypointLabelStyle;
    }

    public static void cycleWaypointLabelStyle() {
        load();
        waypointLabelStyle = next(waypointLabelStyle);
        save();
    }

    public static WaypointLabelScale waypointLabelScale() {
        load();
        return waypointLabelScale;
    }

    public static void cycleWaypointLabelScale() {
        load();
        waypointLabelScale = next(waypointLabelScale);
        save();
    }

    public static boolean waypointDistanceShown() {
        load();
        return waypointDistanceShown;
    }

    public static void toggleWaypointDistanceShown() {
        load();
        waypointDistanceShown = !waypointDistanceShown;
        save();
    }

    public static WaypointRange waypointRange() {
        load();
        return waypointRange;
    }

    public static void cycleWaypointRange() {
        load();
        waypointRange = next(waypointRange);
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
        cycleTimeOfDayMode(1);
    }

    public static boolean mobsEnabled() {
        load();
        for (RadarCategory category : radarCategories) {
            if (category.mob()) {
                return true;
            }
        }
        return false;
    }

    public static boolean radarShows(RadarCategory category) {
        load();
        return radarCategories.contains(category);
    }

    public static Set<RadarCategory> radarCategories() {
        load();
        return EnumSet.copyOf(radarCategories);
    }

    public static void toggleRadarCategory(RadarCategory category) {
        load();
        if (!radarCategories.remove(category)) {
            radarCategories.add(category);
        }
        save();
    }

    public static boolean overlayShown(MapOverlay overlay) {
        load();
        return mapOverlays.contains(overlay);
    }

    public static Set<MapOverlay> mapOverlays() {
        load();
        return mapOverlays.isEmpty() ? EnumSet.noneOf(MapOverlay.class) : EnumSet.copyOf(mapOverlays);
    }

    public static void toggleOverlay(MapOverlay overlay) {
        load();
        if (!mapOverlays.remove(overlay)) {
            mapOverlays.add(overlay);
        }
        save();
    }

    public static Long slimeSeed(String worldId) {
        load();
        return worldId == null ? null : slimeSeeds.get(worldId);
    }

    public static void setSlimeSeed(String worldId, Long seed) {
        load();
        if (!validWorldId(worldId)) return;
        if (seed == null) {
            slimeSeeds.remove(worldId);
        } else {
            slimeSeeds.put(worldId, seed);
        }
        save();
    }

    public static boolean otherDimensionWaypoints() {
        load();
        return otherDimensionWaypoints;
    }

    public static void toggleOtherDimensionWaypoints() {
        load();
        otherDimensionWaypoints = !otherDimensionWaypoints;
        save();
    }

    public static String linkedNether(String overworldId) {
        load();
        return overworldId == null ? null : netherLinks.get(overworldId);
    }

    public static String linkedOverworld(String netherId) {
        load();
        if (netherId == null) return null;
        for (Map.Entry<String, String> link : netherLinks.entrySet()) {
            if (link.getValue().equals(netherId)) {
                return link.getKey();
            }
        }
        return null;
    }

    public static void linkDimensions(String overworldId, String netherId) {
        load();
        if (!validWorldId(overworldId) || !validWorldId(netherId) || netherId.equals(netherLinks.get(overworldId))) {
            return;
        }
        netherLinks.values().removeIf(netherId::equals);
        netherLinks.put(overworldId, netherId);
        save();
    }

    public static String linkedWebMapWorld(String mapId, String worldName) {
        load();
        return mapId == null || worldName == null ? null : webMapLinks.get(mapId + "/" + worldName);
    }

    public static void linkWebMapWorld(String mapId, String worldName, String worldId) {
        load();
        if (mapId == null || worldName == null || !validWorldId(worldId)) {
            return;
        }
        String key = mapId + "/" + worldName;
        if (!WEB_MAP_KEY.matcher(key).matches() || worldId.equals(webMapLinks.get(key))) {
            return;
        }
        webMapLinks.values().removeIf(worldId::equals);
        webMapLinks.put(key, worldId);
        save();
    }

    public static RadarIcons radarIcons() {
        load();
        return radarIcons;
    }

    public static void cycleRadarIcons() {
        load();
        radarIcons = next(radarIcons);
        save();
    }

    public static boolean infoLineShown(MinimapInfoLine line) {
        load();
        return infoLines.contains(line);
    }

    public static Set<MinimapInfoLine> infoLines() {
        load();
        return EnumSet.copyOf(infoLines);
    }

    public static void toggleInfoLine(MinimapInfoLine line) {
        load();
        if (!infoLines.remove(line)) {
            infoLines.add(line);
        }
        save();
    }

    private static Set<RadarCategory> legacyRadarCategories(String mobsEnabled) {
        Set<RadarCategory> categories = EnumSet.copyOf(RadarCategory.defaults());
        if ("false".equalsIgnoreCase(mobsEnabled)) {
            categories.removeIf(RadarCategory::mob);
        }
        return categories;
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

    public static MinimapColors minimapColors() {
        load();
        return minimapColors;
    }

    public static void cycleMinimapColors() {
        load();
        minimapColors = next(minimapColors);
        save();
    }

    public static MinimapShape minimapShape() {
        load();
        return minimapShape;
    }

    public static void cycleMinimapShape() {
        load();
        minimapShape = next(minimapShape);
        save();
    }

    public static MinimapSize minimapSize() {
        load();
        return minimapSize;
    }

    public static void cycleMinimapSize() {
        load();
        minimapSize = next(minimapSize);
        save();
    }

    public static MinimapCorner minimapCorner() {
        load();
        return minimapCorner;
    }

    public static void cycleMinimapCorner() {
        load();
        minimapCorner = next(minimapCorner);
        save();
    }

    public static double minimapX() {
        load();
        return minimapX;
    }

    public static double minimapY() {
        load();
        return minimapY;
    }

    public static void setMinimapPosition(double x, double y, boolean persist) {
        load();
        minimapX = clamp(x, 0.0, 1.0);
        minimapY = clamp(y, 0.0, 1.0);
        minimapCorner = MinimapCorner.CUSTOM;
        if (persist) {
            save();
        }
    }

    public static void resetMinimapPosition() {
        load();
        minimapCorner = MinimapCorner.TOP_LEFT;
        minimapX = 0.0;
        minimapY = 0.0;
        save();
    }

    public static MinimapOpacity minimapOpacity() {
        load();
        return minimapOpacity;
    }

    public static void cycleMinimapOpacity() {
        load();
        minimapOpacity = next(minimapOpacity);
        save();
    }

    public static boolean minimapNightShading() {
        load();
        return minimapNightShading;
    }

    public static void toggleMinimapNightShading() {
        load();
        minimapNightShading = !minimapNightShading;
        save();
    }

    public static CaveMode caveMode() {
        load();
        return caveMode;
    }

    public static void cycleCaveMode() {
        load();
        caveMode = next(caveMode);
        save();
    }

    public static ScreenVisibility minimapScreenVisibility() {
        load();
        return minimapScreenVisibility;
    }

    public static void cycleMinimapScreenVisibility() {
        load();
        minimapScreenVisibility = next(minimapScreenVisibility);
        save();
    }

    public static double minimapPixelsPerBlock() {
        load();
        return MINIMAP_ZOOM_LEVELS[minimapZoom];
    }

    public static void zoomMinimap(int steps) {
        load();
        int zoom = clampZoomIndex(minimapZoom + steps);
        if (zoom == minimapZoom) {
            return;
        }
        minimapZoom = zoom;
        save();
    }

    public static void cycleMinimapZoom() {
        load();
        minimapZoom = (minimapZoom + 1) % MINIMAP_ZOOM_LEVELS.length;
        save();
    }

    static int clampZoomIndex(int index) {
        return Math.max(0, Math.min(MINIMAP_ZOOM_LEVELS.length - 1, index));
    }

    private static <E extends Enum<E>> E next(E value) {
        E[] values = value.getDeclaringClass().getEnumConstants();
        return values[(value.ordinal() + 1) % values.length];
    }

    static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        if (value != null) {
            try {
                return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {

            }
        }
        return fallback;
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

    public static boolean schematicsEnabled() {
        load();
        return schematicsEnabled;
    }

    public static void toggleSchematics() {
        load();
        schematicsEnabled = !schematicsEnabled;
        save();
    }

    public static boolean spatialGuiFlatMap() {
        load();
        return spatialGuiFlatMap;
    }

    public static void toggleSpatialGuiFlatMap() {
        load();
        spatialGuiFlatMap = !spatialGuiFlatMap;
        save();
    }

    public static boolean windowMap3d() {
        load();
        return windowMap3d;
    }

    public static void toggleWindowMap3d() {
        load();
        windowMap3d = !windowMap3d;
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

    public static boolean fogSuppressionActive() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean nether = minecraft != null && minecraft.level != null
                && "minecraft:the_nether".equals(minecraft.level.dimension().identifier().toString());
        return fogSuppressionMode().appliesIn(nether);
    }

    public static MapWeather mapWeather() {
        load();
        return mapWeather;
    }

    public static void cycleMapWeather(int direction) {
        load();
        MapWeather[] values = MapWeather.values();
        mapWeather = values[Math.floorMod(mapWeather.ordinal() + direction, values.length)];
        save();
    }

    public static MapWeather activeMapWeatherOverride() {
        if (!VoxyMapCameraController.isActive()) {
            return null;
        }
        MapWeather weather = mapWeather();
        return weather == MapWeather.AUTO ? null : weather;
    }

    public static void cycleTimeOfDayMode(int direction) {
        load();
        TimeOfDay[] values = TimeOfDay.values();
        timeOfDayMode = values[Math.floorMod(timeOfDayMode.ordinal() + direction, values.length)];
        save();
    }

    public static MapEntityModels mapEntityModels() {
        load();
        return mapEntityModels;
    }

    public static void cycleMapEntityModels() {
        load();
        MapEntityModels[] values = MapEntityModels.values();
        mapEntityModels = values[(mapEntityModels.ordinal() + 1) % values.length];
        save();
    }

    public static boolean voxyInWorld() {
        load();
        return voxyInWorld;
    }

    public static void toggleVoxyInWorld() {
        load();
        voxyInWorld = !voxyInWorld;
        save();
    }

    public static boolean playerHidden(java.util.UUID uuid) {
        load();
        return uuid != null && hiddenPlayers.contains(uuid.toString());
    }

    public static void togglePlayerHidden(java.util.UUID uuid) {
        load();
        if (uuid == null) {
            return;
        }
        String key = uuid.toString();
        if (!hiddenPlayers.remove(key)) {
            hiddenPlayers.add(key);
        }
        save();
    }

    public static boolean uiAnimations() {
        load();
        return uiAnimations;
    }

    public static void toggleUiAnimations() {
        load();
        uiAnimations = !uiAnimations;
        save();
    }

    public static boolean map2d() {
        load();
        return map2d;
    }

    public static void setMap2d(boolean enabled) {
        load();
        if (map2d != enabled) {
            map2d = enabled;
            save();
        }
    }

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

                }
            }
            return AUTO;
        }
    }

    public enum RadarIcons {
        HEADS("screen.voxymap.settings.radaricons.heads"),
        HEADS_ON_TAB("screen.voxymap.settings.radaricons.tab"),
        DOTS("screen.voxymap.settings.radaricons.dots");

        private final String translationKey;

        RadarIcons(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public enum WaypointLabelMode {
        OFF("screen.voxymap.settings.waypointlabels.off"),
        ICONS("screen.voxymap.settings.waypointlabels.icons"),
        AIMING("screen.voxymap.settings.waypointlabels.aiming"),
        ALWAYS("screen.voxymap.settings.waypointlabels.always");

        private final String translationKey;

        WaypointLabelMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public enum WaypointIconShape {
        SQUARE("screen.voxymap.settings.waypointshape.square"),
        CIRCLE("screen.voxymap.settings.waypointshape.circle"),
        DIAMOND("screen.voxymap.settings.waypointshape.diamond");

        private final String translationKey;

        WaypointIconShape(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public enum WaypointIconSize {
        SMALL("screen.voxymap.settings.waypointsize.small", 9),
        MEDIUM("screen.voxymap.settings.waypointsize.medium", 11),
        LARGE("screen.voxymap.settings.waypointsize.large", 15),
        HUGE("screen.voxymap.settings.waypointsize.huge", 19);

        private final String translationKey;
        private final int pixels;

        WaypointIconSize(String translationKey, int pixels) {
            this.translationKey = translationKey;
            this.pixels = pixels;
        }

        public String translationKey() {
            return translationKey;
        }

        public int pixels() {
            return pixels;
        }
    }

    public enum WaypointLabelStyle {
        BOX("screen.voxymap.settings.waypointlabelstyle.box", 0xC0),
        CLEAR("screen.voxymap.settings.waypointlabelstyle.clear", 0x60),
        TEXT("screen.voxymap.settings.waypointlabelstyle.text", 0);

        private final String translationKey;
        private final int backgroundAlpha;

        WaypointLabelStyle(String translationKey, int backgroundAlpha) {
            this.translationKey = translationKey;
            this.backgroundAlpha = backgroundAlpha;
        }

        public String translationKey() {
            return translationKey;
        }

        public int backgroundAlpha() {
            return backgroundAlpha;
        }
    }

    public enum WaypointLabelScale {
        SMALL("75%", 0.75f),
        NORMAL("100%", 1.0f),
        LARGE("150%", 1.5f),
        HUGE("200%", 2.0f);

        private final String label;
        private final float scale;

        WaypointLabelScale(String label, float scale) {
            this.label = label;
            this.scale = scale;
        }

        public String label() {
            return label;
        }

        public float scale() {
            return scale;
        }
    }

    public enum WaypointRange {
        UNLIMITED(0),
        BLOCKS_250(250),
        BLOCKS_500(500),
        BLOCKS_1000(1000),
        BLOCKS_2500(2500),
        BLOCKS_5000(5000);

        private final int blocks;

        WaypointRange(int blocks) {
            this.blocks = blocks;
        }

        public int blocks() {
            return blocks;
        }

        public boolean unlimited() {
            return blocks <= 0;
        }
    }

    public enum MinimapColors {
        TEXTURES("screen.voxymap.settings.minimapcolors.textures"),
        CLASSIC("screen.voxymap.settings.minimapcolors.classic");

        private final String translationKey;

        MinimapColors(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public enum MinimapOpacity {
        FULL("screen.voxymap.settings.minimapopacity.full", 1.0f),
        HIGH("screen.voxymap.settings.minimapopacity.high", 0.85f),
        MEDIUM("screen.voxymap.settings.minimapopacity.medium", 0.7f),
        LOW("screen.voxymap.settings.minimapopacity.low", 0.5f);

        private final String translationKey;
        private final float alpha;

        MinimapOpacity(String translationKey, float alpha) {
            this.translationKey = translationKey;
            this.alpha = alpha;
        }

        public String translationKey() {
            return translationKey;
        }

        public float alpha() {
            return alpha;
        }
    }

    public enum CaveMode {
        AUTO("screen.voxymap.settings.cavemode.auto"),
        OFF("screen.voxymap.settings.cavemode.off"),
        ALWAYS("screen.voxymap.settings.cavemode.always");

        private final String translationKey;

        CaveMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public enum ScreenVisibility {
        ALWAYS("screen.voxymap.settings.minimapscreens.always"),
        HIDE_IN_INVENTORIES("screen.voxymap.settings.minimapscreens.inventories"),
        HIDE_IN_ALL_SCREENS("screen.voxymap.settings.minimapscreens.all");

        private final String translationKey;

        ScreenVisibility(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public enum MinimapShape {
        SQUARE("screen.voxymap.settings.minimapshape.square"),
        CIRCLE("screen.voxymap.settings.minimapshape.circle");

        private final String translationKey;

        MinimapShape(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public enum MinimapSize {
        SMALL("screen.voxymap.settings.minimapsize.small", 96),
        MEDIUM("screen.voxymap.settings.minimapsize.medium", 128),
        LARGE("screen.voxymap.settings.minimapsize.large", 160),
        HUGE("screen.voxymap.settings.minimapsize.huge", 200);

        private final String translationKey;
        private final int pixels;

        MinimapSize(String translationKey, int pixels) {
            this.translationKey = translationKey;
            this.pixels = pixels;
        }

        public String translationKey() {
            return translationKey;
        }

        public int pixels() {
            return pixels;
        }
    }

    public enum MinimapCorner {
        TOP_LEFT("screen.voxymap.settings.minimapcorner.top_left", false, false),
        TOP_RIGHT("screen.voxymap.settings.minimapcorner.top_right", true, false),
        BOTTOM_RIGHT("screen.voxymap.settings.minimapcorner.bottom_right", true, true),
        CUSTOM("screen.voxymap.settings.minimapcorner.custom", false, false);

        private final String translationKey;
        private final boolean right;
        private final boolean bottom;

        MinimapCorner(String translationKey, boolean right, boolean bottom) {
            this.translationKey = translationKey;
            this.right = right;
            this.bottom = bottom;
        }

        public String translationKey() {
            return translationKey;
        }

        public boolean right() {
            return right;
        }

        public boolean bottom() {
            return bottom;
        }
    }

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

                }
            }
            return NO_INTERFACE;
        }
    }

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

        public boolean appliesIn(boolean nether) {
            return switch (this) {
                case ALWAYS -> true;
                case NETHER_ONLY -> nether;
                case OFF -> false;
            };
        }

        static FogSuppression parse(String mode, String legacy) {
            if (mode != null) {
                try {
                    return valueOf(mode.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {

                }
            }
            if (legacy != null) {
                return "false".equalsIgnoreCase(legacy.trim()) ? OFF : ALWAYS;
            }
            return ALWAYS;
        }
    }

    public enum MapWeather {
        CLEAR("screen.voxymap.settings.weather.clear", 0.0f, 0.0f),
        RAIN("screen.voxymap.settings.weather.rain", 1.0f, 0.0f),
        THUNDER("screen.voxymap.settings.weather.thunder", 1.0f, 1.0f),
        AUTO("screen.voxymap.settings.weather.auto", -1.0f, -1.0f);

        private final String translationKey;
        private final float rain;
        private final float thunder;

        MapWeather(String translationKey, float rain, float thunder) {
            this.translationKey = translationKey;
            this.rain = rain;
            this.thunder = thunder;
        }

        public String translationKey() {
            return translationKey;
        }

        public float rain() {
            return rain;
        }

        public float thunder() {
            return thunder;
        }
    }

    public enum MapEntityModels {
        PLAYERS_AND_MOBS("screen.voxymap.settings.entitymodels.all", true, true),
        PLAYERS("screen.voxymap.settings.entitymodels.players", true, false),
        OFF("screen.voxymap.settings.off", false, false);

        private final String translationKey;
        private final boolean players;
        private final boolean mobs;

        MapEntityModels(String translationKey, boolean players, boolean mobs) {
            this.translationKey = translationKey;
            this.players = players;
            this.mobs = mobs;
        }

        public String translationKey() {
            return translationKey;
        }

        public boolean players() {
            return players;
        }

        public boolean mobs() {
            return mobs;
        }
    }

    public enum TimeOfDay {
        AUTO("screen.voxymap.settings.timeofday.auto", -1L, false),
        MORNING("screen.voxymap.settings.timeofday.morning", 1000L, false),
        DAY("screen.voxymap.settings.timeofday.day", 6000L, false),
        EVENING("screen.voxymap.settings.timeofday.evening", 11800L, false),
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

        public long clockTicks() {
            return clockTicks;
        }

        public boolean isDark() {
            return dark;
        }

        public int skyDarken() {
            return dark ? 11 : 0;
        }

        private static TimeOfDay parse(String value) {
            if (value != null) {
                try {
                    return valueOf(value.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {

                }
            }
            return AUTO;
        }
    }

    private static Properties readProperties(Path path, boolean logFailure) {
        if (!Files.exists(path)) return null;
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException | IllegalArgumentException exception) {
            if (logFailure) {
                VoxyMapClient.LOGGER.warn("Could not read settings from {}", path, exception);
            }
            return null;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
