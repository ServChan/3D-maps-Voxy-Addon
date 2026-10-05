package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointTextTest {
    @Test
    void initialIsTheFirstLetterOrDigitUppercased() {
        assertEquals("H", WaypointText.initial("home"));
        assertEquals("Т", WaypointText.initial("  точка смерти"));
        assertEquals("3", WaypointText.initial("#3 farm"));
        assertEquals("?", WaypointText.initial("  !! "));
        assertEquals("?", WaypointText.initial(null));
    }

    @Test
    void xaeroStringRoundTripsNameCoordinatesColourAndDimension() {
        String text = WaypointText.toXaero("Base: north", 120.7, 64.2, -340.1, 0xFFFF5555, WaypointText.Dimension.NETHER);
        assertEquals("xaero-waypoint:Base^col^ north:B:120:64:-341:12:false:0:Internal-the_nether-waypoints", text);

        List<WaypointText.SharedWaypoint> parsed = WaypointText.parseXaero("<Steve> look " + text);
        assertEquals(1, parsed.size());
        WaypointText.SharedWaypoint shared = parsed.getFirst();
        assertEquals("Base: north", shared.name());
        assertEquals(120, shared.x());
        assertEquals(64, shared.y());
        assertEquals(-341, shared.z());
        assertEquals(12, shared.colorIndex());
        assertEquals(WaypointText.Dimension.NETHER, shared.dimension());
    }

    @Test
    void xaeroMessagesWithUnknownHeightAndWithoutContainerAreAccepted() {
        List<WaypointText.SharedWaypoint> parsed = WaypointText.parseXaero(
                "xaero-waypoint:Camp:C:10:~:20:3:true:90");
        assertEquals(1, parsed.size());
        assertNull(parsed.getFirst().y());
        assertEquals(WaypointText.Dimension.OVERWORLD, parsed.getFirst().dimension());
    }

    @Test
    void parsingIsCappedAndIgnoresBrokenEntries() {
        String one = "xaero-waypoint:A:A:1:2:3:0:false:0:Internal-overworld-waypoints ";
        assertEquals(WaypointText.MAX_SHARED_PER_MESSAGE, WaypointText.parseXaero(one.repeat(10)).size());
        assertTrue(WaypointText.parseXaero("xaero-waypoint:A:A:x:2:3:0:false:0").isEmpty());
        assertTrue(WaypointText.parseXaero("hello world").isEmpty());
    }

    @Test
    void namesLoseFormattingCodesAndControlCharacters() {
        assertEquals("Red base", WaypointText.cleanName("§cRed\u0007 base "));
        assertEquals(WaypointText.MAX_NAME_LENGTH, WaypointText.cleanName("x".repeat(200)).length());
    }

    @Test
    void nearestChatColourPicksTheClosestOfTheSixteen() {
        assertEquals(12, WaypointText.nearestChatColor(0xFFFF5050));
        assertEquals(15, WaypointText.nearestChatColor(0xFFF0F0F0));
        assertEquals(0, WaypointText.nearestChatColor(0xFF050505));
        assertEquals(0xFF55FFFF, WaypointText.chatColorArgb(11));
    }

    @Test
    void coordinatesConvertBetweenNetherAndOverworldOnly() {
        assertEquals(new WaypointText.Converted(800, -1600),
                WaypointText.convert(100, -200, WaypointText.Dimension.NETHER, WaypointText.Dimension.OVERWORLD));
        assertEquals(new WaypointText.Converted(12, -26),
                WaypointText.convert(100, -201, WaypointText.Dimension.OVERWORLD, WaypointText.Dimension.NETHER));
        assertEquals(new WaypointText.Converted(5, 6),
                WaypointText.convert(5, 6, WaypointText.Dimension.END, WaypointText.Dimension.END));
        assertNull(WaypointText.convert(5, 6, WaypointText.Dimension.END, WaypointText.Dimension.OVERWORLD));
        assertNull(WaypointText.convert(5, 6, WaypointText.Dimension.OVERWORLD, null));
    }

    @Test
    void typedCoordinatesMustBeWholeNumbersInsideTheWorld() {
        assertEquals(-340, WaypointText.parseCoordinate(" -340 "));
        assertNull(WaypointText.parseCoordinate("12.5"));
        assertNull(WaypointText.parseCoordinate(""));
        assertNull(WaypointText.parseCoordinate("40000000"));
    }

    @Test
    void dimensionsAreRecognisedFromLevelKeysAndIds() {
        assertEquals(WaypointText.Dimension.NETHER, WaypointText.Dimension.fromLevelKey("minecraft:the_nether"));
        assertNull(WaypointText.Dimension.fromLevelKey("mymod:moon"));
        assertEquals(WaypointText.Dimension.END, WaypointText.Dimension.fromId("end"));
        assertNull(WaypointText.Dimension.fromId("moon"));
    }

    @Test
    void distancesSwitchToKilometresAtOneThousandBlocks() {
        assertEquals(false, WaypointText.useKilometres(999.4));
        assertEquals(true, WaypointText.useKilometres(999.5));
        assertEquals("1.0", WaypointText.kilometres(999.5));
        assertEquals("12.3", WaypointText.kilometres(12_345));
    }

    @Test
    void scaleFactorsMapNetherAndOverworldOntoEachOther() {
        assertEquals(1.0 / 8.0, WaypointText.scaleFactor(WaypointText.Dimension.OVERWORLD, WaypointText.Dimension.NETHER));
        assertEquals(8.0, WaypointText.scaleFactor(WaypointText.Dimension.NETHER, WaypointText.Dimension.OVERWORLD));
        assertEquals(1.0, WaypointText.scaleFactor(WaypointText.Dimension.END, WaypointText.Dimension.END));
        assertTrue(Double.isNaN(WaypointText.scaleFactor(WaypointText.Dimension.END, WaypointText.Dimension.NETHER)));
    }

    @Test
    void symbolsAreAtMostTwoUppercaseCharacters() {
        assertEquals("AB", WaypointText.cleanSymbol(" ab c"));
        assertEquals("Ж", WaypointText.cleanSymbol("ж"));
        assertNull(WaypointText.cleanSymbol("   "));
        assertNull(WaypointText.cleanSymbol(null));
    }
}
