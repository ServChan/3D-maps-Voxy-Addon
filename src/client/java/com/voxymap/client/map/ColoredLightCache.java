package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.VoxyInstance;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Builds a world-space RGB light field from emissive blocks already persisted in Voxy. The expensive
 * section walk and diffusion are entirely off-thread; the render thread only uploads a completed frame.
 */
public final class ColoredLightCache {
    public static final int GRID = 1024;
    public static final int CELL_SIZE = 4;
    public static final int SPAN = GRID * CELL_SIZE;
    private static final int RECENTER_STEP = 512;
    private static final int DIFFUSION_PASSES = 7;
    private static final float DIFFUSION_DECAY = 0.76f;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "VoxyMap RGB light cache");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicReference<Request> REQUESTED = new AtomicReference<>();
    private static final AtomicReference<Frame> READY = new AtomicReference<>();
    private static volatile boolean workerRunning;
    private static volatile Request completed;

    private ColoredLightCache() {
    }

    /** Called from MapScreen's render/tick path; it never performs storage or block-volume work. */
    public static void request(Minecraft minecraft, double centerX, double centerZ) {
        if (minecraft == null || minecraft.level == null) return;
        WorldIdentifier worldId = WorldIdentifier.of(minecraft.level);
        VoxyInstance instance = VoxyCommon.getInstance();
        WorldEngine engine = instance == null || worldId == null ? null : instance.getNullable(worldId);
        if (engine == null) return;

        int originX = alignOrigin(centerX);
        int originZ = alignOrigin(centerZ);
        Request previous = REQUESTED.get();
        if (previous != null && previous.worldId().equals(worldId)
                && previous.originX() == originX && previous.originZ() == originZ) return;

        Mapper mapper = engine.getMapper();
        SolasLightColor.Rgb[] colors = new SolasLightColor.Rgb[mapper.getBlockStateCount()];
        for (int id = 0; id < colors.length; id++) {
            BlockState state = mapper.getBlockStateFromBlockId(id);
            if (state == null || state.getLightEmission() <= 0) continue;
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            colors[id] = SolasLightColor.classify(blockId, state);
        }

        REQUESTED.set(new Request(worldId, engine, originX, originZ, colors));
        startWorker();
    }

    private static int alignOrigin(double center) {
        int alignedCenter = Math.floorDiv((int) Math.floor(center), RECENTER_STEP) * RECENTER_STEP;
        return alignedCenter - SPAN / 2;
    }

    private static synchronized void startWorker() {
        if (workerRunning) return;
        workerRunning = true;
        WORKER.execute(ColoredLightCache::workerLoop);
    }

    private static void workerLoop() {
        try {
            Request processed = null;
            while (true) {
                Request request = REQUESTED.get();
                if (request == null || request == processed) return;
                processed = request;
                Frame frame = build(request);
                if (REQUESTED.get() == request) {
                    READY.set(frame);
                    completed = request;
                }
            }
        } catch (RuntimeException exception) {
            VoxyMapClient.LOGGER.warn("[VoxyMap] Could not build the colored-light cache.", exception);
        } finally {
            synchronized (ColoredLightCache.class) {
                workerRunning = false;
                if (REQUESTED.get() != null && completed != REQUESTED.get()) startWorker();
            }
        }
    }

    private static Frame build(Request request) {
        float[] source = new float[GRID * GRID * 3];
        int minSectionX = Math.floorDiv(request.originX(), 32);
        int minSectionZ = Math.floorDiv(request.originZ(), 32);
        int maxSectionX = Math.floorDiv(request.originX() + SPAN - 1, 32);
        int maxSectionZ = Math.floorDiv(request.originZ() + SPAN - 1, 32);

        request.engine().storage.iteratePositions(0, key -> {
            if (REQUESTED.get() != request) return;
            int sectionX = WorldEngine.getX(key);
            int sectionZ = WorldEngine.getZ(key);
            if (sectionX < minSectionX || sectionX > maxSectionX
                    || sectionZ < minSectionZ || sectionZ > maxSectionZ) return;
            long[] data = VoxySectionBridge.exportSectionData(request.worldId(), key);
            if (data == null) return;
            for (int index = 0; index < data.length; index++) {
                int blockId = Mapper.getBlockId(data[index]);
                if (blockId < 0 || blockId >= request.colors().length) continue;
                SolasLightColor.Rgb color = request.colors()[blockId];
                if (color == null) continue;
                int worldX = (sectionX << 5) + (index & 31);
                int worldZ = (sectionZ << 5) + ((index >> 5) & 31);
                int pixelX = Math.floorDiv(worldX - request.originX(), CELL_SIZE);
                int pixelZ = Math.floorDiv(worldZ - request.originZ(), CELL_SIZE);
                if (pixelX < 0 || pixelX >= GRID || pixelZ < 0 || pixelZ >= GRID) continue;
                int out = (pixelZ * GRID + pixelX) * 3;
                source[out] = Math.max(source[out], color.r());
                source[out + 1] = Math.max(source[out + 1], color.g());
                source[out + 2] = Math.max(source[out + 2], color.b());
            }
        });

        float[] spread = diffuse(source);
        byte[] rgb = new byte[GRID * GRID * 3];
        for (int i = 0; i < rgb.length; i++) {
            rgb[i] = (byte) Math.round(Math.min(1.0f, spread[i]) * 255.0f);
        }
        VoxyMapClient.LOGGER.info("[VoxyMap] Colored-light cache ready around {}, {}.",
                request.originX() + SPAN / 2, request.originZ() + SPAN / 2);
        return new Frame(request.worldId(), request.originX(), request.originZ(), rgb);
    }

    /** Max diffusion keeps hue stable where differently-coloured lights overlap and is cheap to rebuild. */
    static float[] diffuse(float[] source) {
        float[] current = source;
        for (int pass = 0; pass < DIFFUSION_PASSES; pass++) {
            float[] next = current.clone();
            for (int z = 1; z < GRID - 1; z++) {
                for (int x = 1; x < GRID - 1; x++) {
                    int base = (z * GRID + x) * 3;
                    for (int channel = 0; channel < 3; channel++) {
                        float neighbour = Math.max(Math.max(current[base - 3 + channel], current[base + 3 + channel]),
                                Math.max(current[base - GRID * 3 + channel], current[base + GRID * 3 + channel]));
                        next[base + channel] = Math.max(current[base + channel], neighbour * DIFFUSION_DECAY);
                    }
                }
            }
            current = next;
        }
        return current;
    }

    public static Frame takeReady() {
        return READY.getAndSet(null);
    }

    private record Request(WorldIdentifier worldId, WorldEngine engine, int originX, int originZ,
                           SolasLightColor.Rgb[] colors) {
    }

    public record Frame(WorldIdentifier worldId, int originX, int originZ, byte[] rgb) {
    }
}
