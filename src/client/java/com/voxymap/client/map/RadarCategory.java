package com.voxymap.client.map;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public enum RadarCategory {
    HOSTILE(true, true, 0xFFFF5555),
    NEUTRAL(true, true, 0xFFFFC94D),
    PASSIVE(true, true, 0xFF7CFC6E),
    ITEMS(false, false, 0xFFE9F2FF),
    PLAYERS(true, false, 0xFFFF62D4);

    public static final List<RadarCategory> MARKER_DRAW_ORDER = List.of(ITEMS, PASSIVE, NEUTRAL, HOSTILE);

    private final boolean defaultShown;
    private final boolean mob;
    private final int color;

    RadarCategory(boolean defaultShown, boolean mob, int color) {
        this.defaultShown = defaultShown;
        this.mob = mob;
        this.color = color;
    }

    public boolean mob() {
        return mob;
    }

    public int color() {
        return color;
    }

    public static Set<RadarCategory> defaults() {
        EnumSet<RadarCategory> shown = EnumSet.noneOf(RadarCategory.class);
        for (RadarCategory category : values()) {
            if (category.defaultShown) {
                shown.add(category);
            }
        }
        return shown;
    }
}
