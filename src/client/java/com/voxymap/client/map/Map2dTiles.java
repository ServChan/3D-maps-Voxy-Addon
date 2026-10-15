package com.voxymap.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.voxymap.client.VoxyMapClient;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class Map2dTiles {
    static final int MAX_TEXTURES = 192;
    private static final int MAX_IN_FLIGHT = 4;
    private static final int UPLOADS_PER_FRAME = 4;
    private static final int PIXEL_CACHE_SIZE = 48;
    private static final long CHANGE_DELAY_NANOS = 4_000_000_000L;
    private static final long RETRY_NANOS = 10_000_000_000L;

    public record TileKey(int level, int x, int z) {
    }

    private record Context(String worldId, int slice, WorldEngine engine, Registry<Biome> biomes, int minY,
                           int maxYExclusive, Path directory, long generation) {
    }

    private enum Source { MEMORY, DISK, SCAN }

    private record SliceKey(int slice, TileKey key) {
    }

    private record PixelKey(long generation, int slice, TileKey key) {
    }

    private record Result(SliceKey key, long generation, int[] pixels, Source source) {
    }

    private static final class Tile {
        Identifier texture;
        DynamicTexture dynamicTexture;
        boolean empty;
        boolean loading;
        boolean fresh;
        long staleAtNanos;
        long retryAtNanos;
        long lastUsed;
        long lastFrame = -1L;
    }

    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "VoxyMap 2D map");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY + 1);
        return thread;
    });

    private static final Map<SliceKey, Tile> TILES = new HashMap<>();
    private static final ConcurrentLinkedQueue<Result> RESULTS = new ConcurrentLinkedQueue<>();
    private static final Map<PixelKey, int[]> PIXELS = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<PixelKey, int[]> eldest) {
            return size() > PIXEL_CACHE_SIZE;
        }
    };
    private static volatile Context context;
    private static volatile long generation;
    private static AtomicInteger inFlight = new AtomicInteger();
    private static long useCounter;
    private static long frame;
    private static int readyTextures;
    private static int nextTextureId;
    private static volatile boolean failureLogged;

    private Map2dTiles() {
    }

    public static boolean prepare(Minecraft minecraft, int slice) {
        Level level = minecraft.level;
        if (level == null) {
            return false;
        }
        WorldIdentifier identifier = WorldIdentifier.of(level);
        WorldEngine engine = WorldIdentifier.ofEngineNullable(level);
        if (identifier == null || engine == null) {
            return false;
        }
        String worldId = identifier.getWorldId();
        Context current = context;
        if (current != null && current.worldId().equals(worldId) && current.engine() == engine) {
            if (current.slice() == slice) {
                return true;
            }
        } else {
            clear(minecraft);
        }
        Path directory = minecraft.gameDirectory.toPath().resolve("voxymap").resolve("map2d").resolve(worldId)
                .resolve(slice == Map2dGeometry.SURFACE ? "surface" : "cave" + slice);
        context = new Context(worldId, slice, engine, level.registryAccess().lookupOrThrow(Registries.BIOME),
                level.getMinY(), level.getMinY() + level.getHeight(), directory, generation);
        return true;
    }

    private static Tile tile(TileKey key) {
        Context current = context;
        return current == null ? null : TILES.get(new SliceKey(current.slice(), key));
    }

    public static Identifier texture(TileKey key) {
        Tile tile = tile(key);
        if (tile == null || tile.texture == null) {
            return null;
        }
        tile.lastUsed = ++useCounter;
        tile.lastFrame = frame;
        return tile.texture;
    }

    public static boolean knownEmpty(TileKey key) {
        Tile tile = tile(key);
        return tile != null && tile.empty;
    }

    public static boolean request(TileKey key) {
        Context current = context;
        if (current == null) {
            return false;
        }
        SliceKey sliceKey = new SliceKey(current.slice(), key);
        Tile tile = TILES.get(sliceKey);
        long now = System.nanoTime();
        boolean known = tile != null && (tile.texture != null || tile.empty);
        if (tile != null && (tile.loading || tile.retryAtNanos != 0L && now - tile.retryAtNanos < 0L)) {
            return false;
        }
        boolean stale = tile != null && tile.staleAtNanos != 0L && now - tile.staleAtNanos >= 0L;
        if (known && tile.fresh && !stale) {
            if (tile.texture != null) {
                tile.lastFrame = frame;
            }
            return false;
        }
        AtomicInteger counter = inFlight;
        if (counter.get() >= MAX_IN_FLIGHT) {
            return true;
        }
        if (tile == null) {
            tile = new Tile();
            TILES.put(sliceKey, tile);
        }
        boolean scan = known || stale;
        tile.loading = true;
        tile.staleAtNanos = 0L;
        tile.lastUsed = ++useCounter;
        counter.incrementAndGet();
        WORKERS.execute(() -> {
            try {
                Result result = load(current, sliceKey, scan);
                if (result != null && result.generation() == generation) {
                    RESULTS.add(result);
                }
            } catch (RuntimeException | LinkageError exception) {
                logFailure(exception);
                RESULTS.add(new Result(sliceKey, current.generation(), null, Source.SCAN));
            } finally {
                counter.decrementAndGet();
            }
        });
        return true;
    }

    public static int pending() {
        return inFlight.get();
    }

    public static void chunkChanged(int chunkX, int chunkZ) {
        Context current = context;
        if (TILES.isEmpty() || current == null) {
            return;
        }
        long staleAt = System.nanoTime() + CHANGE_DELAY_NANOS;
        for (int level = 0; level <= Map2dGeometry.MAX_LEVEL; level++) {
            int tileChunks = Map2dGeometry.tileBlocks(level) >> 4;
            markStale(new SliceKey(current.slice(),
                    new TileKey(level, Math.floorDiv(chunkX, tileChunks), Math.floorDiv(chunkZ, tileChunks))), staleAt);
        }
    }

    private static void markStale(SliceKey key, long staleAt) {
        Tile tile = TILES.get(key);
        if (tile != null && (tile.staleAtNanos == 0L || staleAt - tile.staleAtNanos < 0L)) {
            tile.staleAtNanos = staleAt;
        }
    }

    private static void markAncestorsStale(SliceKey key) {
        long now = System.nanoTime();
        TileKey tile = key.key();
        for (int up = 1; tile.level() + up <= Map2dGeometry.MAX_LEVEL; up++) {
            markStale(new SliceKey(key.slice(), new TileKey(tile.level() + up, tile.x() >> up, tile.z() >> up)), now);
        }
    }

    private static Result load(Context current, SliceKey sliceKey, boolean scan) {
        if (current.generation() != generation) {
            return null;
        }
        TileKey key = sliceKey.key();
        Source source = Source.SCAN;
        int[] pixels = scan ? null : readDisk(current, key);
        if (pixels != null) {
            source = Source.DISK;
        } else {
            pixels = build(current, key);
            if (pixels == null || current.generation() != generation) {
                return null;
            }
            writeDisk(current, key, pixels);
        }
        cachePut(current, key, pixels);
        return new Result(sliceKey, current.generation(), pixels, source);
    }

    private static int[] cacheGet(Context current, TileKey key) {
        synchronized (PIXELS) {
            return PIXELS.get(new PixelKey(current.generation(), current.slice(), key));
        }
    }

    private static void cachePut(Context current, TileKey key, int[] pixels) {
        synchronized (PIXELS) {
            if (current.generation() == generation) {
                PIXELS.put(new PixelKey(current.generation(), current.slice(), key), pixels);
            }
        }
    }

    private static int[] build(Context current, TileKey key) {
        if (current.generation() != generation) {
            return null;
        }
        if (key.level() <= Map2dGeometry.VOXY_MAX_LEVEL) {
            if (!VoxySectionBridge.holdRef(current.engine())) {
                return null;
            }
            try {
                return new Map2dScanner(current.engine(), current.biomes()).scan(new Map2dScanner.Area(key.level(),
                        key.x(), key.z(), current.minY(), current.maxYExclusive(), current.slice()));
            } finally {
                VoxySectionBridge.dropRef(current.engine());
            }
        }
        int childLevel = key.level() - 1;
        int[][] children = new int[4][];
        for (int i = 0; i < 4; i++) {
            TileKey child = new TileKey(childLevel, key.x() * 2 + (i & 1), key.z() * 2 + (i >> 1));
            children[i] = cachedOrBuilt(current, child);
            if (children[i] == null) {
                return null;
            }
        }
        return Map2dGeometry.downsample(children[0], children[1], children[2], children[3]);
    }

    private static int[] cachedOrBuilt(Context current, TileKey key) {
        int[] cached = cacheGet(current, key);
        if (cached != null) {
            return cached;
        }
        int[] pixels = readDisk(current, key);
        if (pixels == null) {
            pixels = build(current, key);
            if (pixels == null) {
                return null;
            }
            writeDisk(current, key, pixels);
        }
        cachePut(current, key, pixels);
        return pixels;
    }

    private static Path file(Context current, TileKey key) {
        return current.directory().resolve(Integer.toString(key.level())).resolve(key.x() + "_" + key.z() + ".png");
    }

    private static int[] readDisk(Context current, TileKey key) {
        Path path = file(current, key);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try (InputStream input = Files.newInputStream(path); NativeImage image = NativeImage.read(input)) {
            int size = Map2dGeometry.TILE_PIXELS;
            if (image.getWidth() != size || image.getHeight() != size) {
                return null;
            }
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    pixels[y * size + x] = image.getPixel(x, y);
                }
            }
            return pixels;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static void writeDisk(Context current, TileKey key, int[] pixels) {
        Path path = file(current, key);
        try {
            if (Map2dGeometry.empty(pixels)) {
                Files.deleteIfExists(path);
                return;
            }
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + "." + Thread.currentThread().threadId() + ".tmp");
            try (NativeImage image = toImage(pixels)) {
                image.writeToFile(temp);
            }
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException exception) {
            logFailure(exception);
        }
    }

    private static NativeImage toImage(int[] pixels) {
        int size = Map2dGeometry.TILE_PIXELS;
        NativeImage image = new NativeImage(size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.setPixel(x, y, pixels[y * size + x]);
            }
        }
        return image;
    }

    private static void logFailure(Throwable exception) {
        if (!failureLogged) {
            failureLogged = true;
            VoxyMapClient.LOGGER.warn("Could not build a 2D map tile from Voxy data", exception);
        }
    }

    public static void tick(Minecraft minecraft) {
        frame++;
        int uploads = 0;
        Result result;
        while (uploads < UPLOADS_PER_FRAME && (result = RESULTS.poll()) != null) {
            Tile tile = TILES.get(result.key());
            if (result.generation() != generation || tile == null) {
                continue;
            }
            tile.loading = false;
            if (result.pixels() == null) {
                tile.retryAtNanos = System.nanoTime() + RETRY_NANOS;
                continue;
            }
            tile.fresh = result.source() == Source.SCAN;
            if (tile.fresh) {
                markAncestorsStale(result.key());
            }
            if (Map2dGeometry.empty(result.pixels())) {
                releaseTexture(minecraft, tile);
                tile.empty = true;
                continue;
            }
            tile.empty = false;
            if (tile.dynamicTexture != null) {
                NativeImage pixels = tile.dynamicTexture.getPixels();
                int size = Map2dGeometry.TILE_PIXELS;
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        pixels.setPixel(x, y, result.pixels()[y * size + x]);
                    }
                }
                tile.dynamicTexture.upload();
            } else {
                Identifier id = Identifier.fromNamespaceAndPath(VoxyMapClient.MOD_ID, "map2d/" + nextTextureId++);
                DynamicTexture texture = new DynamicTexture(() -> "VoxyMap 2D map tile", toImage(result.pixels()));
                minecraft.getTextureManager().register(id, texture);
                tile.texture = id;
                tile.dynamicTexture = texture;
                readyTextures++;
            }
            tile.lastUsed = ++useCounter;
            uploads++;
        }
        evict(minecraft);
    }

    private static void releaseTexture(Minecraft minecraft, Tile tile) {
        if (tile.texture != null) {
            minecraft.getTextureManager().release(tile.texture);
            tile.texture = null;
            tile.dynamicTexture = null;
            readyTextures--;
        }
    }

    private static void evict(Minecraft minecraft) {
        if (readyTextures <= MAX_TEXTURES && TILES.size() <= MAX_TEXTURES * 4) {
            return;
        }
        List<Map.Entry<SliceKey, Tile>> candidates = new ArrayList<>();
        for (Map.Entry<SliceKey, Tile> entry : TILES.entrySet()) {
            Tile tile = entry.getValue();
            if (!tile.loading && tile.lastFrame < frame - 1L) {
                candidates.add(entry);
            }
        }
        candidates.sort(Comparator.comparingLong(entry -> entry.getValue().lastUsed));
        Iterator<Map.Entry<SliceKey, Tile>> iterator = candidates.iterator();
        while (iterator.hasNext() && (readyTextures > MAX_TEXTURES || TILES.size() > MAX_TEXTURES * 4)) {
            Map.Entry<SliceKey, Tile> victim = iterator.next();
            releaseTexture(minecraft, victim.getValue());
            TILES.remove(victim.getKey());
        }
    }

    public static void clear(Minecraft minecraft) {
        generation++;
        inFlight = new AtomicInteger();
        for (Tile tile : TILES.values()) {
            if (tile.texture != null) {
                minecraft.getTextureManager().release(tile.texture);
            }
        }
        TILES.clear();
        readyTextures = 0;
        RESULTS.clear();
        synchronized (PIXELS) {
            PIXELS.clear();
        }
        context = null;
        failureLogged = false;
    }
}
