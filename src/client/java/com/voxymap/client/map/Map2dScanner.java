package com.voxymap.client.map;

import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;

import java.util.HashMap;
import java.util.Map;

final class Map2dScanner {
    private static final int SEEK_AIR = 0;
    private static final int SEARCH = 1;
    private static final int UNDER_WATER = 2;
    private static final int DONE = 3;
    private static final long WATER_FLAG = 1L << 40;

    record Area(int level, int tileX, int tileZ, int minY, int maxYExclusive, int slice) {
        boolean surface() {
            return slice == Map2dGeometry.SURFACE;
        }
    }

    private final WorldEngine engine;
    private final Registry<Biome> biomes;
    private final Map<Long, Long> colors = new HashMap<>();
    private final Map<Integer, Biome> biomesById = new HashMap<>();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private Map2dTintGetter tint;

    Map2dScanner(WorldEngine engine, Registry<Biome> biomes) {
        this.engine = engine;
        this.biomes = biomes;
    }

    int[] scan(Area area) {
        int level = area.level();
        tint = new Map2dTintGetter(area.minY(), area.maxYExclusive() - area.minY());
        int size = Map2dGeometry.TILE_PIXELS;
        int[] argb = new int[size * size];
        int[] heights = new int[size * size];
        int perSection = Map2dGeometry.SECTION_VOXELS;
        for (int sz = 0; sz < Map2dGeometry.SECTIONS_PER_TILE; sz++) {
            for (int sx = 0; sx < Map2dGeometry.SECTIONS_PER_TILE; sx++) {
                int sectionX = area.tileX() * Map2dGeometry.SECTIONS_PER_TILE + sx;
                int sectionZ = area.tileZ() * Map2dGeometry.SECTIONS_PER_TILE + sz;
                scanColumn(area, sectionX, sectionZ, argb, heights, sx * perSection, sz * perSection);
            }
        }
        shade(argb, heights, level);
        return argb;
    }

    private void scanColumn(Area area, int sectionX, int sectionZ, int[] argb, int[] heights, int pixelX, int pixelZ) {
        int level = area.level();
        int voxel = Map2dGeometry.voxelBlocks(level);
        int sectionBlocks = Map2dGeometry.sectionBlocks(level);
        int columns = Map2dGeometry.SECTION_VOXELS * Map2dGeometry.SECTION_VOXELS;
        int[] state = new int[columns];
        int[] solid = new int[columns];
        int[] water = new int[columns];
        int[] depth = new int[columns];
        int[] top = new int[columns];
        int startState = area.surface() ? SEARCH : SEEK_AIR;
        java.util.Arrays.fill(state, startState);
        int remaining = columns;
        int topSection = Map2dGeometry.highestSection(area.surface() ? area.maxYExclusive() : area.slice() + 1, level);
        int bottomSection = Map2dGeometry.lowestSection(area.minY(), level);
        int solidLimit = Map2dGeometry.caveSolidVoxels(level);
        int maxWaterVoxels = Math.max(1, MapColorMath.MAX_WATER_DEPTH / voxel);
        for (int sectionY = topSection; sectionY >= bottomSection && remaining > 0; sectionY--) {
            WorldSection section = engine.acquireIfExists(level, sectionX, sectionY, sectionZ);
            long[] data = section == null ? null : section._unsafeGetRawDataArray();
            try {
                for (int y = Map2dGeometry.SECTION_VOXELS - 1; y >= 0 && remaining > 0; y--) {
                    int blockY = sectionY * sectionBlocks + y * voxel;
                    if (!area.surface() && blockY > area.slice()) {
                        continue;
                    }
                    if (blockY < area.minY()) {
                        break;
                    }
                    for (int column = 0; column < columns; column++) {
                        int current = state[column];
                        if (current == DONE) {
                            continue;
                        }
                        long voxelData = data == null ? Mapper.AIR : data[(y << 10) | column];
                        long color = Mapper.isAir(voxelData) ? 0L : colorOf(voxelData, sectionX, sectionZ, column, level, blockY);
                        boolean empty = color == 0L;
                        boolean isWater = (color & WATER_FLAG) != 0L;
                        int rgb = (int) color;
                        switch (current) {
                            case SEEK_AIR -> {
                                if (empty) {
                                    state[column] = SEARCH;
                                } else if (++solid[column] > solidLimit) {
                                    state[column] = DONE;
                                    remaining--;
                                }
                            }
                            case SEARCH -> {
                                if (empty) {
                                    continue;
                                }
                                top[column] = blockY + voxel;
                                if (isWater) {
                                    water[column] = rgb;
                                    depth[column] = 1;
                                    state[column] = UNDER_WATER;
                                } else {
                                    store(argb, heights, pixelX, pixelZ, column, rgb, top[column]);
                                    state[column] = DONE;
                                    remaining--;
                                }
                            }
                            case UNDER_WATER -> {
                                if (empty) {
                                    continue;
                                }
                                if (isWater && depth[column] < maxWaterVoxels) {
                                    depth[column]++;
                                    continue;
                                }
                                int finalColor = isWater
                                        ? MapColorMath.scale(water[column], 0.8)
                                        : MapColorMath.lerp(rgb, water[column],
                                        MapColorMath.waterOpacity(depth[column] * voxel));
                                store(argb, heights, pixelX, pixelZ, column, finalColor, top[column]);
                                state[column] = DONE;
                                remaining--;
                            }
                            default -> {
                            }
                        }
                    }
                }
            } finally {
                if (section != null) {
                    section.release();
                }
            }
        }
        for (int column = 0; column < columns; column++) {
            if (state[column] == UNDER_WATER) {
                store(argb, heights, pixelX, pixelZ, column, MapColorMath.scale(water[column], 0.8), top[column]);
            }
        }
    }

