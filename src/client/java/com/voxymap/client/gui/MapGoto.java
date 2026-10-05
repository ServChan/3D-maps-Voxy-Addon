package com.voxymap.client.gui;

import com.voxymap.client.map.WaypointText;

final class MapGoto {
    record Target(int x, Integer y, int z) {
    }

    private MapGoto() {
    }

    static Target parse(String text) {
        if (text == null) {
            return null;
        }
        String[] parts = text.trim().split("[\\s,;]+");
        if (parts.length == 2) {
            Integer x = WaypointText.parseCoordinate(parts[0]);
            Integer z = WaypointText.parseCoordinate(parts[1]);
            return x == null || z == null ? null : new Target(x, null, z);
        }
        if (parts.length == 3) {
            Integer x = WaypointText.parseCoordinate(parts[0]);
            Integer y = WaypointText.parseCoordinate(parts[1]);
            Integer z = WaypointText.parseCoordinate(parts[2]);
            return x == null || y == null || z == null ? null : new Target(x, y, z);
        }
        return null;
    }

    static boolean allowedCharacter(int codePoint) {
        return Character.isDigit(codePoint) || codePoint == '-' || codePoint == ' ' || codePoint == ','
                || codePoint == ';';
    }
}
