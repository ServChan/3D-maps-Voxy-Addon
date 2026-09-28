package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import java.util.function.LongPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VoxySectionBridgeTest {
    private static final long SOLID = 42L;
    private static final LongPredicate IS_AIR = voxel -> voxel != SOLID;

    private static int voxyIndex(int x, int y, int z) {
        return (y << 10) | (z << 5) | x;
    }

    @Test
    void occupiedMaskMatchesTheChunkSectionTheExportPutsTheBlockIn() {
        for (int x : new int[]{0, 17, 31}) {
            for (int y : new int[]{3, 20}) {
                for (int z : new int[]{5, 16, 30}) {
                    long[] data = new long[32 * 32 * 32];
                    data[voxyIndex(x, y, z)] = SOLID;

                    long[][] chunks = VoxySectionBridge.splitIntoChunkSections(data);
                    int exportedSub = -1;
                    for (int sub = 0; sub < 8; sub++) {
                        for (long voxel : chunks[sub]) {
                            if (voxel == SOLID) exportedSub = sub;
                        }
                    }
                    int chunkX = (7 << 1) | (exportedSub & 1);
                    int chunkZ = (-3 << 1) | ((exportedSub >> 1) & 1);
                    int chunkY = (2 << 1) | ((exportedSub >> 2) & 1);

                    assertEquals(1 << VoxySectionBridge.chunkSectionIndex(chunkX, chunkY, chunkZ),
                            VoxySectionBridge.occupiedChunkSectionMask(data, IS_AIR));
                }
            }
        }
    }

    @Test
    void anAllAirSectionReportsNoOccupiedChunkSections() {
        assertEquals(0, VoxySectionBridge.occupiedChunkSectionMask(new long[32 * 32 * 32], IS_AIR));
    }
}
