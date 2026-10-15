package com.voxymap.client.gui;

import com.voxymap.client.integration.RemotePlayerMarkers;
import com.voxymap.client.map.MapOverlay;
import com.voxymap.client.map.MinimapRenderer;
import com.voxymap.client.map.MinimapInfoLine;
import com.voxymap.client.map.RadarCategory;
import com.voxymap.client.map.VoxyMapSettings;
import com.voxymap.client.map.WaypointDisplay;
import com.voxymap.client.map.WaypointManager;
import com.voxymap.client.map.WaypointSyncSettings;
import com.voxymap.client.gui.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public final class SettingsModel {
    public interface Host {
        String worldId();

        void openMinimapLayout();

        Component actionValue(SettingsCatalog.Entry entry);

        void runAction(SettingsCatalog.Entry entry);
    }

    public sealed interface Control permits Toggle, Cycle, Slider, Text, Status, Action {
    }

    public record Toggle(BooleanSupplier value, Runnable flip) implements Control {
    }

    public record Cycle(Supplier<Component> value, IntConsumer step) implements Control {
    }

    public record Slider(java.util.function.DoubleSupplier fraction, Supplier<Component> value,
                         java.util.function.DoubleConsumer apply, double keyStep)
            implements Control {
    }

    public record Text(Supplier<String> value, Consumer<String> commit, boolean secret, int maxLength)
            implements Control {
    }

    public record Status(Supplier<Component> value, Supplier<Integer> color) implements Control {
    }

    public record Action(Supplier<Component> label, Runnable run) implements Control {
    }

    public record Reset(BooleanSupplier isDefault, Runnable reset) {
    }

    private static final Map<SettingsCatalog.Entry, String> KEYS = new EnumMap<>(SettingsCatalog.Entry.class);

    static {
        key(SettingsCatalog.Entry.PAUSE, "pauseSingleplayer");
        key(SettingsCatalog.Entry.SPEED, "cameraSpeedMultiplier");
        key(SettingsCatalog.Entry.TIME_OF_DAY, "timeOfDayMode");
        key(SettingsCatalog.Entry.MAP_WEATHER, "mapWeather");
        key(SettingsCatalog.Entry.VOXY_IN_WORLD, "voxyInWorld");
        key(SettingsCatalog.Entry.UI_ANIMATIONS, "uiAnimations");
        key(SettingsCatalog.Entry.FOG_SUPPRESSION, "fogSuppressionMode");
        key(SettingsCatalog.Entry.SCREENSHOT, "screenshotContent");
        key(SettingsCatalog.Entry.SPATIAL_GUI_FLAT_MAP, "spatialGuiFlatMap");
        key(SettingsCatalog.Entry.SPATIAL_GUI_3D, "windowMap3d");
        key(SettingsCatalog.Entry.WAYPOINTS, "waypointsEnabled");
        key(SettingsCatalog.Entry.WAYPOINTS_OTHER_DIMENSION, "otherDimensionWaypoints");
        key(SettingsCatalog.Entry.DEATH_POINTS, "deathPointsEnabled");
        key(SettingsCatalog.Entry.VIEW_DIRECTION, "viewDirectionEnabled");
        key(SettingsCatalog.Entry.ENTITY_MODELS, "mapEntityModels");
        key(SettingsCatalog.Entry.VANILLASQUAD_PLAYERS, "vanillaSquadPlayersEnabled");
        key(SettingsCatalog.Entry.VANILLASQUAD_FLAGS, "vanillaSquadFlagsEnabled");
        key(SettingsCatalog.Entry.SCHEMATICS, "schematicsEnabled");
        key(SettingsCatalog.Entry.NEW_WAYPOINT_DISPLAY, "newWaypointDisplay");
        key(SettingsCatalog.Entry.WAYPOINT_SHAPE, "waypointIconShape");
        key(SettingsCatalog.Entry.WAYPOINT_SIZE, "waypointIconSize");
        key(SettingsCatalog.Entry.WAYPOINT_LABELS, "waypointLabelMode");
        key(SettingsCatalog.Entry.WAYPOINT_LABEL_STYLE, "waypointLabelStyle");
        key(SettingsCatalog.Entry.WAYPOINT_LABEL_SCALE, "waypointLabelScale");
        key(SettingsCatalog.Entry.WAYPOINT_DISTANCE, "waypointDistanceShown");
        key(SettingsCatalog.Entry.WAYPOINT_RANGE, "waypointRange");
        key(SettingsCatalog.Entry.WAYPOINT_BEAMS, "waypointBeams");
        key(SettingsCatalog.Entry.MINIMAP, "minimapEnabled");
        key(SettingsCatalog.Entry.MINIMAP_ROTATE, "minimapRotates");
        key(SettingsCatalog.Entry.MINIMAP_COLORS, "minimapColors");
        key(SettingsCatalog.Entry.MINIMAP_SHAPE, "minimapShape");
        key(SettingsCatalog.Entry.MINIMAP_SIZE, "minimapSize");
        key(SettingsCatalog.Entry.MINIMAP_CORNER, "minimapCorner");
        key(SettingsCatalog.Entry.MINIMAP_ZOOM, "minimapZoom");
        key(SettingsCatalog.Entry.MINIMAP_OPACITY, "minimapOpacity");
        key(SettingsCatalog.Entry.MINIMAP_NIGHT, "minimapNightShading");
        key(SettingsCatalog.Entry.MINIMAP_CAVE, "caveMode");
        key(SettingsCatalog.Entry.MINIMAP_SCREENS, "minimapScreenVisibility");
        key(SettingsCatalog.Entry.CHUNK_GRID, "chunkGridEnabled");
        key(SettingsCatalog.Entry.RADAR_ICONS, "radarIcons");
    }

    private SettingsModel() {
    }

    private static void key(SettingsCatalog.Entry entry, String property) {
        KEYS.put(entry, property);
    }

    static String propertyKey(SettingsCatalog.Entry entry) {
        return KEYS.get(entry);
    }

    public static Reset reset(SettingsCatalog.Entry entry) {
        String property = KEYS.get(entry);
        if (property != null) {
            return new Reset(() -> VoxyMapSettings.isDefault(property), () -> VoxyMapSettings.reset(List.of(property)));
        }
        if (entry.infoLine() != null) {
            MinimapInfoLine line = entry.infoLine();
            boolean byDefault = MinimapInfoLine.defaults().contains(line);
            return new Reset(() -> VoxyMapSettings.infoLineShown(line) == byDefault, () -> {
                if (VoxyMapSettings.infoLineShown(line) != byDefault) {
                    VoxyMapSettings.toggleInfoLine(line);
                }
            });
        }
        if (entry.radarCategory() != null) {
            RadarCategory category = entry.radarCategory();
            boolean byDefault = RadarCategory.defaults().contains(category);
            return new Reset(() -> VoxyMapSettings.radarShows(category) == byDefault, () -> {
                if (VoxyMapSettings.radarShows(category) != byDefault) {
                    VoxyMapSettings.toggleRadarCategory(category);
                }
            });
        }
        if (entry.mapOverlay() != null) {
            MapOverlay overlay = entry.mapOverlay();
            return new Reset(() -> !VoxyMapSettings.overlayShown(overlay), () -> {
                if (VoxyMapSettings.overlayShown(overlay)) {
                    VoxyMapSettings.toggleOverlay(overlay);
                }
            });
        }
        return null;
    }

    public static void resetAll(List<SettingsCatalog.Entry> entries) {
        VoxyMapSettings.batch(() -> {
            List<String> properties = new ArrayList<>();
            for (SettingsCatalog.Entry entry : entries) {
                String property = KEYS.get(entry);
                if (property != null) {
                    properties.add(property);
                } else {
                    Reset reset = reset(entry);
                    if (reset != null && !reset.isDefault().getAsBoolean()) {
                        reset.reset().run();
                    }
                }
            }
            VoxyMapSettings.reset(properties);
        });
    }

    public static boolean changed(SettingsCatalog.Entry entry) {
        Reset reset = reset(entry);
        return reset != null && !reset.isDefault().getAsBoolean();
    }

    static void repeat(Runnable next, int size, int direction) {
        int times = direction >= 0 ? 1 : size - 1;
        for (int i = 0; i < times; i++) {
            next.run();
        }
    }

    private static Cycle cycle(Supplier<Component> value, Runnable next, int size) {
        return new Cycle(value, direction -> VoxyMapSettings.batch(() -> repeat(next, size, direction)));
    }

    private static Component tr(String key) {
        return Component.translatable(key);
    }

    public static Control control(SettingsCatalog.Entry entry, Host host) {
        return switch (entry) {
            case PAUSE -> new Toggle(VoxyMapSettings::pauseSingleplayer, VoxyMapSettings::togglePauseSingleplayer);
            case SPEED -> new Slider(SettingsModel::speedFraction,
                    () -> Component.literal(String.format(Locale.ROOT, "%.2fx", VoxyMapSettings.cameraSpeedMultiplier())),
                    fraction -> VoxyMapSettings.setCameraSpeed(VoxyMapSettings.CAMERA_SPEED_MIN
                            + Math.round(fraction * speedSteps()) * VoxyMapSettings.CAMERA_SPEED_STEP),
                    1.0 / speedSteps());
            case TIME_OF_DAY -> new Cycle(() -> tr(VoxyMapSettings.timeOfDayMode().translationKey()),
                    VoxyMapSettings::cycleTimeOfDayMode);
            case MAP_WEATHER -> new Cycle(() -> tr(VoxyMapSettings.mapWeather().translationKey()),
                    VoxyMapSettings::cycleMapWeather);
            case VOXY_IN_WORLD -> new Toggle(VoxyMapSettings::voxyInWorld, VoxyMapSettings::toggleVoxyInWorld);
            case UI_ANIMATIONS -> new Toggle(VoxyMapSettings::uiAnimations, VoxyMapSettings::toggleUiAnimations);
            case FOG_SUPPRESSION -> cycle(() -> tr(VoxyMapSettings.fogSuppressionMode().translationKey()),
                    VoxyMapSettings::cycleFogSuppression, VoxyMapSettings.FogSuppression.values().length);
            case SCREENSHOT -> cycle(() -> tr(VoxyMapSettings.screenshotContent().translationKey()),
                    VoxyMapSettings::cycleScreenshotContent, VoxyMapSettings.ScreenshotContent.values().length);
            case SPATIAL_GUI_FLAT_MAP -> new Toggle(VoxyMapSettings::spatialGuiFlatMap, VoxyMapSettings::toggleSpatialGuiFlatMap);
            case SPATIAL_GUI_3D -> new Toggle(VoxyMapSettings::windowMap3d, VoxyMapSettings::toggleWindowMap3d);
            case WAYPOINTS -> new Toggle(VoxyMapSettings::waypointsEnabled, VoxyMapSettings::toggleWaypointsVisible);
            case WAYPOINTS_OTHER_DIMENSION -> new Toggle(VoxyMapSettings::otherDimensionWaypoints,
                    VoxyMapSettings::toggleOtherDimensionWaypoints);
            case DEATH_POINTS -> new Toggle(VoxyMapSettings::deathPointsEnabled, VoxyMapSettings::toggleDeathPoints);
            case VIEW_DIRECTION -> new Toggle(VoxyMapSettings::viewDirectionEnabled, VoxyMapSettings::toggleViewDirection);
            case ENTITY_MODELS -> cycle(() -> tr(VoxyMapSettings.mapEntityModels().translationKey()),
                    VoxyMapSettings::cycleMapEntityModels, VoxyMapSettings.MapEntityModels.values().length);
            case VANILLASQUAD_PLAYERS -> new Toggle(VoxyMapSettings::vanillaSquadPlayersEnabled,
                    VoxyMapSettings::toggleVanillaSquadPlayers);
            case VANILLASQUAD_FLAGS -> new Toggle(VoxyMapSettings::vanillaSquadFlagsEnabled,
                    VoxyMapSettings::toggleVanillaSquadFlags);
            case VANILLASQUAD_MAP -> cycle(() -> vanillaSquadMapValue(voxyWorldId()),
                    () -> VoxyMapSettings.cycleVanillaSquadMap(voxyWorldId()),
                    VoxyMapSettings.VanillaSquadMap.values().length);
            case SCHEMATICS -> new Toggle(VoxyMapSettings::schematicsEnabled, VoxyMapSettings::toggleSchematics);
            case NEW_WAYPOINT_DISPLAY -> cycle(() -> tr(VoxyMapSettings.newWaypointDisplay().translationKey()),
                    VoxyMapSettings::cycleNewWaypointDisplay, WaypointDisplay.values().length);
            case WAYPOINT_SHAPE -> cycle(() -> tr(VoxyMapSettings.waypointIconShape().translationKey()),
                    VoxyMapSettings::cycleWaypointIconShape, VoxyMapSettings.WaypointIconShape.values().length);
            case WAYPOINT_SIZE -> cycle(() -> tr(VoxyMapSettings.waypointIconSize().translationKey()),
                    VoxyMapSettings::cycleWaypointIconSize, VoxyMapSettings.WaypointIconSize.values().length);
            case WAYPOINT_LABELS -> cycle(() -> tr(VoxyMapSettings.waypointLabelMode().translationKey()),
                    VoxyMapSettings::cycleWaypointLabelMode, VoxyMapSettings.WaypointLabelMode.values().length);
            case WAYPOINT_LABEL_STYLE -> cycle(() -> tr(VoxyMapSettings.waypointLabelStyle().translationKey()),
                    VoxyMapSettings::cycleWaypointLabelStyle, VoxyMapSettings.WaypointLabelStyle.values().length);
            case WAYPOINT_LABEL_SCALE -> cycle(() -> Component.literal(VoxyMapSettings.waypointLabelScale().label()),
                    VoxyMapSettings::cycleWaypointLabelScale, VoxyMapSettings.WaypointLabelScale.values().length);
            case WAYPOINT_DISTANCE -> new Toggle(VoxyMapSettings::waypointDistanceShown,
                    VoxyMapSettings::toggleWaypointDistanceShown);
            case WAYPOINT_RANGE -> cycle(() -> Component.literal(SettingsValues.waypointRangeLabel()),
                    VoxyMapSettings::cycleWaypointRange, VoxyMapSettings.WaypointRange.values().length);
            case WAYPOINT_BEAMS -> new Toggle(VoxyMapSettings::waypointBeams, VoxyMapSettings::toggleWaypointBeams);
            case MINIMAP -> new Toggle(VoxyMapSettings::minimapEnabled, VoxyMapSettings::toggleMinimap);
            case MINIMAP_ROTATE -> new Toggle(VoxyMapSettings::minimapRotates, VoxyMapSettings::toggleMinimapRotates);
            case MINIMAP_COLORS -> cycle(() -> tr(VoxyMapSettings.minimapColors().translationKey()),
                    VoxyMapSettings::cycleMinimapColors, VoxyMapSettings.MinimapColors.values().length);
            case MINIMAP_SHAPE -> cycle(() -> tr(VoxyMapSettings.minimapShape().translationKey()),
                    VoxyMapSettings::cycleMinimapShape, VoxyMapSettings.MinimapShape.values().length);
            case MINIMAP_SIZE -> cycle(() -> tr(VoxyMapSettings.minimapSize().translationKey()),
                    VoxyMapSettings::cycleMinimapSize, VoxyMapSettings.MinimapSize.values().length);
            case MINIMAP_CORNER -> cycle(() -> tr(VoxyMapSettings.minimapCorner().translationKey()),
                    VoxyMapSettings::cycleMinimapCorner, VoxyMapSettings.MinimapCorner.values().length);
            case MINIMAP_ZOOM -> new Cycle(() -> Component.literal(SettingsValues.minimapZoomLabel()),
                    VoxyMapSettings::zoomMinimap);
            case MINIMAP_MOVE -> new Action(() -> tr("screen.voxymap.settings.minimapmove.open"), host::openMinimapLayout);
            case MINIMAP_OPACITY -> cycle(() -> tr(VoxyMapSettings.minimapOpacity().translationKey()),
                    VoxyMapSettings::cycleMinimapOpacity, VoxyMapSettings.MinimapOpacity.values().length);
            case MINIMAP_NIGHT -> new Toggle(VoxyMapSettings::minimapNightShading, VoxyMapSettings::toggleMinimapNightShading);
            case MINIMAP_CAVE -> cycle(() -> tr(VoxyMapSettings.caveMode().translationKey()),
                    VoxyMapSettings::cycleCaveMode, VoxyMapSettings.CaveMode.values().length);
            case MINIMAP_SCREENS -> cycle(() -> tr(VoxyMapSettings.minimapScreenVisibility().translationKey()),
                    VoxyMapSettings::cycleMinimapScreenVisibility, VoxyMapSettings.ScreenVisibility.values().length);
            case CHUNK_GRID -> new Toggle(VoxyMapSettings::chunkGridEnabled, VoxyMapSettings::toggleChunkGrid);
            case INFO_COORDINATES, INFO_NETHER_COORDINATES, INFO_BIOME, INFO_LIGHT, INFO_TIME, INFO_CHUNK, INFO_FACING ->
                    new Toggle(() -> VoxyMapSettings.infoLineShown(entry.infoLine()),
                            () -> VoxyMapSettings.toggleInfoLine(entry.infoLine()));
            case RADAR_HOSTILE, RADAR_NEUTRAL, RADAR_PASSIVE, RADAR_ITEMS, RADAR_PLAYERS ->
                    new Toggle(() -> VoxyMapSettings.radarShows(entry.radarCategory()),
                            () -> VoxyMapSettings.toggleRadarCategory(entry.radarCategory()));
            case RADAR_ICONS -> cycle(() -> tr(VoxyMapSettings.radarIcons().translationKey()),
                    VoxyMapSettings::cycleRadarIcons, VoxyMapSettings.RadarIcons.values().length);
            case OVERLAY_SLIME -> SettingsValues.slimeSeedMissing()
                    ? new Status(() -> tr("screen.voxymap.settings.overlay.slime.no_seed"), () -> UiTheme.WARNING)
                    : overlayToggle(entry.mapOverlay());
            case OVERLAY_LIGHT, OVERLAY_BIOMES -> overlayToggle(entry.mapOverlay());
            case SYNC_ADDRESS -> new Text(() -> nullToEmpty(WaypointSyncSettings.address(host.worldId())),
                    value -> WaypointSyncSettings.setAddress(host.worldId(), value), false, 128);
            case SYNC_PASSWORD -> new Text(() -> nullToEmpty(WaypointSyncSettings.password(host.worldId())),
                    value -> WaypointSyncSettings.setPassword(host.worldId(), value), true, 128);
            case SYNC_STATUS -> new Status(() -> syncStatusText(host.worldId()), () -> syncStatusColor(host.worldId()));
            case MAP_SHARE, MAP_RECEIVE -> new Action(() -> host.actionValue(entry), () -> host.runAction(entry));
            case DIAGNOSTICS -> new Status(() -> tr(SettingsValues.diagnosticsKey()),
                    () -> "screen.voxymap.diagnostics.ok".equals(SettingsValues.diagnosticsKey())
                            ? UiTheme.SUCCESS : UiTheme.WARNING);
        };
    }

    private static Control overlayToggle(MapOverlay overlay) {
        return new Toggle(() -> VoxyMapSettings.overlayShown(overlay), () -> VoxyMapSettings.toggleOverlay(overlay));
    }

    private static int speedSteps() {
        return (int) Math.round((VoxyMapSettings.CAMERA_SPEED_MAX - VoxyMapSettings.CAMERA_SPEED_MIN)
                / VoxyMapSettings.CAMERA_SPEED_STEP);
    }

    private static double speedFraction() {
        return (VoxyMapSettings.cameraSpeedMultiplier() - VoxyMapSettings.CAMERA_SPEED_MIN)
                / (VoxyMapSettings.CAMERA_SPEED_MAX - VoxyMapSettings.CAMERA_SPEED_MIN);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String voxyWorldId() {
        return MinimapRenderer.currentVoxyWorldId(Minecraft.getInstance());
    }

    private static Component vanillaSquadMapValue(String worldId) {
        VoxyMapSettings.VanillaSquadMap map = VoxyMapSettings.vanillaSquadMap(worldId);
        Component name = tr(map.translationKey());
        return map == VoxyMapSettings.VanillaSquadMap.AUTO
                ? Component.empty().append(name).append(" → " + RemotePlayerMarkers.activeMapId(worldId))
                : name;
    }

    static Component syncStatusText(String worldId) {
        WaypointManager.SyncStatus status = worldId == null ? WaypointManager.SyncStatus.DISABLED
                : WaypointManager.syncStatus(worldId);
        return tr(switch (status) {
            case DISABLED -> "screen.voxymap.settings.sync.status.disabled";
            case READ_ONLY -> "screen.voxymap.settings.sync.status.read_only";
            case SYNCING -> "screen.voxymap.settings.sync.status.syncing";
            case OK -> "screen.voxymap.settings.sync.status.ok";
            case ERROR -> "screen.voxymap.settings.sync.status.error";
        });
    }

    static int syncStatusColor(String worldId) {
        WaypointManager.SyncStatus status = worldId == null ? WaypointManager.SyncStatus.DISABLED
                : WaypointManager.syncStatus(worldId);
        return switch (status) {
            case OK -> UiTheme.SUCCESS;
            case READ_ONLY -> UiTheme.WARNING;
            case SYNCING -> UiTheme.ACCENT_BRIGHT;
            case ERROR -> UiTheme.DANGER;
            case DISABLED -> UiTheme.TEXT_MUTED;
        };
    }
}
