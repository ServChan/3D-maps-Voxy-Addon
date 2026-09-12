package com.voxymap.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Drives {@link RoomCutawayComputer} off the player's real position while the 3D map is open, throttled
 * so the flood fill only reruns when the player has actually moved (or periodically as a safety net),
 * and pushes the result to {@link RoomCutawayTexture} for the patched Voxy shaders to sample.
 */
public final class RoomCutawayController {
    private static final long ENCLOSED_RECOMPUTE_INTERVAL_NANOS = 2_000_000_000L;
    private static final long OPEN_RECOMPUTE_INTERVAL_NANOS = 10_000_000_000L;
    private static final int SCAN_NODE_BUDGET_PER_TICK = 128;
    private static final int RECOMPUTE_MOVE_THRESHOLD = 2;
    // The Nether's solid bedrock roof blocks the elevated map camera outright no matter where the
    // player stands, so it is stripped unconditionally - not just inside a locally detected room.
    private static final float NETHER_ROOF_STRIP_MARGIN = 8.0f;

    private static RoomCutawayComputer.Result result = RoomCutawayComputer.Result.OPEN;
    private static long lastComputeNanos = 0L;
    private static boolean hasComputed = false;
    private static int lastFeetX;
    private static int lastFeetY;
    private static int lastFeetZ;
    private static boolean cuttingSomething;
    private static RoomCutawayComputer.Result uploadedResult;
    private static float uploadedRoofCutoff = Float.NaN;
    private static RoomCutawayComputer.Search pendingSearch;
    private static String lastDimensionId;

    private RoomCutawayController() {
    }

    /** Called from the screen tick, never from the per-frame render-state extraction path. */
    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            deactivate();
            return;
        }

        if (VoxyMapSettings.roomCutawayEnabled()) {
            recomputeRoomIfNeeded(minecraft);
        } else {
            result = RoomCutawayComputer.Result.OPEN;
            hasComputed = false;
            pendingSearch = null;
        }

        Level level = minecraft.level;
        String dimensionId = level.dimension().identifier().toString();
        boolean isNether = "minecraft:the_nether".equals(dimensionId);
        float netherRoofCutoff = isNether
                ? (float) (level.dimensionType().minY() + level.dimensionType().logicalHeight()) - NETHER_ROOF_STRIP_MARGIN
                : RoomCutawayTexture.NO_CUT_VALUE;

        cuttingSomething = result.enclosed || isNether;
        if (cuttingSomething) {
            if (!RoomCutawayTexture.isEnabled() || uploadedResult != result || uploadedRoofCutoff != netherRoofCutoff) {
                RoomCutawayTexture.update(result, netherRoofCutoff);
                uploadedResult = result;
                uploadedRoofCutoff = netherRoofCutoff;
            }
        } else {
            RoomCutawayTexture.disable();
        }
    }

    private static void recomputeRoomIfNeeded(Minecraft minecraft) {
        BlockPos feet = minecraft.player.blockPosition();
        long now = System.nanoTime();
        String dimensionId = minecraft.level.dimension().identifier().toString();
        if (!dimensionId.equals(lastDimensionId)) {
            lastDimensionId = dimensionId;
            result = RoomCutawayComputer.Result.OPEN;
            hasComputed = false;
            pendingSearch = null;
        }
        boolean moved = !hasComputed
                || Math.abs(feet.getX() - lastFeetX) >= RECOMPUTE_MOVE_THRESHOLD
                || Math.abs(feet.getY() - lastFeetY) >= RECOMPUTE_MOVE_THRESHOLD
                || Math.abs(feet.getZ() - lastFeetZ) >= RECOMPUTE_MOVE_THRESHOLD;
        long interval = result.enclosed ? ENCLOSED_RECOMPUTE_INTERVAL_NANOS : OPEN_RECOMPUTE_INTERVAL_NANOS;
        boolean stale = (now - lastComputeNanos) >= interval;

        if (moved || (pendingSearch == null && stale)) {
            pendingSearch = RoomCutawayComputer.start(feet.getX(), feet.getY(), feet.getZ());
            lastFeetX = feet.getX();
            lastFeetY = feet.getY();
            lastFeetZ = feet.getZ();
            hasComputed = true;
        }
        if (pendingSearch == null) {
            return;
        }

        Level level = minecraft.level;
        boolean complete = pendingSearch.advance((x, y, z) -> {
            BlockPos pos = new BlockPos(x, y, z);
            // A server can withhold or fake chunk data it hasn't sent yet (low view distance,
            // anti-xray obfuscation, ...) - client-side block state there cannot be trusted, so
            // never let the flood fill treat it as open air.
            if (!level.isLoaded(pos)) {
                return RoomCutawayComputer.UNKNOWN;
            }
            return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    ? RoomCutawayComputer.PASSABLE
                    : RoomCutawayComputer.BLOCKED;
        }, SCAN_NODE_BUDGET_PER_TICK);
        if (complete) {
            result = pendingSearch.result();
            pendingSearch = null;
            lastComputeNanos = now;
        }
    }

    /** Whether the cutaway texture currently discards anything (local room, Nether roof strip, or both). */
    public static boolean isActive() {
        return VoxyMapCameraController.isActive() && cuttingSomething;
    }

    public static void deactivate() {
        result = RoomCutawayComputer.Result.OPEN;
        hasComputed = false;
        cuttingSomething = false;
        uploadedResult = null;
        uploadedRoofCutoff = Float.NaN;
        pendingSearch = null;
        lastDimensionId = null;
        RoomCutawayTexture.disable();
    }
}
