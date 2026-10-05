package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnumFlagsTest {
    @Test
    void missingValueFallsBackToDefaults() {
        assertEquals(RadarCategory.defaults(), EnumFlags.parse(RadarCategory.class, null, RadarCategory.defaults()));
    }

    @Test
    void emptyValueMeansEverythingIsOff() {
        assertEquals(EnumSet.noneOf(RadarCategory.class), EnumFlags.parse(RadarCategory.class, "", RadarCategory.defaults()));
    }

    @Test
    void unknownNamesAndWhitespaceAreIgnored() {
        assertEquals(EnumSet.of(RadarCategory.HOSTILE, RadarCategory.ITEMS),
                EnumFlags.parse(RadarCategory.class, " hostile , dragons,ITEMS,", RadarCategory.defaults()));
    }

    @Test
    void formatRoundTripsInDeclarationOrder() {
        EnumSet<MinimapInfoLine> lines = EnumSet.of(MinimapInfoLine.FACING, MinimapInfoLine.COORDINATES, MinimapInfoLine.TIME);
        String text = EnumFlags.format(lines);
        assertEquals("coordinates,time,facing", text);
        assertEquals(lines, EnumFlags.parse(MinimapInfoLine.class, text, MinimapInfoLine.defaults()));
    }
}
