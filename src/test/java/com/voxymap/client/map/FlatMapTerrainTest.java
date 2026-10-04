package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlatMapTerrainTest {
    @Test
    void evictsLeastRecentlyUsedAndKeepsPlayerRegion() {
        long player = MinimapTileCache.key(0, 0);
        long old = MinimapTileCache.key(5, -3);
        long older = MinimapTileCache.key(-7, 2);
        long recent = MinimapTileCache.key(1, 1);
        Map<Long, Long> lastUsed = Map.of(player, 1L, old, 5L, older, 2L, recent, 9L);
        assertEquals(List.of(older, old),
                FlatMapTerrain.evictionOrder(lastUsed.keySet(), lastUsed::get, 0, 0, 2));
    }

    @Test
    void evictionNeverReturnsMoreThanAvailable() {
        Map<Long, Long> lastUsed = Map.of(MinimapTileCache.key(0, 0), 1L, MinimapTileCache.key(3, 3), 2L);
        assertEquals(List.of(MinimapTileCache.key(3, 3)),
                FlatMapTerrain.evictionOrder(lastUsed.keySet(), lastUsed::get, 0, 0, 5));
    }
}
