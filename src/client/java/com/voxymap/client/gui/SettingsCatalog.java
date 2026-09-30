package com.voxymap.client.gui;

import java.util.ArrayList;
import java.util.List;

public final class SettingsCatalog {
    private SettingsCatalog() {
    }

    public enum Group {
        GENERAL("screen.voxymap.settings.group.general"),
        LAYERS("screen.voxymap.settings.group.layers"),
        MINIMAP("screen.voxymap.settings.group.minimap"),
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
        FOG_SUPPRESSION(Group.GENERAL, "screen.voxymap.settings.fogsuppression", Availability.EVERYWHERE, false),
        SCREENSHOT(Group.GENERAL, "screen.voxymap.settings.screenshot", Availability.EVERYWHERE, false),

        WAYPOINTS(Group.LAYERS, "screen.voxymap.settings.waypoints", Availability.EVERYWHERE, false),
        DEATH_POINTS(Group.LAYERS, "screen.voxymap.settings.deathpoints", Availability.EVERYWHERE, false),
        VIEW_DIRECTION(Group.LAYERS, "screen.voxymap.settings.viewdirection", Availability.EVERYWHERE, false),
        VANILLASQUAD_PLAYERS(Group.LAYERS, "screen.voxymap.settings.vanillasquad", Availability.EVERYWHERE, true),
        VANILLASQUAD_FLAGS(Group.LAYERS, "screen.voxymap.settings.vanillasquadflags", Availability.EVERYWHERE, true),
        VANILLASQUAD_MAP(Group.LAYERS, "screen.voxymap.settings.vanillasquadmap", Availability.IN_WORLD, true),
        SCHEMATICS(Group.LAYERS, "screen.voxymap.settings.schematics", Availability.EVERYWHERE, false),

        MINIMAP(Group.MINIMAP, "screen.voxymap.settings.minimap", Availability.EVERYWHERE, false),
        MINIMAP_ROTATE(Group.MINIMAP, "screen.voxymap.settings.minimaprotate", Availability.EVERYWHERE, false),
        MOBS(Group.MINIMAP, "screen.voxymap.settings.mobs", Availability.EVERYWHERE, false),
        CHUNK_GRID(Group.MINIMAP, "screen.voxymap.settings.chunkgrid", Availability.EVERYWHERE, false),

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

        Entry(Group group, String labelKey, Availability availability, boolean vanillaSquadOnly) {
            this.group = group;
            this.labelKey = labelKey;
            this.availability = availability;
            this.vanillaSquadOnly = vanillaSquadOnly;
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
        List<Section> sections = new ArrayList<>();
        for (Group group : Group.values()) {
            List<Entry> entries = new ArrayList<>();
            for (Entry entry : Entry.values()) {
                if (entry.group != group) continue;
                if (entry.vanillaSquadOnly && !onVanillaSquad) continue;
                if (entry == Entry.VANILLASQUAD_PLAYERS && !playerMarkersBuiltIn) continue;
                if (entry == Entry.SCHEMATICS && !schematicsInstalled) continue;
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
