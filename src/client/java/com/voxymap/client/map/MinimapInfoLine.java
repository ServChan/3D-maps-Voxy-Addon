package com.voxymap.client.map;

import java.util.EnumSet;
import java.util.Set;

public enum MinimapInfoLine {
    COORDINATES(true),
    NETHER_COORDINATES(false),
    BIOME(true),
    LIGHT(false),
    TIME(false),
    CHUNK(false),
    FACING(false);

    private final boolean defaultShown;

    MinimapInfoLine(boolean defaultShown) {
        this.defaultShown = defaultShown;
    }

    public static Set<MinimapInfoLine> defaults() {
        EnumSet<MinimapInfoLine> shown = EnumSet.noneOf(MinimapInfoLine.class);
        for (MinimapInfoLine line : values()) {
            if (line.defaultShown) {
                shown.add(line);
            }
        }
        return shown;
    }
}
