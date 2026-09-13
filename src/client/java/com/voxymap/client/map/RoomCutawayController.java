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
    private static final int SCAN_NODE_BUDGET_PER_TICK = 1024;
    private static final int RECOMPUTE_MOVE_THRESHOLD = 2;
    private static RoomCutawayComputer.Result result = RoomCutawayComputer.Result.OPEN;
    private static long lastComputeNanos = 0L;
    private static boolean hasComputed = false;
    private static int lastFeetX;
    private static int lastFeetY;
    private static int lastFeetZ;
    private static boolean cuttingSomething;
    private static RoomCutawayComputer.Result uploadedResult;
    private static float uploadedGlobalCutoff = Float.NaN;
    private static RoomCutawayComputer.Search pendingSearch;
    private static String lastDimensionId;

    private RoomCutawayController() {
    }

    /** Updates cheap shader-only view parameters; safe to call whenever pan or zoom changes. */
    public static void updateViewContext(Minecraft minecraft, double viewX, double viewY, double viewZ,
                                         double blocksPerPixel, int viewportMinPixels) {
        if (minecraft == null || minecraft.level == null
                || !"minecraft:the_nether".equals(minecraft.level.dimension().identifier().toString())) {
            RoomCutawayTexture.clearAperture();
            return;
        }
        Level level = minecraft.level;
        NetherRoofPolicy.Aperture aperture = NetherRoofPolicy.aperture(
                level.dimensionType().minY(), level.dimensionType().logicalHeight(),
                viewY, blocksPerPixel, viewportMinPixels);
        RoomCutawayTexture.setAperture(viewX, viewZ, aperture.innerCutoff(), aperture.radius());
    }

    /** Called from the screen tick, never from the per-frame render-state extraction path. */
    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            deactivate();
            return;
        }

        Level level = minecraft.level;
        String dimensionId = level.dimension().identifier().toString();
        boolean isNether = "minecraft:the_nether".equals(dimensionId);
        boolean trustedLocalWorld = minecraft.isLocalServer();

        if (VoxyMapSettings.roomCutawayEnabled()) {
            recomputeRoomIfNeeded(minecraft, isNether && !trustedLocalWorld);
        } else {
            result = RoomCutawayComputer.Result.OPEN;
            hasComputed = false;
            pendingSearch = null;
        }

        // This threshold no longer slices through cached voxels. The shader removes only complete
        // quads wholly in the roof band, so it is safe on remote anti-xray caches as well.
        float netherRoofCutoff = isNether
                ? NetherRoofPolicy.cutoff(
                        level.dimensionType().minY(),
                        level.dimensionType().logicalHeight())
                : RoomCutawayTexture.NO_CUT_VALUE;

        cuttingSomething = result.hasCutaway() || netherRoofCutoff != RoomCutawayTexture.NO_CUT_VALUE;
        if (cuttingSomething) {
            if (!RoomCutawayTexture.isEnabled() || !result.equals(uploadedResult)) {
                RoomCutawayTexture.update(result, netherRoofCutoff);
                uploadedResult = result;
            } else if (uploadedGlobalCutoff != netherRoofCutoff) {
                // A dimension cutoff change updates only one shader uniform. Do not allocate and
                // re-upload the 97x97 room texture when its local connectivity result is unchanged.
                RoomCutawayTexture.setUniformCutoff(netherRoofCutoff);
            }
            uploadedGlobalCutoff = netherRoofCutoff;
        } else {
            RoomCutawayTexture.disable();
        }
    }

    private static void recomputeRoomIfNeeded(Minecraft minecraft, boolean retainVerifiedPartial) {
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
        long interval = result.hasCutaway() ? ENCLOSED_RECOMPUTE_INTERVAL_NANOS : OPEN_RECOMPUTE_INTERVAL_NANOS;
        boolean stale = (now - lastComputeNanos) >= interval;

        if (moved || (pendingSearch == null && stale)) {
            pendingSearch = retainVerifiedPartial
                    ? RoomCutawayComputer.startVerifiedPartial(feet.getX(), feet.getY(), feet.getZ())
                    : RoomCutawayComputer.start(feet.getX(), feet.getY(), feet.getZ());
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

    /** Whether the cutaway texture currently discards anything (verified room or trusted local roof). */
    public static boolean isActive() {
        return VoxyMapCameraController.isActive() && cuttingSomething;
    }

    public static void deactivate() {
        result = RoomCutawayComputer.Result.OPEN;
        hasComputed = false;
        cuttingSomething = false;
        uploadedResult = null;
        uploadedGlobalCutoff = Float.NaN;
        pendingSearch = null;
        lastDimensionId = null;
        RoomCutawayTexture.clearAperture();
        RoomCutawayTexture.disable();
    }
}
