package com.voxymap.client.map;

import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class VoxyWorldData {
    public static final int API_VERSION = 2;
    public static final int PADDED_SIZE = 18;
    private static final int PADDED_VOLUME = PADDED_SIZE * PADDED_SIZE * PADDED_SIZE;

    private VoxyWorldData() {
    }

    public static BlockState[] chunkSectionPadded(Level level, int sectionX, int sectionY, int sectionZ) {
        if (level == null) {
            return null;
        }
        WorldSection[] held = new WorldSection[8];
        try {
            WorldEngine engine = WorldIdentifier.ofEngineNullable(level);
            if (engine == null) {
                return null;
            }
            int startX = (sectionX << 4) - 1;
            int startY = (sectionY << 4) - 1;
            int startZ = (sectionZ << 4) - 1;
            int baseX = startX >> 5;
            int baseY = startY >> 5;
            int baseZ = startZ >> 5;
            long[][] data = new long[8][];
            boolean[] tried = new boolean[8];
            int centre = ((sectionX >> 1) - baseX) | ((sectionZ >> 1) - baseZ) << 1 | ((sectionY >> 1) - baseY) << 2;
            held[centre] = engine.acquireIfExists(0, sectionX >> 1, sectionY >> 1, sectionZ >> 1);
            tried[centre] = true;
            if (held[centre] == null) {
                return null;
            }
            data[centre] = held[centre]._unsafeGetRawDataArray();
            Mapper mapper = engine.getMapper();
            BlockState air = Blocks.AIR.defaultBlockState();
            BlockState[] states = new BlockState[PADDED_VOLUME];
            int lastId = -1;
            BlockState lastState = air;
            int index = 0;
            for (int dy = 0; dy < PADDED_SIZE; dy++) {
                int y = startY + dy;
                for (int dz = 0; dz < PADDED_SIZE; dz++) {
                    int z = startZ + dz;
                    for (int dx = 0; dx < PADDED_SIZE; dx++, index++) {
                        int x = startX + dx;
                        int slot = ((x >> 5) - baseX) | ((z >> 5) - baseZ) << 1 | ((y >> 5) - baseY) << 2;
                        if (!tried[slot]) {
                            tried[slot] = true;
                            held[slot] = engine.acquireIfExists(0, x >> 5, y >> 5, z >> 5);
                            data[slot] = held[slot] == null ? null : held[slot]._unsafeGetRawDataArray();
                        }
                        long[] voxels = data[slot];
                        if (voxels == null) {
                            continue;
                        }
                        long voxel = voxels[((y & 31) << 10) | ((z & 31) << 5) | (x & 31)];
                        if (Mapper.isAir(voxel)) {
                            states[index] = air;
                            continue;
                        }
                        int id = Mapper.getBlockId(voxel);
                        if (id != lastId) {
                            BlockState state = mapper.getBlockStateFromBlockId(id);
                            lastState = state == null ? air : state;
                            lastId = id;
                        }
                        states[index] = lastState;
                    }
                }
            }
            return states;
        } catch (RuntimeException | LinkageError exception) {
            return null;
        } finally {
            for (WorldSection section : held) {
                if (section != null) {
                    section.release();
                }
            }
        }
    }
}
