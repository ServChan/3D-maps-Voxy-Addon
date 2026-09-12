package com.voxymap.client.integration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryClustererTest {
    @Test
    void groupsSameNameAndColourFlagsIntoOneTerritoryInsteadOfManyMarkers() {
        List<VanillaSquadFlagMapBridge.Flag> flags = List.of(
                new VanillaSquadFlagMapBridge.Flag(0, 0, "RED", "Virelia"),
                new VanillaSquadFlagMapBridge.Flag(10, 0, "RED", "Virelia"),
                new VanillaSquadFlagMapBridge.Flag(10, 10, "RED", "Virelia"),
                new VanillaSquadFlagMapBridge.Flag(0, 10, "RED", "Virelia"));

        List<VanillaSquadFlagMapBridge.Territory> territories = TerritoryClusterer.cluster(flags);

        assertEquals(1, territories.size(), "1200 individual markers of the same nation must collapse to one territory");
        VanillaSquadFlagMapBridge.Territory virelia = territories.get(0);
        assertEquals("Virelia", virelia.name());
        assertEquals(4, virelia.outline().size());
        assertEquals(5.0, virelia.centroidX());
        assertEquals(5.0, virelia.centroidZ());
    }

    @Test
    void sameNameButDifferentColourFormsSeparateTerritories() {
        List<VanillaSquadFlagMapBridge.Flag> flags = List.of(
                new VanillaSquadFlagMapBridge.Flag(0, 0, "RED", "Border"),
                new VanillaSquadFlagMapBridge.Flag(1, 0, "RED", "Border"),
                new VanillaSquadFlagMapBridge.Flag(2, 1, "RED", "Border"),
                new VanillaSquadFlagMapBridge.Flag(500, 500, "BLUE", "Border"),
                new VanillaSquadFlagMapBridge.Flag(501, 500, "BLUE", "Border"),
                new VanillaSquadFlagMapBridge.Flag(502, 501, "BLUE", "Border"));

        assertEquals(2, TerritoryClusterer.cluster(flags).size());
    }

    @Test
    void unnamedBannersAreDroppedRatherThanRenderedIndividually() {
        List<VanillaSquadFlagMapBridge.Flag> flags = List.of(
                new VanillaSquadFlagMapBridge.Flag(0, 0, "RED", ""),
                new VanillaSquadFlagMapBridge.Flag(1, 1, "RED", ""));

        assertTrue(TerritoryClusterer.cluster(flags).isEmpty());
    }

    @Test
    void clustersSmallerThanThreeKeepTheirRawPointsInstedOfAnEmptyHull() {
        List<VanillaSquadFlagMapBridge.Flag> flags = List.of(
                new VanillaSquadFlagMapBridge.Flag(0, 0, "GREEN", "Outpost"),
                new VanillaSquadFlagMapBridge.Flag(4, 0, "GREEN", "Outpost"));

        List<VanillaSquadFlagMapBridge.Territory> territories = TerritoryClusterer.cluster(flags);

        assertEquals(1, territories.size());
        assertEquals(2, territories.get(0).outline().size());
    }
}
