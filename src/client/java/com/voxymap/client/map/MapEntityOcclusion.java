package com.voxymap.client.map;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class MapEntityOcclusion {
    static final double MAX_RAY_BLOCKS = 96.0;
    static final double OCCLUDING_VOLUME = 0.5;
    private static final double[] SAMPLE_HEIGHTS = {0.9, 0.5};
    private static final long REUSE_NANOS = 250_000_000L;
    private static final int MAX_CACHED = 4096;
    private static final double CAMERA_STEP_DIVISOR = 16.0;
    private static final Int2ObjectOpenHashMap<Result> RESULTS = new Int2ObjectOpenHashMap<>();
    private static final Reference2BooleanOpenHashMap<BlockState> OCCLUDERS = new Reference2BooleanOpenHashMap<>();

    private record Result(long key, long time, boolean visible) {
    }

    private MapEntityOcclusion() {
    }

    public static boolean visible(Entity entity) {
        if (!VoxyMapCameraController.isActive()) {
            if (!RESULTS.isEmpty()) {
                RESULTS.clear();
            }
            return true;
        }
        if (MapViewMode.flat()) {
            return true;
        }
        double cameraX = VoxyMapCameraController.cameraX();
        double cameraY = VoxyMapCameraController.cameraY();
        double cameraZ = VoxyMapCameraController.cameraZ();
        long key = key(entity, cameraX, cameraY, cameraZ);
        long now = System.nanoTime();
        Result cached = RESULTS.get(entity.getId());
        if (cached != null && cached.key() == key && now - cached.time() < REUSE_NANOS) {
            return cached.visible();
        }
        boolean visible = compute(entity, cameraX, cameraY, cameraZ);
        if (RESULTS.size() >= MAX_CACHED) {
            RESULTS.clear();
        }
        RESULTS.put(entity.getId(), new Result(key, now, visible));
        return visible;
    }

    private static boolean compute(Entity entity, double cameraX, double cameraY, double cameraZ) {
        Level level = entity.level();
        boolean roofCut = NetherRoofController.isActive();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        VoxelRayPicker.Occupancy occupancy = (x, y, z) -> !(roofCut && NetherRoofController.isHiddenForMapPick(x, y, z))
                && occludes(level.getBlockState(pos.set(x, y, z)));
        double height = entity.getBbHeight();
        for (double sample : SAMPLE_HEIGHTS) {
            if (clearTowards(entity.getX(), entity.getY() + height * sample, entity.getZ(),
                    cameraX, cameraY, cameraZ, MAX_RAY_BLOCKS, occupancy)) {
                return true;
            }
        }
        return false;
    }

    static boolean clearTowards(double x, double y, double z, double cameraX, double cameraY, double cameraZ,
                                double maxBlocks, VoxelRayPicker.Occupancy occupancy) {
        double dx = cameraX - x;
        double dy = cameraY - y;
        double dz = cameraZ - z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(distance > 0.0)) {
            return true;
        }
        return VoxelRayPicker.pick(x, y, z, dx, dy, dz, Math.min(distance, maxBlocks), occupancy) == null;
    }

    private static boolean occludes(BlockState state) {
        if (state.isAir() || !state.canOcclude() || state.getBlock() instanceof LiquidBlock) {
            return false;
        }
        if (OCCLUDERS.containsKey(state)) {
            return OCCLUDERS.getBoolean(state);
        }
        double volume = 0.0;
        for (AABB box : state.getOcclusionShape().toAabbs()) {
            volume += box.getXsize() * box.getYsize() * box.getZsize();
        }
        boolean occludes = coversCell(volume);
        OCCLUDERS.put(state, occludes);
        return occludes;
    }

    static boolean coversCell(double volume) {
        return volume >= OCCLUDING_VOLUME;
    }

    private static long key(Entity entity, double cameraX, double cameraY, double cameraZ) {
        double distance = Math.sqrt(entity.distanceToSqr(cameraX, cameraY, cameraZ));
        double cameraStep = Math.max(1.0, distance / CAMERA_STEP_DIVISOR);
        long hash = 17;
        hash = hash * 31 + Math.round(entity.getX() * 4.0);
        hash = hash * 31 + Math.round(entity.getY() * 4.0);
        hash = hash * 31 + Math.round(entity.getZ() * 4.0);
        hash = hash * 31 + Math.round(cameraX / cameraStep);
        hash = hash * 31 + Math.round(cameraY / cameraStep);
        hash = hash * 31 + Math.round(cameraZ / cameraStep);
        return hash;
    }
}
