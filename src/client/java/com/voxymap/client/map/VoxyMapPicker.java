package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.core.Direction;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Performs exact cursor picking against Voxy's explored level-0 voxel data. */
public final class VoxyMapPicker {
    private static final ExecutorService PICKER_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "VoxyMap surface picker");
        thread.setDaemon(true);
        return thread;
    });

    private VoxyMapPicker() {
    }

    public record Ray(double originX, double originY, double originZ,
                      double directionX, double directionY, double directionZ) {
    }

    public record Hit(double x, double y, double z, int blockX, int blockY, int blockZ, Direction face) {
    }

    public static CompletableFuture<Hit> pickAsync(WorldIdentifier worldId, Ray ray, double maxDistance) {
        if (worldId == null || ray == null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.supplyAsync(() -> pick(worldId, ray, maxDistance), PICKER_EXECUTOR);
    }

    private static Hit pick(WorldIdentifier worldId, Ray ray, double maxDistance) {
        WorldEngine engine = VoxySectionBridge.liveEngine(worldId);
        if (engine == null) return null;

        try (SectionReader reader = new SectionReader(engine)) {
            VoxelRayPicker.Hit hit = VoxelRayPicker.pick(
                    ray.originX(), ray.originY(), ray.originZ(),
                    ray.directionX(), ray.directionY(), ray.directionZ(), maxDistance,
                    (x, y, z) -> !RoomCutawayController.isHiddenForMapPick(x, y, z) && reader.occupied(x, y, z));
            return hit == null ? null : new Hit(hit.x(), hit.y(), hit.z(), hit.blockX(), hit.blockY(),
                    hit.blockZ(), hit.face());
        } catch (RuntimeException | LinkageError exception) {
            // Input can race a dimension unload or a Voxy engine replacement. A missed click is much
            // safer than letting an internal lifecycle mismatch escape the screen event handler.
            VoxyMapClient.LOGGER.warn("[VoxyMap] Could not pick the Voxy surface under the cursor.", exception);
            return null;
        }
    }

    private static final class SectionReader implements AutoCloseable {
        private final WorldEngine engine;
        private WorldSection section;
        private long[] data;
        private int sectionX = Integer.MIN_VALUE;
        private int sectionY = Integer.MIN_VALUE;
        private int sectionZ = Integer.MIN_VALUE;

        private SectionReader(WorldEngine engine) {
            this.engine = engine;
        }

        private boolean occupied(int x, int y, int z) {
            if (y < -4096 || y >= 4096) return false;
            int sx = Math.floorDiv(x, 32);
            int sy = Math.floorDiv(y, 32);
            int sz = Math.floorDiv(z, 32);
            if (sx != sectionX || sy != sectionY || sz != sectionZ) {
                select(sx, sy, sz);
            }
            if (data == null) return false;
            int index = WorldSection.getIndex(Math.floorMod(x, 32), Math.floorMod(y, 32), Math.floorMod(z, 32));
            return !Mapper.isAir(data[index]);
        }

        private void select(int x, int y, int z) {
            closeCurrent();
            sectionX = x;
            sectionY = y;
            sectionZ = z;
            section = engine.acquireIfExists(0, x, y, z);
            data = section == null ? null : section._unsafeGetRawDataArray();
        }

        private void closeCurrent() {
            if (section != null) {
                section.release();
                section = null;
                data = null;
            }
        }

        @Override
        public void close() {
            closeCurrent();
        }
    }
}
