package com.voxymap.client.gui;

import com.voxymap.client.map.MapOverlay;
import com.voxymap.client.map.MinimapInfoLine;
import com.voxymap.client.map.RadarCategory;

import java.util.ArrayList;
import java.util.List;

public final class SettingsCatalog {
    private SettingsCatalog() {
    }

    public enum Group {
        GENERAL("screen.voxymap.settings.group.general"),
        LAYERS("screen.voxymap.settings.group.layers"),
        WAYPOINT_LOOK("screen.voxymap.settings.group.waypointlook"),
        MINIMAP("screen.voxymap.settings.group.minimap"),
        INFO("screen.voxymap.settings.group.info"),
        RADAR("screen.voxymap.settings.group.radar"),
        OVERLAYS("screen.voxymap.settings.group.overlays"),
        SYNC("screen.voxymap.settings.group.sync"),
        DIAGNOSTICS("screen.voxymap.settings.group.diagnostics");

        private final String titleKey;

        Group(String titleKey) {
            this.titleKey = titleKey;
        }

        public String titleKey() {
            return titleKey;
        }
    }

    public enum Availability {

        EVERYWHERE,

        IN_WORLD,

        IN_MAP
    }

    public enum Entry {
        PAUSE(Group.GENERAL, "screen.voxymap.settings.pause", Availability.EVERYWHERE, false),
        SPEED(Group.GENERAL, "screen.voxymap.settings.speed", Availability.EVERYWHERE, false),
        TIME_OF_DAY(Group.GENERAL, "screen.voxymap.settings.timeofday", Availability.EVERYWHERE, false),
        MAP_WEATHER(Group.GENERAL, "screen.voxymap.settings.weather", Availability.EVERYWHERE, false),
        VOXY_IN_WORLD(Group.GENERAL, "screen.voxymap.settings.voxyinworld", Availability.EVERYWHERE, false),
        UI_ANIMATIONS(Group.GENERAL, "screen.voxymap.settings.uianimations", Availability.EVERYWHERE, false),
        FOG_SUPPRESSION(Group.GENERAL, "screen.voxymap.settings.fogsuppression", Availability.EVERYWHERE, false),
        SCREENSHOT(Group.GENERAL, "screen.voxymap.settings.screenshot", Availability.EVERYWHERE, false),
        SPATIAL_GUI_FLAT_MAP(Group.GENERAL, "screen.voxymap.settings.spatialgui", Availability.EVERYWHERE, false),
        SPATIAL_GUI_3D(Group.GENERAL, "screen.voxymap.settings.spatialgui3d", Availability.EVERYWHERE, false),

        WAYPOINTS(Group.LAYERS, "screen.voxymap.settings.waypoints", Availability.EVERYWHERE, false),
        WAYPOINTS_OTHER_DIMENSION(Group.LAYERS, "screen.voxymap.settings.otherdimension", Availability.EVERYWHERE, false),
        DEATH_POINTS(Group.LAYERS, "screen.voxymap.settings.deathpoints", Availability.EVERYWHERE, false),
        VIEW_DIRECTION(Group.LAYERS, "screen.voxymap.settings.viewdirection", Availability.EVERYWHERE, false),
        ENTITY_MODELS(Group.LAYERS, "screen.voxymap.settings.entitymodels", Availability.EVERYWHERE, false),
        VANILLASQUAD_PLAYERS(Group.LAYERS, "screen.voxymap.settings.vanillasquad", Availability.EVERYWHERE, true),
        VANILLASQUAD_FLAGS(Group.LAYERS, "screen.voxymap.settings.vanillasquadflags", Availability.EVERYWHERE, true),
        VANILLASQUAD_MAP(Group.LAYERS, "screen.voxymap.settings.vanillasquadmap", Availability.IN_WORLD, true),
        SCHEMATICS(Group.LAYERS, "screen.voxymap.settings.schematics", Availability.EVERYWHERE, false),

        NEW_WAYPOINT_DISPLAY(Group.WAYPOINT_LOOK, "screen.voxymap.settings.newwaypointdisplay", Availability.EVERYWHERE, false),
        WAYPOINT_SHAPE(Group.WAYPOINT_LOOK, "screen.voxymap.settings.waypointshape", Availability.EVERYWHERE, false),
        WAYPOINT_SIZE(Group.WAYPOINT_LOOK, "screen.voxymap.settings.waypointsize", Availability.EVERYWHERE, false),
        WAYPOINT_LABELS(Group.WAYPOINT_LOOK, "screen.voxymap.settings.waypointlabels", Availability.EVERYWHERE, false),
        WAYPOINT_LABEL_STYLE(Group.WAYPOINT_LOOK, "screen.voxymap.settings.waypointlabelstyle", Availability.EVERYWHERE, false),
        WAYPOINT_LABEL_SCALE(Group.WAYPOINT_LOOK, "screen.voxymap.settings.waypointlabelscale", Availability.EVERYWHERE, false),
        WAYPOINT_DISTANCE(Group.WAYPOINT_LOOK, "screen.voxymap.settings.waypointdistance", Availability.EVERYWHERE, false),
        WAYPOINT_RANGE(Group.WAYPOINT_LOOK, "screen.voxymap.settings.waypointrange", Availability.EVERYWHERE, false),
        WAYPOINT_BEAMS(Group.WAYPOINT_LOOK, "screen.voxymap.settings.waypointbeams", Availability.EVERYWHERE, false),

