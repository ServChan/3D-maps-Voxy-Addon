package com.voxymap.client.map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointDisplayTest {
    @Test
    void everyModeNarrowsThePreviousOne() {
        assertEquals(EnumSet.allOf(WaypointDisplay.Surface.class), surfaces(WaypointDisplay.EVERYWHERE));
        assertEquals(EnumSet.of(WaypointDisplay.Surface.MINIMAP, WaypointDisplay.Surface.MAP), surfaces(WaypointDisplay.MAPS));
        assertEquals(EnumSet.of(WaypointDisplay.Surface.MAP), surfaces(WaypointDisplay.BIG_MAP));
        assertEquals(EnumSet.noneOf(WaypointDisplay.Surface.class), surfaces(WaypointDisplay.HIDDEN));
    }

    @Test
    void cyclingVisitsEveryModeAndWrapsAround() {
        WaypointDisplay display = WaypointDisplay.EVERYWHERE;
        EnumSet<WaypointDisplay> seen = EnumSet.noneOf(WaypointDisplay.class);
        for (int i = 0; i < WaypointDisplay.values().length; i++) {
            assertTrue(seen.add(display));
            display = display.next();
        }
        assertEquals(WaypointDisplay.EVERYWHERE, display);
    }

    @Test
    void parsesStoredKeysAndRejectsUnknownOnes() {
        for (WaypointDisplay display : WaypointDisplay.values()) {
            assertEquals(display, WaypointDisplay.parse(display.key()));
        }
        assertEquals(WaypointDisplay.BIG_MAP, WaypointDisplay.parse(" Big_Map "));
        assertNull(WaypointDisplay.parse("minimap"));
        assertNull(WaypointDisplay.parse(null));
    }

    @Test
    void translationKeysFollowTheStoredKey() {
        assertEquals("screen.voxymap.waypoint.display.big_map", WaypointDisplay.BIG_MAP.translationKey());
        assertEquals("screen.voxymap.waypoint.display.big_map.short", WaypointDisplay.BIG_MAP.shortTranslationKey());
        assertFalse(WaypointDisplay.HIDDEN.shows(WaypointDisplay.Surface.MAP));
    }

    @Test
    void everyModeIsTranslated() throws IOException {
        JsonObject english;
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources/assets/voxymap/lang/en_us.json"),
                StandardCharsets.UTF_8)) {
            english = JsonParser.parseReader(reader).getAsJsonObject();
        }
        for (WaypointDisplay display : WaypointDisplay.values()) {
            assertTrue(english.has(display.translationKey()), display.name());
            assertTrue(english.has(display.shortTranslationKey()), display.name());
            assertTrue(english.has(display.tooltipTranslationKey()), display.name());
        }
    }

    private static EnumSet<WaypointDisplay.Surface> surfaces(WaypointDisplay display) {
        EnumSet<WaypointDisplay.Surface> surfaces = EnumSet.noneOf(WaypointDisplay.Surface.class);
        for (WaypointDisplay.Surface surface : WaypointDisplay.Surface.values()) {
            if (display.shows(surface)) {
                surfaces.add(surface);
            }
        }
        return surfaces;
    }
}
