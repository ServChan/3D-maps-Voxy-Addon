package com.voxymap.shared;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointJsonTest {
    @Test
    void writeListThenReadListRoundTripsAllFields() {
        WaypointDto base = new WaypointDto("id-1", "Дом \"базы\"", 100.5, 64.0, -200.25, 0xFF62E7FF);
        WaypointDto mine = new WaypointDto("id-2", "Mine\nentrance", -12.0, 11.0, 300.0, 0xFFFFC94D);

        String json = WaypointJson.writeList(List.of(base, mine));
        List<WaypointDto> parsed = WaypointJson.readList(json);

        assertEquals(2, parsed.size());
        assertEquals(base, parsed.get(0));
        assertEquals(mine, parsed.get(1));
    }

    @Test
    void writeOneThenReadOneRoundTrips() {
        WaypointDto waypoint = new WaypointDto("id-1", "Base", 1.0, 2.0, 3.0, 0xFFFF62D4);
        String json = WaypointJson.writeOne(waypoint);
        assertEquals(waypoint, WaypointJson.readOne(json, "fallback-id"));
    }

    @Test
    void readOneUsesFallbackIdWhenBodyOmitsIt() {

        String bodyWithoutId = "{\"name\":\"Base\",\"x\":1,\"y\":2,\"z\":3,\"color\":\"ff62e7ff\"}";
        WaypointDto parsed = WaypointJson.readOne(bodyWithoutId, "from-url");
        assertEquals("from-url", parsed.id());
        assertEquals("Base", parsed.name());
    }

    @Test
    void loneSurrogatesAreNotWellFormedText() {
        assertTrue(WaypointJson.wellFormedText("Base \uD83C\uDFE0"));
        assertFalse(WaypointJson.wellFormedText("broken \uD800"));
        assertFalse(WaypointJson.wellFormedText("\uDC00 broken"));
    }

    @Test
    void strictReadingRejectsWhatLenientReadingTurnsIntoAnEmptyList() {
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> WaypointJson.readListStrict("<html>not found</html>"));
        assertTrue(WaypointJson.readListStrict("{\"waypoints\":[]}").isEmpty());
    }

    @Test
    void readListReturnsEmptyForMalformedJson() {
        assertTrue(WaypointJson.readList("not json at all").isEmpty());
        assertTrue(WaypointJson.readList("{\"waypoints\": [").isEmpty());
    }

    @Test
    void readListDropsEntriesMissingRequiredFields() {
        String json = "{\"waypoints\":[" +
                "{\"id\":\"a\",\"name\":\"Complete\",\"x\":1,\"y\":2,\"z\":3,\"color\":\"ff62e7ff\"}," +
                "{\"id\":\"b\",\"name\":\"Missing z\",\"x\":1,\"y\":2}" +
                "]}";
        List<WaypointDto> parsed = WaypointJson.readList(json);
        assertEquals(1, parsed.size());
        assertEquals("Complete", parsed.get(0).name());
    }

    @Test
    void deeplyNestedInputIsRejectedInsteadOfOverflowingTheStack() {
        String nested = "[".repeat(100_000) + "]".repeat(100_000);
        assertTrue(WaypointJson.readList(nested).isEmpty());
        assertNull(WaypointJson.readOne(nested, "fallback"));
    }

    @Test
    void readOneReturnsNullForMalformedJson() {
        assertNull(WaypointJson.readOne("{broken", "id"));
    }

    @Test
    void integerCoordinatesRoundTripWithoutADecimalPoint() {
        String json = WaypointJson.writeOne(new WaypointDto("id", "Spawn", 100.0, 64.0, -32.0, 0xFF62E7FF));
        assertTrue(json.contains("\"x\":100,"), json);
        assertTrue(json.contains("\"z\":-32,") || json.contains("\"z\":-32}"), json);
    }
}