        MINIMAP(Group.MINIMAP, "screen.voxymap.settings.minimap", Availability.EVERYWHERE, false),
        MINIMAP_ROTATE(Group.MINIMAP, "screen.voxymap.settings.minimaprotate", Availability.EVERYWHERE, false),
        MINIMAP_COLORS(Group.MINIMAP, "screen.voxymap.settings.minimapcolors", Availability.EVERYWHERE, false),
        MINIMAP_SHAPE(Group.MINIMAP, "screen.voxymap.settings.minimapshape", Availability.EVERYWHERE, false),
        MINIMAP_SIZE(Group.MINIMAP, "screen.voxymap.settings.minimapsize", Availability.EVERYWHERE, false),
        MINIMAP_CORNER(Group.MINIMAP, "screen.voxymap.settings.minimapcorner", Availability.EVERYWHERE, false),
        MINIMAP_ZOOM(Group.MINIMAP, "screen.voxymap.settings.minimapzoom", Availability.EVERYWHERE, false),
        MINIMAP_MOVE(Group.MINIMAP, "screen.voxymap.settings.minimapmove", Availability.IN_WORLD, false),
        MINIMAP_OPACITY(Group.MINIMAP, "screen.voxymap.settings.minimapopacity", Availability.EVERYWHERE, false),
        MINIMAP_NIGHT(Group.MINIMAP, "screen.voxymap.settings.minimapnight", Availability.EVERYWHERE, false),
        MINIMAP_CAVE(Group.MINIMAP, "screen.voxymap.settings.cavemode", Availability.EVERYWHERE, false),
        MINIMAP_SCREENS(Group.MINIMAP, "screen.voxymap.settings.minimapscreens", Availability.EVERYWHERE, false),
        CHUNK_GRID(Group.MINIMAP, "screen.voxymap.settings.chunkgrid", Availability.EVERYWHERE, false),

        INFO_COORDINATES(Group.INFO, "screen.voxymap.settings.info.coordinates", MinimapInfoLine.COORDINATES),
        INFO_NETHER_COORDINATES(Group.INFO, "screen.voxymap.settings.info.nether_coordinates", MinimapInfoLine.NETHER_COORDINATES),
        INFO_BIOME(Group.INFO, "screen.voxymap.settings.info.biome", MinimapInfoLine.BIOME),
        INFO_LIGHT(Group.INFO, "screen.voxymap.settings.info.light", MinimapInfoLine.LIGHT),
        INFO_TIME(Group.INFO, "screen.voxymap.settings.info.time", MinimapInfoLine.TIME),
        INFO_CHUNK(Group.INFO, "screen.voxymap.settings.info.chunk", MinimapInfoLine.CHUNK),
        INFO_FACING(Group.INFO, "screen.voxymap.settings.info.facing", MinimapInfoLine.FACING),

        RADAR_HOSTILE(Group.RADAR, "screen.voxymap.settings.radar.hostile", RadarCategory.HOSTILE),
        RADAR_NEUTRAL(Group.RADAR, "screen.voxymap.settings.radar.neutral", RadarCategory.NEUTRAL),
        RADAR_PASSIVE(Group.RADAR, "screen.voxymap.settings.radar.passive", RadarCategory.PASSIVE),
        RADAR_ITEMS(Group.RADAR, "screen.voxymap.settings.radar.items", RadarCategory.ITEMS),
        RADAR_PLAYERS(Group.RADAR, "screen.voxymap.settings.radar.players", RadarCategory.PLAYERS),
        RADAR_ICONS(Group.RADAR, "screen.voxymap.settings.radaricons", Availability.EVERYWHERE, false),

        OVERLAY_SLIME(Group.OVERLAYS, "screen.voxymap.settings.overlay.slime", MapOverlay.SLIME_CHUNKS),
        OVERLAY_LIGHT(Group.OVERLAYS, "screen.voxymap.settings.overlay.light", MapOverlay.SPAWN_LIGHT),
        OVERLAY_BIOMES(Group.OVERLAYS, "screen.voxymap.settings.overlay.biomes", MapOverlay.BIOMES),

