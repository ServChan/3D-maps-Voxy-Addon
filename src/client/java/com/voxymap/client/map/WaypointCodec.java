package com.voxymap.client.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Pure parsing/serialization for {@link WaypointManager}'s waypoint list, kept free of
 * FabricLoader/file I/O so it can be unit tested directly. {@link WaypointManager} touches
 * {@code FabricLoader.getInstance()} in a static field initializer, and any static method call on
 * that class - even a pure one - would trigger that same initializer and throw outside a running
 * Fabric environment, so the pure logic lives here instead, in a class with no such field.
 */
final class WaypointCodec {
    static final Pattern VOXY_WORLD_ID = Pattern.compile("[0-9a-f]{32}");
    static final int[] PALETTE = {
            0xFF62E7FF, 0xFFFFC94D, 0xFFFF62D4, 0xFF7CFF7A, 0xFFB388FF, 0xFFFF8A5C
    };

    private WaypointCodec() {
    }

    static boolean validWorldId(String worldId) {
        return worldId != null && VOXY_WORLD_ID.matcher(worldId).matches();
    }

    static Map<String, List<WaypointManager.Waypoint>> parse(Properties properties) {
        Map<String, Map<String, Map<String, String>>> raw = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            String[] parts = key.split("\\.");
            if (parts.length != 4 || !"waypoint".equals(parts[0])) continue;
            String worldId = parts[1];
            if (!validWorldId(worldId)) continue;
            raw.computeIfAbsent(worldId, w -> new LinkedHashMap<>())
                    .computeIfAbsent(parts[2], i -> new HashMap<>())
                    .put(parts[3], properties.getProperty(key));
        }

        Map<String, List<WaypointManager.Waypoint>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Map<String, String>>> worldEntry : raw.entrySet()) {
            List<WaypointManager.Waypoint> list = new ArrayList<>();
            for (Map.Entry<String, Map<String, String>> idEntry : worldEntry.getValue().entrySet()) {
                Map<String, String> fields = idEntry.getValue();
                String name = fields.get("name");
                Double x = parseDouble(fields.get("x"));
                Double y = parseDouble(fields.get("y"));
                Double z = parseDouble(fields.get("z"));
                if (name == null || name.isBlank() || x == null || y == null || z == null) continue;
                // Local storage only ever holds non-shared pins - shared ones live on the sync server.
                list.add(new WaypointManager.Waypoint(idEntry.getKey(), name, x, y, z, parseColor(fields.get("color")), false));
            }
            list.sort(Comparator.comparing(w -> w.name().toLowerCase(Locale.ROOT)));
            result.put(worldEntry.getKey(), list);
        }
        return result;
    }

    static Properties serialize(Map<String, List<WaypointManager.Waypoint>> data) {
        Properties properties = new Properties();
        for (Map.Entry<String, List<WaypointManager.Waypoint>> worldEntry : data.entrySet()) {
            for (WaypointManager.Waypoint waypoint : worldEntry.getValue()) {
                String prefix = "waypoint." + worldEntry.getKey() + "." + waypoint.id() + ".";
                properties.setProperty(prefix + "name", waypoint.name());
                properties.setProperty(prefix + "x", Double.toString(waypoint.x()));
                properties.setProperty(prefix + "y", Double.toString(waypoint.y()));
                properties.setProperty(prefix + "z", Double.toString(waypoint.z()));
                properties.setProperty(prefix + "color", Integer.toHexString(waypoint.color()));
            }
        }
        return properties;
    }

    private static Double parseDouble(String value) {
        if (value == null) return null;
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int parseColor(String value) {
        if (value != null) {
            try {
                return (int) Long.parseLong(value, 16);
            } catch (NumberFormatException ignored) {
                // Fall through to the default below.
            }
        }
        return PALETTE[0];
    }
}
