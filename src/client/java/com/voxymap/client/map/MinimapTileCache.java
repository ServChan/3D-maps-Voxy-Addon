package com.voxymap.client.map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class MinimapTileCache {
    static final int TILE_SIZE = 16;
    static final int Y_TOLERANCE_BLOCKS = 8;
    static final int ROOF_CLEARANCE_BLOCKS = 3;
    static final int CAVE_SOLID_SKIP_BLOCKS = 8;

    enum ScanMode { SURFACE, CAVE }

    private record Tile(int[] colors, ScanMode mode, int playerY) {
    }

    private final Map<Long, Tile> tiles = new ConcurrentHashMap<>();

    private Level currentLevel;

    void forget() {
        tiles.clear();
        currentLevel = null;
    }

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
            return cached == null || cached.mode() != mode ? null : cached.colors();
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

    void clear() {
        tiles.clear();
    }

    boolean isFreshFor(int chunkX, int chunkZ, ScanMode mode, int playerY) {
        Tile cached = tiles.get(key(chunkX, chunkZ));
        return cached != null && isFresh(cached.mode(), cached.playerY(), mode, playerY);
    }

    int[] cached(int chunkX, int chunkZ) {
        Tile cached = tiles.get(key(chunkX, chunkZ));
        return cached == null ? null : cached.colors();
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

    static int[] computeTile(Level level, ChunkAccess chunk, int chunkX, int chunkZ, ScanMode mode, int playerY) {
        boolean textured = VoxyMapSettings.minimapColors() == VoxyMapSettings.MinimapColors.TEXTURES;
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int minY = level.getMinY();
        int caveStartY = Math.max(playerY, minY + 1);
        int stride = TILE_SIZE + 1;

        ChunkAccess north = level.getChunk(chunkX, chunkZ - 1, ChunkStatus.FULL, false);
        ChunkAccess west = level.getChunk(chunkX - 1, chunkZ, ChunkStatus.FULL, false);
        int[] heights = new int[stride * stride];
        BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
        for (int lz = -1; lz < TILE_SIZE; lz++) {
            for (int lx = -1; lx < TILE_SIZE; lx++) {
                if (lz < 0 && lx < 0) {
                    continue;
                }
                ChunkAccess source = lz < 0 ? north : lx < 0 ? west : chunk;
                int index = (lz + 1) * stride + lx + 1;
                if (source == null) {
                    heights[index] = Integer.MIN_VALUE;
                    continue;
                }
                heights[index] = columnHeight(level, source, scan, baseX + lx, baseZ + lz, mode, caveStartY, minY);
            }
        }
        for (int i = 1; i < stride; i++) {
            if (heights[i] == Integer.MIN_VALUE) {
                heights[i] = heights[stride + i];
            }
            if (heights[i * stride] == Integer.MIN_VALUE) {
                heights[i * stride] = heights[i * stride + 1];
            }
        }

        int[] colors = new int[TILE_SIZE * TILE_SIZE];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lz = 0; lz < TILE_SIZE; lz++) {
            for (int lx = 0; lx < TILE_SIZE; lx++) {
                int height = heights[(lz + 1) * stride + lx + 1];
                int northHeight = heights[lz * stride + lx + 1];
                int westHeight = heights[(lz + 1) * stride + lx];
                colors[lz * TILE_SIZE + lx] = height <= minY ? 0 : textured
                        ? texturedColor(level, chunk, pos, baseX + lx, height, baseZ + lz, minY, northHeight, westHeight)
                        : classicColor(level, chunk, pos, baseX + lx, height, baseZ + lz, northHeight);
            }
        }
        Set<MapOverlay> overlays = VoxyMapSettings.mapOverlays();
        if (!overlays.isEmpty()) {
            applyOverlays(level, chunk, chunkX, chunkZ, heights, stride, minY, colors, overlays);
        }
        return colors;
    }

    private static void applyOverlays(Level level, ChunkAccess chunk, int chunkX, int chunkZ, int[] heights, int stride,
                                      int minY, int[] colors, Set<MapOverlay> overlays) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        if (overlays.contains(MapOverlay.BIOMES)) {
            for (int qz = 0; qz < TILE_SIZE / 4; qz++) {
                for (int qx = 0; qx < TILE_SIZE / 4; qx++) {
                    int sampleX = qx * 4 + 2;
                    int sampleZ = qz * 4 + 2;
                    int height = Math.max(heights[(sampleZ + 1) * stride + sampleX + 1], minY + 1);
                    pos.set(baseX + sampleX, height, baseZ + sampleZ);
                    int biome = level.getBiome(pos).unwrapKey()
                            .map(key -> MinimapOverlays.biomeColor(key.identifier().toString())).orElse(0);
                    if (biome == 0) {
                        continue;
                    }
                    for (int lz = qz * 4; lz < qz * 4 + 4; lz++) {
                        for (int lx = qx * 4; lx < qx * 4 + 4; lx++) {
                            int index = lz * TILE_SIZE + lx;
                            if (colors[index] != 0) {
                                colors[index] = MapColorMath.lerp(colors[index], biome, 0.45);
                            }
                        }
                    }
                }
            }
        }
        if (overlays.contains(MapOverlay.SLIME_CHUNKS)) {
            Long seed = MinimapOverlays.slimeSeed(level);
            if (seed != null && MinimapOverlays.isSlimeChunk(seed, chunkX, chunkZ)) {
                for (int lz = 0; lz < TILE_SIZE; lz++) {
                    for (int lx = 0; lx < TILE_SIZE; lx++) {
                        int index = lz * TILE_SIZE + lx;
                        boolean border = lx == 0 || lz == 0 || lx == TILE_SIZE - 1 || lz == TILE_SIZE - 1;
                        colors[index] = MapColorMath.lerp(colors[index] == 0 ? 0xFF101820 : colors[index],
                                MinimapOverlays.SLIME_COLOR, border ? 0.65 : 0.3);
                    }
                }
            }
        }
        if (overlays.contains(MapOverlay.SPAWN_LIGHT)) {
            BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
            for (int lz = 0; lz < TILE_SIZE; lz++) {
                for (int lx = 0; lx < TILE_SIZE; lx++) {
                    int index = lz * TILE_SIZE + lx;
                    int height = heights[(lz + 1) * stride + lx + 1];
                    if (colors[index] == 0 || height <= minY) {
                        continue;
                    }
                    pos.set(baseX + lx, height - 1, baseZ + lz);
                    above.set(baseX + lx, height, baseZ + lz);
                    if (mobsCanSpawn(level, chunk, pos, above)) {
                        colors[index] = MapColorMath.lerp(colors[index], MinimapOverlays.SPAWN_COLOR,
                                ((lx + lz) & 1) == 0 ? 0.7 : 0.3);
                    }
                }
            }
        }
    }

    private static boolean mobsCanSpawn(Level level, ChunkAccess chunk, BlockPos floor, BlockPos above) {
        BlockState aboveState = chunk.getBlockState(above);
        if (!aboveState.getFluidState().isEmpty() || !aboveState.getCollisionShape(level, above).isEmpty()) {
            return false;
        }
        if (!chunk.getBlockState(floor).isValidSpawn(level, floor, EntityTypes.ZOMBIE)) {
            return false;
        }
        return level.getBrightness(LightLayer.BLOCK, above) == 0;
    }

    private static int columnHeight(Level level, ChunkAccess source, BlockPos.MutableBlockPos scan, int x, int z,
                                    ScanMode mode, int caveStartY, int minY) {
        int startY = mode == ScanMode.SURFACE
                ? Math.max(source.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), minY + 1)
                : caveStartY;
        AirCheck air = (sx, sy, sz) -> {
            BlockState state = source.getBlockState(scan.set(sx, sy, sz));
            return state.isAir() || state.getMapColor(level, scan) == MapColor.NONE;
        };
        return mode == ScanMode.SURFACE ? surfaceHeight(air, x, z, startY, minY)
                : caveFloorHeight(air, x, z, startY, minY);
    }

    private static int classicColor(Level level, ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int height,
                                    int z, int northHeight) {
        pos.set(x, height - 1, z);
        MapColor color = chunk.getBlockState(pos).getMapColor(level, pos);
        if (color == MapColor.NONE) {
            return 0;
        }
        return color.calculateARGBColor(toMapColorBrightness(shadeOf(height, northHeight)));
    }

    private static int texturedColor(Level level, ChunkAccess chunk, BlockPos.MutableBlockPos pos, int x, int height,
                                     int z, int minY, int northHeight, int westHeight) {
        pos.set(x, height - 1, z);
        BlockState state = chunk.getBlockState(pos);
        int color;
        if (isWater(state)) {
            int water = MinimapBlockColors.waterColor(level, pos);
            if (water == 0) {
                water = mapColor(level, state, pos);
            }
            int depth = 1;
            int floorY = height - 2;
            while (floorY > minY && depth < MapColorMath.MAX_WATER_DEPTH
                    && isWater(chunk.getBlockState(pos.set(x, floorY, z)))) {
                depth++;
                floorY--;
            }
            BlockState floor = chunk.getBlockState(pos.set(x, floorY, z));
            int floorColor = floorY > minY && !isWater(floor) ? baseColor(level, floor, pos) : 0;
            color = floorColor == 0 ? MapColorMath.scale(water, 0.8)
                    : MapColorMath.lerp(floorColor, water, MapColorMath.waterOpacity(depth));
        } else {
            color = baseColor(level, state, pos);
        }
        if (color == 0) {
            return 0;
        }
        return MapColorMath.scale(color, MapColorMath.slopeFactor(height, northHeight, westHeight));
    }

    private static int baseColor(Level level, BlockState state, BlockPos pos) {
        int textured = MinimapBlockColors.blockColor(level, state, pos);
        return textured != 0 ? textured : mapColor(level, state, pos);
    }

    private static int mapColor(Level level, BlockState state, BlockPos pos) {
        MapColor color = state.getMapColor(level, pos);
        return color == MapColor.NONE ? 0 : color.calculateARGBColor(MapColor.Brightness.NORMAL);
    }

    private static boolean isWater(BlockState state) {
        return state.getFluidState().getType().isSame(Fluids.WATER);
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

    static int caveFloorHeight(AirCheck airCheck, int x, int z, int startY, int minY) {
        int y = startY;
        while (y > minY && !airCheck.isAir(x, y, z)) {
            if (startY - y >= CAVE_SOLID_SKIP_BLOCKS) {
                return minY;
            }
            y--;
        }
        return y <= minY ? minY : surfaceHeight(airCheck, x, z, y, minY);
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
