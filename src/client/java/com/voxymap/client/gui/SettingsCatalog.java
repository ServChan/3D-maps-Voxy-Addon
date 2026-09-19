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

    /** Declaration order is display order inside a group. */
    public enum Entry {
        PAUSE(Group.GENERAL, "screen.voxymap.settings.pause", false),
        SPEED(Group.GENERAL, "screen.voxymap.settings.speed", false),
        TIME_OF_DAY(Group.GENERAL, "screen.voxymap.settings.timeofday", false),
        FOG_SUPPRESSION(Group.GENERAL, "screen.voxymap.settings.fogsuppression", false),

        WAYPOINTS(Group.LAYERS, "screen.voxymap.settings.waypoints", false),
        DEATH_POINTS(Group.LAYERS, "screen.voxymap.settings.deathpoints", false),
        VANILLASQUAD_PLAYERS(Group.LAYERS, "screen.voxymap.settings.vanillasquad", false),
        VANILLASQUAD_FLAGS(Group.LAYERS, "screen.voxymap.settings.vanillasquadflags", false),
        VANILLASQUAD_MAP(Group.LAYERS, "screen.voxymap.settings.vanillasquadmap", true),

        MINIMAP(Group.MINIMAP, "screen.voxymap.settings.minimap", false),
        MINIMAP_ROTATE(Group.MINIMAP, "screen.voxymap.settings.minimaprotate", false),
        MOBS(Group.MINIMAP, "screen.voxymap.settings.mobs", false),
        CHUNK_GRID(Group.MINIMAP, "screen.voxymap.settings.chunkgrid", false),

        SYNC_ADDRESS(Group.SYNC, "screen.voxymap.settings.sync_address", true),
        SYNC_PASSWORD(Group.SYNC, "screen.voxymap.settings.sync_password", true),
        SYNC_STATUS(Group.SYNC, "screen.voxymap.settings.sync_status", true),
        MAP_SHARE(Group.SYNC, "screen.voxymap.settings.map_share", true),
        MAP_RECEIVE(Group.SYNC, "screen.voxymap.settings.map_receive", true),

        DIAGNOSTICS(Group.DIAGNOSTICS, "screen.voxymap.settings.diagnostics", false);

        private final Group group;
        private final String labelKey;
        private final boolean needsWorld;

        Entry(Group group, String labelKey, boolean needsWorld) {
            this.group = group;
            this.labelKey = labelKey;
            this.needsWorld = needsWorld;
        }

        public Group group() {
            return group;
        }

        public String labelKey() {
            return labelKey;
        }

        /** True for settings stored per world or reflecting live world state; they need the map open. */
        public boolean needsWorld() {
            return needsWorld;
        }
    }

    public record Section(Group group, List<Entry> entries) {
    }

    /**
     * @param inWorld whether a world is loaded (the in-map panel); false for the Mod Menu screen opened
     *                from the title screen, where per-world settings cannot be shown
     * @return the non-empty groups in display order
     */
    public static List<Section> sections(boolean inWorld) {
        List<Section> sections = new ArrayList<>();
        for (Group group : Group.values()) {
            List<Entry> entries = new ArrayList<>();
            for (Entry entry : Entry.values()) {
                if (entry.group != group) continue;
                if (entry.needsWorld && !inWorld) continue;
                entries.add(entry);
            }
            if (!entries.isEmpty()) {
                sections.add(new Section(group, List.copyOf(entries)));
            }
        }
        return List.copyOf(sections);
    }
}
