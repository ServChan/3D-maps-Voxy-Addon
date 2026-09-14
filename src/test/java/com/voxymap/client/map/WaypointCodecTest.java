package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointCodecTest {
    private static final String WORLD_A = "0123456789abcdef0123456789abcdef";
    private static final String WORLD_B = "abcdefabcdefabcdefabcdefabcdefab";

    @Test
    void parseIgnoresPropertiesOutsideTheWaypointNamespace() {
        Properties properties = new Properties();
        properties.setProperty("pauseSingleplayer", "true");
        properties.setProperty("vanillaSquadWorld." + WORLD_A, "build");

        assertTrue(WaypointCodec.parse(properties).isEmpty());
    }

    @Test
    void parseSkipsAnEntryWithAnInvalidWorldIdOrMissingFields() {
        Properties properties = new Properties();
        // "not-a-world-id" is not 32 lowercase hex characters, so this whole entry must be dropped.
        properties.setProperty("waypoint.not-a-world-id.id1.name", "Base");
        properties.setProperty("waypoint.not-a-world-id.id1.x", "10");
        properties.setProperty("waypoint.not-a-world-id.id1.y", "64");
        properties.setProperty("waypoint.not-a-world-id.id1.z", "10");
        // Missing "z" - incomplete waypoints must not surface as a marker with a garbage coordinate.
        properties.setProperty("waypoint." + WORLD_A + ".id2.name", "Incomplete");
        properties.setProperty("waypoint." + WORLD_A + ".id2.x", "5");
        properties.setProperty("waypoint." + WORLD_A + ".id2.y", "64");

        Map<String, List<WaypointManager.Waypoint>> parsed = WaypointCodec.parse(properties);
        assertTrue(parsed.getOrDefault(WORLD_A, List.of()).isEmpty());
    }

    @Test
    void serializeThenParseRoundTripsNameCoordinatesAndColor() {
        WaypointManager.Waypoint base = new WaypointManager.Waypoint("id-1", "Дом", 100.5, 64.0, -200.25, 0xFF62E7FF, false);
        WaypointManager.Waypoint mine = new WaypointManager.Waypoint("id-2", "Mine entrance", -12.0, 11.0, 300.0, 0xFFFFC94D, false);
        Map<String, List<WaypointManager.Waypoint>> data = Map.of(
                WORLD_A, List.of(base, mine),
                WORLD_B, List.of(new WaypointManager.Waypoint("id-3", "Other world", 1.0, 2.0, 3.0, 0xFFFF62D4, false)));

        Properties serialized = WaypointCodec.serialize(data);
        Map<String, List<WaypointManager.Waypoint>> reparsed = WaypointCodec.parse(serialized);

        List<WaypointManager.Waypoint> worldAWaypoints = reparsed.get(WORLD_A);
        assertEquals(2, worldAWaypoints.size());
        // parse() sorts alphabetically by name (code point order, so Latin sorts before Cyrillic),
        // independent of Properties' unordered iteration.
        assertEquals("Mine entrance", worldAWaypoints.get(0).name());
        assertEquals("Дом", worldAWaypoints.get(1).name());
        assertEquals(100.5, worldAWaypoints.get(1).x());
        assertEquals(-200.25, worldAWaypoints.get(1).z());
        assertEquals(0xFF62E7FF, worldAWaypoints.get(1).color());
        assertEquals(false, worldAWaypoints.get(1).shared());

        assertEquals(1, reparsed.get(WORLD_B).size());
        assertEquals("Other world", reparsed.get(WORLD_B).get(0).name());
    }

    @Test
    void parseFallsBackToTheDefaultPaletteColorWhenColorIsMissingOrCorrupt() {
        Properties properties = new Properties();
        properties.setProperty("waypoint." + WORLD_A + ".id1.name", "No color stored");
        properties.setProperty("waypoint." + WORLD_A + ".id1.x", "0");
        properties.setProperty("waypoint." + WORLD_A + ".id1.y", "0");
        properties.setProperty("waypoint." + WORLD_A + ".id1.z", "0");
        properties.setProperty("waypoint." + WORLD_A + ".id2.name", "Corrupt color");
        properties.setProperty("waypoint." + WORLD_A + ".id2.x", "0");
        properties.setProperty("waypoint." + WORLD_A + ".id2.y", "0");
        properties.setProperty("waypoint." + WORLD_A + ".id2.z", "0");
        properties.setProperty("waypoint." + WORLD_A + ".id2.color", "not-hex");

        for (WaypointManager.Waypoint waypoint : WaypointCodec.parse(properties).get(WORLD_A)) {
            assertEquals(0xFF62E7FF, waypoint.color());
        }
    }
}
