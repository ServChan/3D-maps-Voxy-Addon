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
            List<OrderedWaypoint> list = new ArrayList<>();
            for (Map.Entry<String, Map<String, String>> idEntry : worldEntry.getValue().entrySet()) {
                Map<String, String> fields = idEntry.getValue();
                String name = fields.get("name");
                Double x = parseDouble(fields.get("x"));
                Double y = parseDouble(fields.get("y"));
                Double z = parseDouble(fields.get("z"));
                if (name == null || name.isBlank() || x == null || y == null || z == null) continue;

                list.add(new OrderedWaypoint(parseOrder(fields.get("order")),
                        new WaypointManager.Waypoint(idEntry.getKey(), name, x, y, z, parseColor(fields.get("color")), false)));
            }
            list.sort(Comparator.comparingLong(OrderedWaypoint::order)
                    .thenComparing(entry -> entry.waypoint().name().toLowerCase(Locale.ROOT)));
            result.put(worldEntry.getKey(), new ArrayList<>(list.stream().map(OrderedWaypoint::waypoint).toList()));
        }
        return result;
    }

    static Properties serialize(Map<String, List<WaypointManager.Waypoint>> data) {
        Properties properties = new Properties();
        for (Map.Entry<String, List<WaypointManager.Waypoint>> worldEntry : data.entrySet()) {
            List<WaypointManager.Waypoint> waypoints = worldEntry.getValue();
            for (int index = 0; index < waypoints.size(); index++) {
                WaypointManager.Waypoint waypoint = waypoints.get(index);
                String prefix = "waypoint." + worldEntry.getKey() + "." + waypoint.id() + ".";
                properties.setProperty(prefix + "order", Integer.toString(index));
                properties.setProperty(prefix + "name", waypoint.name());
                properties.setProperty(prefix + "x", Double.toString(waypoint.x()));
                properties.setProperty(prefix + "y", Double.toString(waypoint.y()));
                properties.setProperty(prefix + "z", Double.toString(waypoint.z()));
                properties.setProperty(prefix + "color", Integer.toHexString(waypoint.color()));
            }
        }
        return properties;
    }

    private record OrderedWaypoint(long order, WaypointManager.Waypoint waypoint) {
    }

    private static long parseOrder(String value) {
        if (value == null) return Long.MAX_VALUE;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return Long.MAX_VALUE;
        }
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

            }
        }
        return PALETTE[0];
    }
}
