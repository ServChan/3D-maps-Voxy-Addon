package com.voxymap.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FlatMapTerrain {
    public static final int REGION_CHUNKS = 8;
    public static final int REGION_BLOCKS = REGION_CHUNKS * MinimapTileCache.TILE_SIZE;
    static final int MAX_REGIONS = 192;
    private static final int CHUNKS_PER_FRAME = 32;
    private static final long RESCAN_MILLIS = 4000L;

    private static final class Region {
        final Identifier id;
        final DynamicTexture texture;
        boolean dirty;
        long lastUsed;

        Region(Identifier id, DynamicTexture texture) {
            this.id = id;
            this.texture = texture;
        }
    }

    private static final Map<Long, Region> REGIONS = new HashMap<>();
    private static final Map<Long, Long> SCANNED_AT = new HashMap<>();
    private static Level level;
    private static MinimapTileCache.ScanMode mode;
    private static int modeY;
    private static long useCounter;

    private FlatMapTerrain() {
    }

    public static void update(Minecraft minecraft) {
        Player player = minecraft.player;
        Level current = minecraft.level;
        if (player == null || current == null) {
            return;
        }
        if (current != level) {
            clear(minecraft);
            level = current;
        }
        int playerY = (int) Math.floor(player.getY());
        MinimapTileCache.ScanMode scanMode = MinimapTileCache.scanMode(current.dimensionType().hasCeiling(), playerY,
                current.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        (int) Math.floor(player.getX()), (int) Math.floor(player.getZ())));
        if (scanMode != mode || (scanMode == MinimapTileCache.ScanMode.CAVE
                && Math.abs(playerY - modeY) >= MinimapTileCache.Y_TOLERANCE_BLOCKS)) {
            mode = scanMode;
            modeY = playerY;
            SCANNED_AT.clear();
        }

        int playerChunkX = Math.floorDiv((int) Math.floor(player.getX()), 16);
        int playerChunkZ = Math.floorDiv((int) Math.floor(player.getZ()), 16);
        int radius = Math.max(2, minecraft.options.getEffectiveRenderDistance());
        long now = System.currentTimeMillis();
        int budget = CHUNKS_PER_FRAME;
        for (int ring = 0; ring <= radius && budget > 0; ring++) {
            for (int dz = -ring; dz <= ring && budget > 0; dz++) {
                int step = dz == -ring || dz == ring ? 1 : ring * 2;
                for (int dx = -ring; dx <= ring && budget > 0; dx += Math.max(1, step)) {
                    if (scanChunk(minecraft, current, playerChunkX + dx, playerChunkZ + dz, now)) {
                        budget--;
                    }
                }
            }
        }

        for (Region region : REGIONS.values()) {
            if (region.dirty) {
                region.texture.upload();
                region.dirty = false;
            }
        }
        evict(minecraft, Math.floorDiv(playerChunkX, REGION_CHUNKS), Math.floorDiv(playerChunkZ, REGION_CHUNKS));
    }

    private static boolean scanChunk(Minecraft minecraft, Level current, int chunkX, int chunkZ, long now) {
        long key = MinimapTileCache.key(chunkX, chunkZ);
        Long scanned = SCANNED_AT.get(key);
        if (scanned != null && now - scanned < RESCAN_MILLIS) {
            return false;
        }
        ChunkAccess chunk = current.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) {
            return false;
        }
        int[] tile = MinimapTileCache.computeTile(current, chunk, chunkX, chunkZ, mode, modeY);
        SCANNED_AT.put(key, now);
        Region region = region(minecraft, Math.floorDiv(chunkX, REGION_CHUNKS), Math.floorDiv(chunkZ, REGION_CHUNKS));
        NativeImage pixels = region.texture.getPixels();
        int size = MinimapTileCache.TILE_SIZE;
        int baseX = Math.floorMod(chunkX, REGION_CHUNKS) * size;
        int baseY = Math.floorMod(chunkZ, REGION_CHUNKS) * size;
        for (int ty = 0; ty < size; ty++) {
            for (int tx = 0; tx < size; tx++) {
                pixels.setPixelABGR(baseX + tx, baseY + ty, MinimapRenderer.argbToAbgr(tile[ty * size + tx]));
            }
        }
        region.dirty = true;
        region.lastUsed = ++useCounter;
        return true;
    }

    private static Region region(Minecraft minecraft, int regionX, int regionZ) {
        long key = MinimapTileCache.key(regionX, regionZ);
        Region region = REGIONS.get(key);
        if (region == null) {
            Identifier id = Identifier.fromNamespaceAndPath("voxymap", "flat_map/" + regionX + "_" + regionZ);
            DynamicTexture texture = new DynamicTexture(() -> "VoxyMap flat map " + regionX + "," + regionZ,
                    REGION_BLOCKS, REGION_BLOCKS, true);
            minecraft.getTextureManager().register(id, texture);
            region = new Region(id, texture);
            REGIONS.put(key, region);
        }
        return region;
    }

    public static Identifier regionTexture(int regionX, int regionZ) {
        Region region = REGIONS.get(MinimapTileCache.key(regionX, regionZ));
        if (region == null) {
            return null;
        }
        region.lastUsed = ++useCounter;
        return region.id;
    }

    private static void evict(Minecraft minecraft, int playerRegionX, int playerRegionZ) {
        if (REGIONS.size() <= MAX_REGIONS) {
            return;
        }
        List<Long> victims = evictionOrder(REGIONS.keySet(), key -> REGIONS.get(key).lastUsed, playerRegionX, playerRegionZ,
                REGIONS.size() - MAX_REGIONS);
        Set<Long> removed = new HashSet<>(victims);
        for (long key : victims) {
            Region region = REGIONS.remove(key);
            minecraft.getTextureManager().release(region.id);
        }
        SCANNED_AT.keySet().removeIf(chunkKey -> removed.contains(MinimapTileCache.key(
                Math.floorDiv(MinimapTileCache.keyX(chunkKey), REGION_CHUNKS),
                Math.floorDiv(MinimapTileCache.keyZ(chunkKey), REGION_CHUNKS))));
    }

    static List<Long> evictionOrder(Set<Long> keys, java.util.function.ToLongFunction<Long> lastUsed,
                                    int keepRegionX, int keepRegionZ, int count) {
        long keep = MinimapTileCache.key(keepRegionX, keepRegionZ);
        List<Long> candidates = new ArrayList<>();
        for (long key : keys) {
            if (key != keep) {
                candidates.add(key);
            }
        }
        candidates.sort(Comparator.comparingLong(lastUsed));
        return List.copyOf(candidates.subList(0, Math.min(count, candidates.size())));
    }

    public static void clear(Minecraft minecraft) {
        for (Region region : REGIONS.values()) {
            minecraft.getTextureManager().release(region.id);
        }
        REGIONS.clear();
        SCANNED_AT.clear();
        level = null;
        mode = null;
    }
}
