package com.voxymap.client.map;

import java.util.Locale;

public enum WaypointDisplay {
    EVERYWHERE,
    MAPS,
    BIG_MAP,
    HIDDEN;

    public enum Surface { WORLD, MINIMAP, MAP }

    public boolean shows(Surface surface) {
        return switch (surface) {
            case WORLD -> this == EVERYWHERE;
            case MINIMAP -> this == EVERYWHERE || this == MAPS;
            case MAP -> this != HIDDEN;
        };
    }

    public WaypointDisplay next() {
        WaypointDisplay[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "screen.voxymap.waypoint.display." + key();
    }

    public String shortTranslationKey() {
        return translationKey() + ".short";
    }

    public String tooltipTranslationKey() {
        return translationKey() + ".tooltip";
    }

    public static WaypointDisplay parse(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (WaypointDisplay display : values()) {
            if (display.name().equals(normalized)) return display;
        }
        return null;
    }
}
