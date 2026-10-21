package com.voxymap.client.nav;

import com.voxymap.client.map.VoxySectionBridge;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

final class NavTerrain {
    static final int SECTION_VOXELS = 32;
    static final int CEILING_HEADROOM = 24;
    private static final int COLUMNS = SECTION_VOXELS * SECTION_VOXELS;
    private static final byte SEEK_AIR = 0;
    private static final byte SEARCH = 1;
    private static final byte DONE = 2;

    record Request(WorldEngine engine, NavArea area, int minY, int maxYExclusive, boolean ceiling, int startY) {
    }

    private final Request request;
    private final Map<Integer, Byte> codes = new HashMap<>();

    private NavTerrain(Request request) {
        this.request = request;
    }

    static NavGrid scan(Request request, BooleanSupplier cancelled) {
        return new NavTerrain(request).run(cancelled);
    }

    private NavGrid run(BooleanSupplier cancelled) {
        NavArea area = request.area();
        NavGrid grid = NavGrid.empty(area.originX(), area.originZ(), area.cell(), area.width(), area.depth());
        int level = area.level();
        int sectionBlocks = SECTION_VOXELS << level;
        int firstSectionX = Math.floorDiv(area.originX(), sectionBlocks);
        int firstSectionZ = Math.floorDiv(area.originZ(), sectionBlocks);
        int lastSectionX = Math.floorDiv(area.originX() + area.width() * area.cell() - 1, sectionBlocks);
        int lastSectionZ = Math.floorDiv(area.originZ() + area.depth() * area.cell() - 1, sectionBlocks);
        for (int sectionZ = firstSectionZ; sectionZ <= lastSectionZ; sectionZ++) {
            for (int sectionX = firstSectionX; sectionX <= lastSectionX; sectionX++) {
                if (cancelled.getAsBoolean()) {
                    return null;
                }
                if (!VoxySectionBridge.holdRef(request.engine())) {
                    return null;
                }
                try {
                    scanColumn(grid, sectionX, sectionZ, sectionBlocks);
                } finally {
                    VoxySectionBridge.dropRef(request.engine());
                }
            }
        }
        return grid;
    }

    private void scanColumn(NavGrid grid, int sectionX, int sectionZ, int sectionBlocks) {
        int level = request.area().level();
        int voxel = 1 << level;
        byte[] state = new byte[COLUMNS];
        boolean[] canopy = new boolean[COLUMNS];
        int[] cellX = new int[COLUMNS];
        int[] cellZ = new int[COLUMNS];
        byte start = request.ceiling() ? SEEK_AIR : SEARCH;
        int remaining = 0;
        for (int column = 0; column < COLUMNS; column++) {
            int blockX = sectionX * sectionBlocks + (column & 31) * voxel;
            int blockZ = sectionZ * sectionBlocks + (column >> 5) * voxel;
            cellX[column] = Math.floorDiv(blockX - grid.originX(), voxel);
            cellZ[column] = Math.floorDiv(blockZ - grid.originZ(), voxel);
            if (grid.contains(cellX[column], cellZ[column])) {
                state[column] = start;
                remaining++;
            } else {
                state[column] = DONE;
            }
        }
        int topY = request.ceiling() ? Math.min(request.maxYExclusive(), request.startY() + CEILING_HEADROOM)
                : request.maxYExclusive();
        int topSection = Math.floorDiv(topY - 1, sectionBlocks);
        int bottomSection = Math.floorDiv(request.minY(), sectionBlocks);
        Mapper mapper = request.engine().getMapper();
        for (int sectionY = topSection; sectionY >= bottomSection && remaining > 0; sectionY--) {
            WorldSection section = request.engine().acquireIfExists(level, sectionX, sectionY, sectionZ);
            if (section == null) {
                for (int column = 0; column < COLUMNS; column++) {
                    if (state[column] == SEEK_AIR) {
                        state[column] = SEARCH;
                    }
                }
                continue;
            }
            try {
                long[] data = section._unsafeGetRawDataArray();
                for (int y = SECTION_VOXELS - 1; y >= 0 && remaining > 0; y--) {
                    int blockY = sectionY * sectionBlocks + y * voxel;
                    if (blockY >= topY) {
                        continue;
                    }
                    if (blockY < request.minY()) {
                        break;
                    }
                    for (int column = 0; column < COLUMNS; column++) {
                        byte current = state[column];
                        if (current == DONE) {
                            continue;
                        }
                        long value = data[(y << 10) | column];
                        byte code = Mapper.isAir(value) ? NavBlocks.PASS : code(mapper, Mapper.getBlockId(value));
                        if (current == SEEK_AIR) {
                            if (code == NavBlocks.PASS || code == NavBlocks.CANOPY) {
                                state[column] = SEARCH;
                            }
                            continue;
                        }
                        if (code == NavBlocks.PASS) {
                            continue;
                        }
                        if (code == NavBlocks.CANOPY) {
                            canopy[column] = true;
                            continue;
                        }
                        if (code == NavBlocks.TRUNK && canopy[column]) {
                            continue;
                        }
                        grid.set(cellX[column], cellZ[column], NavBlocks.surfaceKind(code, canopy[column]), blockY + voxel);
                        state[column] = DONE;
                        remaining--;
                    }
                }
            } finally {
                section.release();
            }
        }
    }

    private byte code(Mapper mapper, int blockId) {
        Byte cached = codes.get(blockId);
        if (cached != null) {
            return cached;
        }
        byte code = NavBlocks.classify(mapper.getBlockStateFromBlockId(blockId));
        codes.put(blockId, code);
        return code;
    }

    static boolean anyKnown(NavGrid grid) {
        for (byte kind : grid.kinds()) {
            if (kind != NavKind.UNKNOWN) {
                return true;
            }
        }
        return false;
    }
}
