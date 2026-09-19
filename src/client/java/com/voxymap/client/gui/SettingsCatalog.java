package com.voxymap.client.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for which settings exist, how they are grouped and in what order. The
 * in-map settings panel and the Mod Menu screen both render from this list, so a setting can never
 * appear in one and be missing or misplaced in the other.
 */
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

    /** Where a setting can be shown. */
    public enum Availability {
        /** Global: on the map and in the Mod Menu screen, in or out of a world. */
        EVERYWHERE,
        /** Stored per world, so it needs a loaded world: on the map, and in Mod Menu opened from a world. */
        IN_WORLD,
        /** Needs the map's own interaction (selecting an area, typing a code), so only on the map. */
        IN_MAP
    }

    /** Declaration order is display order inside a group. */
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

        /** True for the VanillaSquad settings, which only exist while connected to that server. */
        public boolean vanillaSquadOnly() {
            return vanillaSquadOnly;
        }
    }

    public record Section(Group group, List<Entry> entries) {
    }

    /**
     * @param inWorld        whether a world is loaded (the map, or Mod Menu opened from inside a world)
     * @param inMap          whether this is the map's own settings panel, which can also run the map-only actions
     * @param onVanillaSquad whether the client is connected to the VanillaSquad server; the VanillaSquad
     *                       settings are hidden everywhere else
     * @return the non-empty groups in display order
     */
    public static List<Section> sections(boolean inWorld, boolean inMap, boolean onVanillaSquad) {
        List<Section> sections = new ArrayList<>();
        for (Group group : Group.values()) {
            List<Entry> entries = new ArrayList<>();
            for (Entry entry : Entry.values()) {
                if (entry.group != group) continue;
                if (entry.vanillaSquadOnly && !onVanillaSquad) continue;
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
