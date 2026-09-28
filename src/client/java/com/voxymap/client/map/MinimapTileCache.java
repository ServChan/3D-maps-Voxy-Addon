package com.voxymap.client.map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class MinimapTileCache {
    static final int TILE_SIZE = 16;
    static final int Y_TOLERANCE_BLOCKS = 8;
    static final int ROOF_CLEARANCE_BLOCKS = 3;

    enum ScanMode { SURFACE, CAVE }

    private record Tile(int[] colors, ScanMode mode, int playerY) {
    }

    private final Map<Long, Tile> tiles = new ConcurrentHashMap<>();

    private Level currentLevel;

    int[] tileFor(Level level, int chunkX, int chunkZ, ScanMode mode, int playerY, boolean forceRefresh) {
        if (level != currentLevel) {
            tiles.clear();
            currentLevel = level;
        }
        long key = key(chunkX, chunkZ);
        Tile cached = tiles.get(key);
        if (!forceRefresh && cached != null && isFresh(cached.mode(), cached.playerY(), mode, playerY)) {
            return cached.colors();
        }

        ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) {
            return cached == null ? null : cached.colors();
        }

        int[] colors = computeTile(level, chunk, chunkX, chunkZ, mode, playerY);
        tiles.put(key, new Tile(colors, mode, playerY));
        return colors;
    }

    void retainWithin(int centerChunkX, int centerChunkZ, int radiusChunks) {
        tiles.keySet().removeIf(key -> Math.abs(keyX(key) - centerChunkX) > radiusChunks
                || Math.abs(keyZ(key) - centerChunkZ) > radiusChunks);
    }

    int size() {
        return tiles.size();
    }

    static boolean isFresh(ScanMode cachedMode, int cachedPlayerY, ScanMode mode, int playerY) {
        if (cachedMode != mode) return false;
        return mode == ScanMode.SURFACE || Math.abs(cachedPlayerY - playerY) < Y_TOLERANCE_BLOCKS;
    }

    static ScanMode scanMode(boolean dimensionHasCeiling, int playerY, int solidRoofTopY) {
        if (dimensionHasCeiling) return ScanMode.CAVE;
        return playerY + ROOF_CLEARANCE_BLOCKS < solidRoofTopY ? ScanMode.CAVE : ScanMode.SURFACE;
    }

    static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    static int keyX(long key) {
        return (int) (key >> 32);
    }

    static int keyZ(long key) {
        return (int) key;
    }

    private static int[] computeTile(Level level, ChunkAccess chunk, int chunkX, int chunkZ, ScanMode mode, int playerY) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int minY = level.getMinY();

        int caveStartY = Math.max(playerY, minY + 1);

        int[] heights = new int[TILE_SIZE * TILE_SIZE];
        BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
        for (int lz = 0; lz < TILE_SIZE; lz++) {
            for (int lx = 0; lx < TILE_SIZE; lx++) {
                int x = baseX + lx;
                int z = baseZ + lz;
                int startY = mode == ScanMode.SURFACE
                        ? Math.max(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), minY + 1)
                        : caveStartY;
                heights[lz * TILE_SIZE + lx] = surfaceHeight((sx, sy, sz) -> {
                    BlockState state = chunk.getBlockState(scan.set(sx, sy, sz));
                    return state.isAir() || state.getMapColor(level, scan) == MapColor.NONE;
                }, x, z, startY, minY);
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
                colors[lz * TILE_SIZE + lx] = color.calculateARGBColor(toMapColorBrightness(shadeOf(height, northHeight)));
            }
        }
        return colors;
    }

    enum Shade { LOW, NORMAL, HIGH }

    static Shade shadeOf(int height, int northHeight) {
        if (northHeight > height) return Shade.LOW;
        if (northHeight < height) return Shade.HIGH;
        return Shade.NORMAL;
    }

    private static MapColor.Brightness toMapColorBrightness(Shade shade) {
        return switch (shade) {
            case LOW -> MapColor.Brightness.LOW;
            case NORMAL -> MapColor.Brightness.NORMAL;
            case HIGH -> MapColor.Brightness.HIGH;
        };
    }

    @FunctionalInterface
    interface AirCheck {
        boolean isAir(int x, int y, int z);
    }

    static int surfaceHeight(AirCheck airCheck, int x, int z, int startY, int minY) {
        for (int y = startY; y > minY; y--) {
            if (!airCheck.isAir(x, y, z)) {
                return y + 1;
            }
        }
        return minY;
    }
}
