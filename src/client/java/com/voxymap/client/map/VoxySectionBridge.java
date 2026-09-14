package com.voxymap.client.map;

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

/**
 * Bridges VoxyMap's "share explored terrain with another player" feature to Voxy's live in-memory
 * world storage. See {@link MapShareClient} for the wire format and overall flow; this class is the
 * part that actually touches Voxy.
 *
 * <p>Voxy assigns each distinct block state and biome a small integer id, per world database, in
 * first-encountered order (see {@link Mapper}) - two players' independently-explored copies of "the
 * same" server can therefore have different ids for the same real block. Every export/import here goes
 * through {@link Mapper}'s own id table (block state / biome &lt;-&gt; local id) so voxel data always
 * gets correctly re-mapped into the receiving world's own ids - raw ids are never copied across worlds.
 *
 * <p>Import only ever adds sections this world doesn't already have (exact position match) and always
 * goes through Voxy's own {@link WorldUpdater#insertUpdate} - the same method Voxy itself calls when it
 * finishes voxelizing a newly-loaded chunk - so mip levels, non-empty-block bookkeeping, and render/save
 * notification all happen exactly as they would from normal exploration.
 */
final class VoxySectionBridge {
    private static final int BLOCK_STATE_TYPE = 1;
    private static final int BIOME_TYPE = 2;
    private static final int TYPE_SHIFT = 30;
    private static final int TYPE_MASK = (1 << TYPE_SHIFT) - 1;

    private VoxySectionBridge() {
    }

    static WorldIdentifier currentWorldIdentifier(Minecraft minecraft) {
        return minecraft.level == null ? null : WorldIdentifier.of(minecraft.level);
    }

    private static WorldEngine liveEngine(WorldIdentifier worldId) {
        if (worldId == null) return null;
        VoxyInstance instance = VoxyCommon.getInstance();
        return instance == null ? null : instance.getNullable(worldId);
    }

    /** One row of the sender's block/biome id vocabulary - opaque bytes taken directly from Voxy's own
     * mapping storage format, so no separate serialization is needed for this half of the transfer. */
    record MappingRow(int packedKey, byte[] data) {
    }

    static List<MappingRow> exportMappings(WorldIdentifier worldId) {
        WorldEngine engine = liveEngine(worldId);
        if (engine == null) return List.of();
        List<MappingRow> rows = new ArrayList<>();
        engine.storage.getIdMappingsData().forEach((key, bytes) -> rows.add(new MappingRow(key, bytes)));
        return rows;
    }

