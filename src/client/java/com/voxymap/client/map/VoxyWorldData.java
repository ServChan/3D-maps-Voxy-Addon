package com.voxymap.client.map;

import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class VoxyWorldData {
    public static final int API_VERSION = 1;
    private static final int CHUNK_SECTION_VOLUME = 4096;

    private VoxyWorldData() {
    }

    public static BlockState[] chunkSection(Level level, int sectionX, int sectionY, int sectionZ) {
        if (level == null) {
            return null;
        }
        try {
            WorldEngine engine = WorldIdentifier.ofEngineNullable(level);
            if (engine == null) {
                return null;
            }
            WorldSection section = engine.acquireIfExists(0, sectionX >> 1, sectionY >> 1, sectionZ >> 1);
            if (section == null) {
                return null;
            }
            long[] data;
            try {
                data = section.copyData();
            } finally {
                section.release();
            }
            return extract(data, engine.getMapper(), sectionX & 1, sectionY & 1, sectionZ & 1);
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    private static BlockState[] extract(long[] data, Mapper mapper, int halfX, int halfY, int halfZ) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState[] states = new BlockState[CHUNK_SECTION_VOLUME];
        int baseX = halfX << 4;
        int baseY = halfY << 4;
        int baseZ = halfZ << 4;
        int lastId = -1;
        BlockState lastState = air;
        boolean anything = false;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                int row = ((baseY + y) << 10) | ((baseZ + z) << 5) | baseX;
                int target = (y << 8) | (z << 4);
                for (int x = 0; x < 16; x++) {
                    long voxel = data[row + x];
                    if (Mapper.isAir(voxel)) {
                        states[target + x] = air;
                        continue;
                    }
                    int id = Mapper.getBlockId(voxel);
                    if (id != lastId) {
                        BlockState state = mapper.getBlockStateFromBlockId(id);
                        lastState = state == null ? air : state;
                        lastId = id;
                    }
                    states[target + x] = lastState;
                    anything = true;
                }
            }
        }
        return anything ? states : null;
    }
}
