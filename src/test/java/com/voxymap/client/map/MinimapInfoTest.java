package com.voxymap.client.map;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapInfoTest {
    @Test
    void clockStartsAtSixInTheMorningAndWrapsAtMidnight() {
        assertEquals("06:00", MinimapInfo.clockOf(0L));
        assertEquals("12:00", MinimapInfo.clockOf(6000L));
        assertEquals("00:00", MinimapInfo.clockOf(18000L));
        assertEquals("05:59", MinimapInfo.clockOf(23999L));
        assertEquals("06:00", MinimapInfo.clockOf(24000L * 5));
    }

    @Test
    void dayCountsFromOneAndChangesAtMidnight() {
        assertEquals(1, MinimapInfo.dayOf(0L));
        assertEquals(1, MinimapInfo.dayOf(17999L));
        assertEquals(2, MinimapInfo.dayOf(18000L));
        assertEquals(11, MinimapInfo.dayOf(24000L * 10));
    }

    @Test
    void counterpartCoordinatesFloorTowardsNegativeInfinity() {
        assertEquals(803, MinimapInfo.scaled(100.4, MinimapInfo.NETHER_SCALE));
        assertEquals(12, MinimapInfo.scaled(100.0, 1.0 / MinimapInfo.NETHER_SCALE));
        assertEquals(-13, MinimapInfo.scaled(-100.0, 1.0 / MinimapInfo.NETHER_SCALE));
        assertEquals(-1, MinimapInfo.scaled(-0.5, 1.0 / MinimapInfo.NETHER_SCALE));
    }

    @Test
    void bearingIsClockwiseFromNorth() {
        assertEquals(180, MinimapInfo.bearing(0.0f));
        assertEquals(270, MinimapInfo.bearing(90.0f));
        assertEquals(0, MinimapInfo.bearing(180.0f));
        assertEquals(90, MinimapInfo.bearing(-90.0f));
        assertEquals(90, MinimapInfo.bearing(-450.0f));
    }

    @Test
    void axisLabelsMatchMinecraftAxes() {
        assertEquals("-Z", MinimapInfo.axisLabel(Direction.NORTH));
        assertEquals("+Z", MinimapInfo.axisLabel(Direction.SOUTH));
        assertEquals("+X", MinimapInfo.axisLabel(Direction.EAST));
        assertEquals("-X", MinimapInfo.axisLabel(Direction.WEST));
    }

    @Test
    void defaultsShowCoordinatesAndBiomeOnly() {
        assertEquals(EnumSet.of(MinimapInfoLine.COORDINATES, MinimapInfoLine.BIOME), MinimapInfoLine.defaults());
    }

    @Test
    void radarShowsEveryMobKindAndPlayersButNotItemsByDefault() {
        assertEquals(EnumSet.of(RadarCategory.HOSTILE, RadarCategory.NEUTRAL, RadarCategory.PASSIVE, RadarCategory.PLAYERS),
                RadarCategory.defaults());
        assertTrue(RadarCategory.MARKER_DRAW_ORDER.stream().noneMatch(category -> category == RadarCategory.PLAYERS));
        assertEquals(RadarCategory.HOSTILE, RadarCategory.MARKER_DRAW_ORDER.getLast());
    }
}