    /** Number of currently-explored level-0 (real detail, not LOD-simplified) sections for this world -
     * see {@link WorldEngine#getWorldSectionId}. Counts without materializing every key into a list;
     * see {@link #forEachExportedSectionKey} when the keys themselves are actually needed. */
    static long exportedSectionCount(WorldIdentifier worldId) {
        return exportedSectionCount(worldId, null);
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

    /** Streams every currently-explored level-0 section key to {@code action} one at a time, instead of
     * collecting them all into one list first - a heavily-explored world (the class doc's "many
     * gigabytes" case) can have millions of sections, and holding that many boxed keys alive at once as
     * a single list is exactly the kind of large, long-lived allocation that turns into a visible GC
     * pause even though the export itself runs on a background thread. */
    static void forEachExportedSectionKey(WorldIdentifier worldId, java.util.function.LongConsumer action) {
        forEachExportedSectionKey(worldId, null, action);
    }

    static void forEachExportedSectionKey(WorldIdentifier worldId, MapShareBounds bounds,
                                           java.util.function.LongConsumer action) {
        WorldEngine engine = liveEngine(worldId);
        if (engine == null) return;
        engine.storage.iteratePositions(0, key -> {
            if (bounds == null || bounds.containsSection(WorldEngine.getX(key), WorldEngine.getZ(key))) {
                action.accept(key);
            }
        });
    }

    /** Upper-bound estimate, in bytes, of what a full export of this world would send - the raw
     * uncompressed size (each section splits into 8 chunk-sections of 4096 level-0 voxels, 8 bytes
     * each), not the gzip-compressed size actually put on the wire. Computing the real compressed size
     * ahead of time would mean fully compressing the export before anyone has agreed to receive it -
     * exactly what the confirm-before-anything-moves design (see MapRelayHandler) exists to avoid - so
     * this deliberately overstates it; shown to both sides as "up to ~N MB" before they agree to a
     * transfer, not as an exact byte count. */
    static long estimateExportBytes(WorldIdentifier worldId) {
        return estimateExportBytes(worldId, null);
    }

    static long estimateExportBytes(WorldIdentifier worldId, MapShareBounds bounds) {
        return exportedSectionCount(worldId, bounds) * 8L * 4096L * 8L;
    }

    /** Copies one level-0 section's raw 32x32x32 voxel data out (still in the sender's own local
     * block/biome ids - translation happens on the receiving side), or null if it no longer exists
     * (e.g. unloaded between listing and export). */
    static long[] exportSectionData(WorldIdentifier worldId, long sectionKey) {
        WorldEngine engine = liveEngine(worldId);
        if (engine == null) return null;
        WorldSection section = engine.acquireIfExists(sectionKey);
        if (section == null) return null;
        try {
            return section.copyData();
        } finally {
            section.release();
        }
    }

    /** Splits one level-0 section's 32x32x32 array (indexed {@code (y<<10)|(z<<5)|x}, matching
     * {@code WorldSection.getIndex}) into its 8 constituent 16x16x16 chunk-sections (indexed
     * {@code (y<<8)|(z<<4)|x}, matching {@link VoxelizedSection}'s own level-0 layout). Sub-section
     * {@code i} occupies the low half of each axis when its bit is 0 - bit0=x, bit1=z, bit2=y, the same
     * convention {@code WorldSection.getChildIndex} uses. */
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

    /** True if this world already has a level-0 section at this exact position - import must never
     * overwrite it. */
    static boolean hasSection(WorldIdentifier worldId, int x, int y, int z) {
        WorldEngine engine = liveEngine(worldId);
        if (engine == null) return false;
        WorldSection existing = engine.acquireIfExists(0, x, y, z);
        if (existing == null) return false;
        existing.release();
        return true;
    }

    record IdTranslation(Map<Integer, Integer> blockIds, Map<Integer, Integer> biomeIds) {
    }

    /** Identifies one level-0 {@code WorldSection} (32x32x32, made up of 8 chunk-sections) for
     * {@link #importChunkSection}'s per-session "did this already exist before we started importing"
     * cache - see that method for why this can't just call {@link #hasSection} fresh each time. */
    record WorldSectionKey(int x, int y, int z) {
    }

    /** Builds this world's own translation from a sender's local block/biome ids to this world's local
     * ids, registering any block state or biome this world hasn't encountered yet - exactly as normal
     * exploration would. Call once per import session, before {@link #importChunkSection}. */
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
                    if (holder == null) continue; // This world's registry lacks that biome - sections using it are dropped below.
                    biomeIds.put(senderId, mapper.getIdForBiome(holder));
                }
            } catch (RuntimeException ignored) {
                // A corrupt or foreign-version entry - the sections referencing it get dropped in
                // importChunkSection below rather than aborting the whole transfer.
            }
        }
        return new IdTranslation(blockIds, biomeIds);
    }

    /** Imports one chunk-section's worth of level-0 voxel data (still in the sender's local ids -
     * translated here via {@code translation}) at the given chunk-section position, going through
     * Voxy's normal ingestion path so mips/bookkeeping/render notification all happen correctly.
     * Returns false (no-op) if this exact position isn't a gap, or if any referenced block/biome id has
     * no translation (a corrupt or foreign-version entry - refused rather than guessed at).
     *
     * <p>{@code worldSectionCache} must be one fresh, mutable map shared across an entire import
     * session (see {@link MapShareClient#readImport}), not created fresh per call. A level-0
     * {@code WorldSection} covers all 8 chunk-sections this method gets called with for a given parent
     * position, and {@link WorldUpdater#insertUpdate} makes that {@code WorldSection} exist as soon as
     * the first of those 8 is inserted - so a fresh {@link #hasSection} check on the 2nd-8th call would
     * see our own just-inserted sibling and wrongly treat the position as "already explored", silently
     * dropping 7 of every 8 chunk-sections. Caching the "did this exist before we touched it" verdict
     * once per parent position, before any of its children are imported, avoids that. */
    static boolean importChunkSection(WorldIdentifier worldId, int chunkX, int chunkY, int chunkZ,
                                       long[] senderLocalVoxels, IdTranslation translation,
                                       Map<WorldSectionKey, Boolean> worldSectionCache) {
        WorldEngine engine = liveEngine(worldId);
        if (engine == null) return false;
        int wx = chunkX >> 1, wy = chunkY >> 1, wz = chunkZ >> 1;
        boolean preexisting = worldSectionCache.computeIfAbsent(new WorldSectionKey(wx, wy, wz),
                key -> hasSection(worldId, key.x(), key.y(), key.z()));
        if (preexisting) return false;

        VoxelizedSection section = VoxelizedSection.createEmpty().zero();
        section.setPosition(chunkX, chunkY, chunkZ);
        int nonAir = 0;
        for (int i = 0; i < senderLocalVoxels.length; i++) {
            long voxel = senderLocalVoxels[i];
            if (Mapper.isAir(voxel)) {
                section.section[i] = voxel;
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
        WorldVoxilizedSectionMipper.mipSection(section, engine.getMapper());
        WorldUpdater.insertUpdate(engine, section);
        return true;
    }
}
