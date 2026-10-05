package com.voxymap.client.integration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VanillaSquadWebMapTest {
    private static final String SETTINGS = """
            {"worlds":[{"name":"minecraft_the_nether","display_name":"Нижний мир","type":"nether","order":1},
            {"name":"minecraft_overworld","display_name":"Обычный мир","type":"normal","order":0},
            {"name":"../escape","display_name":"x","type":"normal","order":0},
            {"display_name":"no name"}],"ui":{}}
            """;

    @Test
    void listsValidWorldsInServerOrder() {
        List<VanillaSquadWebMap.World> worlds = VanillaSquadWebMap.parseWorlds("farm", SETTINGS);
        assertEquals(List.of("minecraft_overworld", "minecraft_the_nether"),
                worlds.stream().map(VanillaSquadWebMap.World::name).toList());
        assertEquals("farm/minecraft_the_nether", worlds.get(1).key());
        assertEquals("minecraft:the_nether", worlds.get(1).dimensionId());
        assertEquals("Нижний мир", worlds.get(1).displayName());
    }

    @Test
    void buildsSquaremapTileUrls() {
        VanillaSquadWebMap.World world = VanillaSquadWebMap.parseWorlds("build", SETTINGS).getFirst();
        assertEquals("https://vanillasquad.com/map/proxy/build/tiles/minecraft_overworld/3/-1_2.png",
                world.tileUrl(3, -1, 2));
    }

    @Test
    void rejectsAnEmptySettingsResponse() {
        assertThrows(IllegalArgumentException.class, () -> VanillaSquadWebMap.parseWorlds("art", ""));
    }

    @Test
    void readsZoomAndSpawnFromWorldSettings() {
        VanillaSquadWebMap.WorldSettings settings = VanillaSquadWebMap.parseWorldSettings(
                "{\"spawn\":{\"x\":-133,\"z\":130},\"zoom\":{\"def\":3,\"max\":3,\"extra\":2}}");
        assertEquals(new VanillaSquadWebMap.WorldSettings(3, -133, 130), settings);
    }

    @Test
    void fallsBackToDefaultsForBrokenOrHostileWorldSettings() {
        assertEquals(VanillaSquadWebMap.WorldSettings.DEFAULT, VanillaSquadWebMap.parseWorldSettings("not json"));
        assertEquals(8, VanillaSquadWebMap.parseWorldSettings("{\"zoom\":{\"max\":999}}").maxZoom());
        assertEquals(30_000_000, VanillaSquadWebMap.parseWorldSettings("{\"spawn\":{\"x\":2000000000,\"z\":0}}").spawnX());
    }
}