    private static void store(int[] argb, int[] heights, int pixelX, int pixelZ, int column, int color, int height) {
        int x = pixelX + (column & 31);
        int z = pixelZ + (column >> 5);
        int index = z * Map2dGeometry.TILE_PIXELS + x;
        argb[index] = color;
        heights[index] = height;
    }

    private static void shade(int[] argb, int[] heights, int level) {
        int size = Map2dGeometry.TILE_PIXELS;
        for (int z = size - 1; z >= 0; z--) {
            for (int x = size - 1; x >= 0; x--) {
                int index = z * size + x;
                if ((argb[index] >>> 24) == 0) {
                    continue;
                }
                int height = heights[index];
                int north = z > 0 && (argb[index - size] >>> 24) != 0 ? heights[index - size] : height;
                int west = x > 0 && (argb[index - 1] >>> 24) != 0 ? heights[index - 1] : height;
                argb[index] = MapColorMath.scale(argb[index], Map2dGeometry.slopeFactor(height, north, west, level));
            }
        }
    }

    private long colorOf(long voxel, int sectionX, int sectionZ, int column, int level, int blockY) {
        int blockId = Mapper.getBlockId(voxel);
        int biomeId = Mapper.getBiomeId(voxel);
        long key = (long) blockId << 32 | (biomeId & 0xFFFFFFFFL);
        Long cached = colors.get(key);
        if (cached != null) {
            return cached;
        }
        long computed = computeColor(blockId, biomeId, sectionX, sectionZ, column, level, blockY);
        colors.put(key, computed);
        return computed;
    }

    private long computeColor(int blockId, int biomeId, int sectionX, int sectionZ, int column, int level, int blockY) {
        BlockState state = engine.getMapper().getBlockStateFromBlockId(blockId);
        if (state == null || state.isAir()) {
            return 0L;
        }
        Biome biome = biome(biomeId);
        int sectionBlocks = Map2dGeometry.sectionBlocks(level);
        int voxel = Map2dGeometry.voxelBlocks(level);
        pos.set(sectionX * sectionBlocks + (column & 31) * voxel, blockY, sectionZ * sectionBlocks + (column >> 5) * voxel);
        if (state.getFluidState().getType().isSame(Fluids.WATER)) {
            int water = biome == null ? MapColor.WATER.calculateARGBColor(MapColor.Brightness.NORMAL)
                    : MinimapBlockColors.waterColor(biome.getWaterColor());
            return WATER_FLAG | (water & 0xFFFFFFFFL);
        }
        tint.setBiome(biome);
        int color = MinimapBlockColors.blockColor(tint, state, pos);
        if (color == 0) {
            MapColor mapColor = state.getMapColor(tint, pos);
            if (mapColor == MapColor.NONE) {
                return 0L;
            }
            color = mapColor.calculateARGBColor(MapColor.Brightness.NORMAL);
        }
        return color & 0xFFFFFFFFL;
    }

    private Biome biome(int biomeId) {
        if (biomesById.containsKey(biomeId)) {
            return biomesById.get(biomeId);
        }
        Biome found = null;
        for (Mapper.BiomeEntry entry : engine.getMapper().getBiomeEntries()) {
            if (entry == null || entry.id != biomeId) {
                continue;
            }
            Identifier id = Identifier.tryParse(entry.biome);
            if (id != null) {
                found = biomes.get(id).map(Holder::value).orElse(null);
            }
            break;
        }
        biomesById.put(biomeId, found);
        return found;
    }
}
