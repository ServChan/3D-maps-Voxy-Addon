package com.voxymap.client.map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.MapColor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cached 16x16 top-down color tiles per chunk, computed once from already-loaded chunk data using
 * Minecraft's own per-block {@link MapColor} - the same source vanilla's own item map uses, not
 * anything Voxy-specific. The minimap only ever shows chunks the client already has loaded, the same
 * "never load or reveal anything new" rule the rest of VoxyMap follows.
 *
 * <p>A tile is computed once and kept forever - if a player edits terrain in an already-cached chunk,
 * the minimap won't reflect it until the client restarts. That is an accepted simplification: proper
 * invalidation needs a block-change listener, real complexity for staleness a small gameplay overlay
 * can tolerate.
 */
final class MinimapTileCache {
    static final int TILE_SIZE = 16;

    private final Map<Long, int[]> tiles = new ConcurrentHashMap<>();
    /** The {@link Level} the cache is currently keyed against - chunk (x,z) alone repeats across
     * dimensions and servers, so without this a tile cached in one world/dimension would silently be
     * served back as another's after switching. Minecraft hands out a fresh {@code Level} instance on
     * every dimension change, world rejoin, or reconnect, so comparing this reference is enough to
     * detect that and also keeps the cache's size bounded to the current visit instead of growing for
     * the whole client process's lifetime. */
    private Level currentLevel;

    /** Returns the cached tile for this chunk (computing and caching it first if needed), or null if
     * the chunk isn't currently loaded - never forces a chunk load. {@code playerY} is only consulted
     * the first time a given chunk is cached (see the class doc) - it anchors the downward surface scan
     * in {@link #computeTile} to roughly where the player was standing at that moment. */
    int[] tileFor(Level level, int chunkX, int chunkZ, int playerY) {
        if (level != currentLevel) {
            tiles.clear();
            currentLevel = level;
        }
        long key = ChunkPos.pack(chunkX, chunkZ);
        int[] cached = tiles.get(key);
        if (cached != null) {
            return cached;
        }

        ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) {
            return null;
        }

        int[] colors = computeTile(level, chunk, chunkX, chunkZ, playerY);
        tiles.put(key, colors);
        return colors;
    }

    private static int[] computeTile(Level level, ChunkAccess chunk, int chunkX, int chunkZ, int playerY) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int minY = level.getMinY();
        // Vanilla's WORLD_SURFACE heightmap scans down from the top of the world, so indoors it finds
        // the roof and in the Nether it finds the bedrock ceiling instead of the ground the player is
        // actually standing on. Scanning down from the player's own altitude instead finds the floor of
        // whatever room/cave/cavern they're in, matching how other cave-aware minimaps behave.
        int startY = Math.max(playerY, minY + 1);

        int[] heights = new int[TILE_SIZE * TILE_SIZE];
        BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
        for (int lz = 0; lz < TILE_SIZE; lz++) {
            for (int lx = 0; lx < TILE_SIZE; lx++) {
                heights[lz * TILE_SIZE + lx] = surfaceHeight(chunk, scan, baseX + lx, baseZ + lz, startY, minY);
            }
        }

        int[] colors = new int[TILE_SIZE * TILE_SIZE];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lz = 0; lz < TILE_SIZE; lz++) {
            for (int lx = 0; lx < TILE_SIZE; lx++) {
                int height = heights[lz * TILE_SIZE + lx];
                if (height <= minY) {
                    colors[lz * TILE_SIZE + lx] = 0;
                    continue;
                }
                pos.set(baseX + lx, height - 1, baseZ + lz);
                BlockState state = chunk.getBlockState(pos);
                MapColor color = state.getMapColor(level, pos);
                if (color == MapColor.NONE) {
                    colors[lz * TILE_SIZE + lx] = 0;
                    continue;
                }
                int northHeight = lz > 0 ? heights[(lz - 1) * TILE_SIZE + lx] : height;
                MapColor.Brightness brightness = northHeight > height ? MapColor.Brightness.LOW
                        : northHeight < height ? MapColor.Brightness.HIGH : MapColor.Brightness.NORMAL;
                colors[lz * TILE_SIZE + lx] = color.calculateARGBColor(brightness);
            }
        }
        return colors;
    }

    /** First non-air block at or below {@code startY} in this column, in vanilla heightmap convention
     * (one above the solid block), or {@code minY} if the column is air all the way down from there. */
    private static int surfaceHeight(ChunkAccess chunk, BlockPos.MutableBlockPos scan, int x, int z, int startY, int minY) {
        for (int y = startY; y > minY; y--) {
            scan.set(x, y, z);
            if (!chunk.getBlockState(scan).isAir()) {
                return y + 1;
            }
        }
        return minY;
    }
}