        SYNC_ADDRESS(Group.SYNC, "screen.voxymap.settings.sync_address", Availability.IN_WORLD, false),
        SYNC_PASSWORD(Group.SYNC, "screen.voxymap.settings.sync_password", Availability.IN_WORLD, false),
        SYNC_STATUS(Group.SYNC, "screen.voxymap.settings.sync_status", Availability.IN_WORLD, false),
        MAP_SHARE(Group.SYNC, "screen.voxymap.settings.map_share", Availability.IN_MAP, false),
        MAP_RECEIVE(Group.SYNC, "screen.voxymap.settings.map_receive", Availability.IN_MAP, false),

        DIAGNOSTICS(Group.DIAGNOSTICS, "screen.voxymap.settings.diagnostics", Availability.EVERYWHERE, false);

        private final Group group;
        private final String labelKey;
        private final Availability availability;
        private final boolean vanillaSquadOnly;
        private final MinimapInfoLine infoLine;
        private final RadarCategory radarCategory;
        private final MapOverlay mapOverlay;

        Entry(Group group, String labelKey, Availability availability, boolean vanillaSquadOnly) {
            this(group, labelKey, availability, vanillaSquadOnly, null, null);
        }

        Entry(Group group, String labelKey, MapOverlay mapOverlay) {
            this(group, labelKey, Availability.EVERYWHERE, false, null, null, mapOverlay);
        }

        Entry(Group group, String labelKey, MinimapInfoLine infoLine) {
            this(group, labelKey, Availability.EVERYWHERE, false, infoLine, null);
        }

        Entry(Group group, String labelKey, RadarCategory radarCategory) {
            this(group, labelKey, Availability.EVERYWHERE, false, null, radarCategory);
        }

        Entry(Group group, String labelKey, Availability availability, boolean vanillaSquadOnly,
              MinimapInfoLine infoLine, RadarCategory radarCategory) {
            this(group, labelKey, availability, vanillaSquadOnly, infoLine, radarCategory, null);
        }

        Entry(Group group, String labelKey, Availability availability, boolean vanillaSquadOnly,
              MinimapInfoLine infoLine, RadarCategory radarCategory, MapOverlay mapOverlay) {
            this.mapOverlay = mapOverlay;
            this.group = group;
            this.labelKey = labelKey;
            this.availability = availability;
            this.vanillaSquadOnly = vanillaSquadOnly;
            this.infoLine = infoLine;
            this.radarCategory = radarCategory;
        }

        public Group group() {
            return group;
        }

        public String labelKey() {
            return labelKey;
        }

        public Availability availability() {
            return availability;
        }

        public boolean vanillaSquadOnly() {
            return vanillaSquadOnly;
        }

        public MinimapInfoLine infoLine() {
            return infoLine;
        }

        public RadarCategory radarCategory() {
            return radarCategory;
        }

        public MapOverlay mapOverlay() {
            return mapOverlay;
        }
    }

    public record Section(Group group, List<Entry> entries) {
    }

    public static List<Section> sections(boolean inWorld, boolean inMap, boolean onVanillaSquad) {
        return sections(inWorld, inMap, onVanillaSquad, true);
    }

    public static List<Section> sections(boolean inWorld, boolean inMap, boolean onVanillaSquad,
                                         boolean playerMarkersBuiltIn) {
        return sections(inWorld, inMap, onVanillaSquad, playerMarkersBuiltIn, true);
    }

    public static List<Section> sections(boolean inWorld, boolean inMap, boolean onVanillaSquad,
                                         boolean playerMarkersBuiltIn, boolean schematicsInstalled) {
        return sections(inWorld, inMap, onVanillaSquad, playerMarkersBuiltIn, schematicsInstalled, true);
    }

    public static List<Section> sections(boolean inWorld, boolean inMap, boolean onVanillaSquad,
                                         boolean playerMarkersBuiltIn, boolean schematicsInstalled,
                                         boolean spatialGuiInstalled) {
        List<Section> sections = new ArrayList<>();
        for (Group group : Group.values()) {
            List<Entry> entries = new ArrayList<>();
            for (Entry entry : Entry.values()) {
                if (entry.group != group) continue;
                if (entry.vanillaSquadOnly && !onVanillaSquad) continue;
                if (entry == Entry.VANILLASQUAD_PLAYERS && !playerMarkersBuiltIn) continue;
                if (entry == Entry.SCHEMATICS && !schematicsInstalled) continue;
                if ((entry == Entry.SPATIAL_GUI_FLAT_MAP || entry == Entry.SPATIAL_GUI_3D) && !spatialGuiInstalled) continue;
                boolean available = switch (entry.availability) {
                    case EVERYWHERE -> true;
                    case IN_WORLD -> inWorld || inMap;
                    case IN_MAP -> inMap;
                };
                if (available) {
                    entries.add(entry);
                }
            }
            if (!entries.isEmpty()) {
                sections.add(new Section(group, List.copyOf(entries)));
            }
        }
        return List.copyOf(sections);
    }
}
