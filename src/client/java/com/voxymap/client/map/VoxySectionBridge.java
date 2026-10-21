package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import me.cortex.voxy.common.voxelization.VoxelizedSection;
import me.cortex.voxy.common.voxelization.WorldVoxilizedSectionMipper;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.WorldUpdater;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.VoxyInstance;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VoxySectionBridge {
    private static final int BLOCK_STATE_TYPE = 1;
    private static final int BIOME_TYPE = 2;
    private static final int TYPE_SHIFT = 30;
    private static final int TYPE_MASK = (1 << TYPE_SHIFT) - 1;
    static final long EXPORT_BYTES_PER_SECTION = 8L * 4096L * 8L;

    private VoxySectionBridge() {
    }

    static WorldIdentifier currentWorldIdentifier(Minecraft minecraft) {
        return minecraft.level == null ? null : WorldIdentifier.of(minecraft.level);
    }

    public static boolean holdRef(WorldEngine engine) {
        try {
            engine.acquireRef();
            return true;
        } catch (IllegalStateException notLive) {
            return false;
        }
    }

    public static void dropRef(WorldEngine engine) {
        try {
            engine.releaseRef();
        } catch (IllegalStateException alreadyFreed) {
            VoxyMapClient.LOGGER.debug("Voxy world was freed while VoxyMap still held a reference");
        }
    }

    static WorldEngine liveEngine(WorldIdentifier worldId) {
        if (worldId == null) return null;
        VoxyInstance instance = VoxyCommon.getInstance();
        return instance == null ? null : instance.getNullable(worldId);
    }

    record MappingRow(int packedKey, byte[] data) {
    }

    static List<MappingRow> exportMappings(WorldIdentifier worldId) {
        WorldEngine engine = liveEngine(worldId);
        if (engine == null || !holdRef(engine)) return List.of();
        try {
            List<MappingRow> rows = new ArrayList<>();
            engine.storage.getIdMappingsData().forEach((key, bytes) -> rows.add(new MappingRow(key, bytes)));
            return rows;
        } finally {
            dropRef(engine);
        }
    }

    static long exportedSectionCount(WorldIdentifier worldId, MapShareBounds bounds) {
        WorldEngine engine = liveEngine(worldId);
        if (engine == null) return 0L;
        long[] count = {0L};
        engine.storage.iteratePositions(0, key -> {
            if (bounds == null || bounds.containsSection(WorldEngine.getX(key), WorldEngine.getZ(key))) {
                count[0]++;
            }
        });
        return count[0];
    }

    static long[] exportedSectionKeys(WorldIdentifier worldId, MapShareBounds bounds, int limit) {
        WorldEngine engine = liveEngine(worldId);
        if (engine == null || !holdRef(engine)) return new long[0];
        try {
            LongArrayList keys = new LongArrayList();
            engine.storage.iteratePositions(0, key -> {
                if (keys.size() < limit
                        && (bounds == null || bounds.containsSection(WorldEngine.getX(key), WorldEngine.getZ(key)))) {
                    keys.add(key);
                }
            });
            return keys.toLongArray();
        } finally {
            dropRef(engine);
        }
    }

    static long[] exportSectionData(WorldIdentifier worldId, long sectionKey) {
        WorldEngine engine = liveEngine(worldId);
        if (engine == null || !holdRef(engine)) return null;
        try {
            WorldSection section = engine.acquireIfExists(sectionKey);
            if (section == null) return null;
            try {
                return section.copyData();
            } finally {
                section.release();
            }
        } finally {
            dropRef(engine);
        }
    }

    static long[][] splitIntoChunkSections(long[] worldSectionData) {
        long[][] result = new long[8][4096];
        for (int y = 0; y < 32; y++) {
            for (int z = 0; z < 32; z++) {
                int rowBase = (y << 10) | (z << 5);
                for (int x = 0; x < 32; x++) {
                    long voxel = worldSectionData[rowBase | x];
                    int sub = (x >> 4) | ((z >> 4) << 1) | ((y >> 4) << 2);
                    int dstIdx = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
                    result[sub][dstIdx] = voxel;
                }
            }
        }
        return result;
    }

    static int occupiedChunkSectionMask(WorldIdentifier worldId, int x, int y, int z) {
        WorldEngine engine = liveEngine(worldId);
        if (engine == null || !holdRef(engine)) return 0xFF;
        long[] data;
        try {
            WorldSection existing = engine.acquireIfExists(0, x, y, z);
            if (existing == null) return 0;
            try {
                data = existing.copyData();
            } finally {
                existing.release();
            }
        } finally {
            dropRef(engine);
        }
        return occupiedChunkSectionMask(data, Mapper::isAir);
    }

    static int occupiedChunkSectionMask(long[] worldSectionData, java.util.function.LongPredicate isAir) {
        int mask = 0;
        for (int index = 0; index < worldSectionData.length; index++) {
            if (!isAir.test(worldSectionData[index])) {
                int x = index & 31;
                int z = (index >> 5) & 31;
                int y = (index >> 10) & 31;
                mask |= 1 << chunkSectionIndex(x >> 4, y >> 4, z >> 4);
            }
        }
        return mask;
    }

    static int chunkSectionIndex(int halfX, int halfY, int halfZ) {
        return (halfX & 1) | ((halfZ & 1) << 1) | ((halfY & 1) << 2);
    }

    record IdTranslation(Map<Integer, Integer> blockIds, Map<Integer, Integer> biomeIds) {
    }

    record WorldSectionKey(int x, int y, int z) {
    }

    static IdTranslation buildTranslation(Minecraft minecraft, WorldIdentifier worldId, List<MappingRow> senderMappings) {
        WorldEngine engine = liveEngine(worldId);
        if (engine == null || minecraft.level == null) return new IdTranslation(Map.of(), Map.of());
        Mapper mapper = engine.getMapper();
        Registry<Biome> biomeRegistry = minecraft.level.registryAccess().lookupOrThrow(Registries.BIOME);

        Map<Integer, Integer> blockIds = new HashMap<>();
        Map<Integer, Integer> biomeIds = new HashMap<>();
        for (MappingRow row : senderMappings) {
            int type = row.packedKey() >>> TYPE_SHIFT;
            int senderId = row.packedKey() & TYPE_MASK;
            try {
                if (type == BLOCK_STATE_TYPE) {
                    Mapper.StateEntry entry = Mapper.StateEntry.deserialize(senderId, row.data(), new boolean[1]);
                    blockIds.put(senderId, mapper.getIdForBlockState(entry.state));
                } else if (type == BIOME_TYPE) {
                    Mapper.BiomeEntry entry = Mapper.BiomeEntry.deserialize(senderId, row.data());
                    Identifier biomeName = Identifier.tryParse(entry.biome);
                    if (biomeName == null) continue;
                    Holder<Biome> holder = biomeRegistry.get(biomeName).orElse(null);
                    if (holder == null) continue;
                    biomeIds.put(senderId, mapper.getIdForBiome(holder));
                }
            } catch (RuntimeException ignored) {

            }
        }
        return new IdTranslation(blockIds, biomeIds);
    }

    static boolean importChunkSection(WorldIdentifier worldId, int chunkX, int chunkY, int chunkZ,
                                       long[] senderLocalVoxels, IdTranslation translation,
                                       Map<WorldSectionKey, Integer> occupiedMaskCache) {
        WorldEngine engine = liveEngine(worldId);
        if (engine == null) return false;
        int wx = chunkX >> 1, wy = chunkY >> 1, wz = chunkZ >> 1;
        int occupied = occupiedMaskCache.computeIfAbsent(new WorldSectionKey(wx, wy, wz),
                key -> occupiedChunkSectionMask(worldId, key.x(), key.y(), key.z()));
        if ((occupied & (1 << chunkSectionIndex(chunkX, chunkY, chunkZ))) != 0) return false;

        VoxelizedSection section = VoxelizedSection.createEmpty().zero();
        section.setPosition(chunkX, chunkY, chunkZ);
        int nonAir = 0;
        for (int i = 0; i < senderLocalVoxels.length; i++) {
            long voxel = senderLocalVoxels[i];
            if (Mapper.isAir(voxel)) {
                Integer airBiomeId = translation.biomeIds().get(Mapper.getBiomeId(voxel));
                section.section[i] = Mapper.withBlockBiome(voxel, 0, airBiomeId == null ? 0 : airBiomeId);
                continue;
            }
            Integer blockId = translation.blockIds().get(Mapper.getBlockId(voxel));
            Integer biomeId = translation.biomeIds().get(Mapper.getBiomeId(voxel));
            if (blockId == null || biomeId == null) {
                return false;
            }
            section.section[i] = Mapper.withBlockBiome(voxel, blockId, biomeId);
            nonAir++;
        }
        section.lvl0NonAirCount = nonAir;
        if (!holdRef(engine)) {
            return false;
        }
        try {
            WorldVoxilizedSectionMipper.mipSection(section, engine.getMapper());
            WorldUpdater.insertUpdate(engine, section);
        } finally {
            dropRef(engine);
        }
        return true;
    }
}
