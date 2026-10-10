package com.voxymap.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.voxymap.client.VoxyMapClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class WebMapTiles {
    static final int MAX_TEXTURES = 96;
    private static final int MAX_IN_FLIGHT = 6;
    private static final int UPLOADS_PER_FRAME = 3;
    private static final int MAX_TILE_BYTES = 4 * 1024 * 1024;
    private static final long MISSING_RETRY_NANOS = 5L * 60L * 1_000_000_000L;
    private static final long FAILED_RETRY_NANOS = 20L * 1_000_000_000L;
    private static final long SWEEP_INTERVAL_NANOS = 1_000_000_000L;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public record TileKey(String mapId, String world, int zoom, int x, int z) {
    }

    private enum Outcome { READY, MISSING, FAILED }

    private record Result(TileKey key, long generation, Outcome outcome, NativeImage image) {
    }

    private static final class Tile {
        Identifier texture;
        boolean loading;
        long retryAtNanos;
        long lastUsed;
        long lastFrame = -1L;
    }

    private static final Map<TileKey, Tile> TILES = new HashMap<>();
    private static final ConcurrentLinkedQueue<Result> RESULTS = new ConcurrentLinkedQueue<>();
    private static final Set<CompletableFuture<?>> REQUESTS = ConcurrentHashMap.newKeySet();
    private static volatile AtomicInteger inFlight = new AtomicInteger();
    private static volatile long generation;
    private static long useCounter;
    private static long frame;
    private static int readyTextures;
    private static long lastSweepNanos;
    private static int nextTextureId;
    private static boolean failureLogged;

    private WebMapTiles() {
    }

    public static Identifier texture(TileKey key) {
        Tile tile = TILES.get(key);
        if (tile == null || tile.texture == null) {
            return null;
        }
        tile.lastUsed = ++useCounter;
        tile.lastFrame = frame;
        return tile.texture;
    }

    public static void request(TileKey key, String url) {
        Tile tile = TILES.get(key);
        long now = System.nanoTime();
        if (tile != null && (tile.texture != null || tile.loading || now - tile.retryAtNanos < 0L)) {
            return;
        }
        AtomicInteger counter = inFlight;
        if (counter.get() >= MAX_IN_FLIGHT) {
            return;
        }
        if (tile == null) {
            tile = new Tile();
            TILES.put(key, tile);
        }
        tile.loading = true;
        tile.lastUsed = ++useCounter;
        counter.incrementAndGet();
        long requestGeneration = generation;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "VoxyMap")
                .GET()
                .build();
        CompletableFuture<HttpResponse<byte[]>> future = CLIENT.sendAsync(request, HttpResponse.BodyHandlers.limiting(
                HttpResponse.BodyHandlers.ofByteArray(), MAX_TILE_BYTES));
        REQUESTS.add(future);
        future.whenComplete((response, throwable) -> {
            try {
                Result result = decode(key, requestGeneration, response, throwable);
                if (result.generation() == generation) {
                    RESULTS.add(result);
                } else if (result.image() != null) {
                    result.image().close();
                }
            } finally {
                counter.decrementAndGet();
                REQUESTS.remove(future);
            }
        });
    }

    public static int pending() {
        return inFlight.get();
    }

    private static Result decode(TileKey key, long requestGeneration, HttpResponse<byte[]> response, Throwable throwable) {
        if (requestGeneration != generation) {
            return new Result(key, requestGeneration, Outcome.FAILED, null);
        }
        if (throwable != null) {
            logFailure("Could not download a VanillaSquad web map tile", throwable);
            return new Result(key, requestGeneration, Outcome.FAILED, null);
        }
        int status = response.statusCode();
        if (status == 404 || status == 200 && !WebMapGeometry.isPng(response.body())) {
            return new Result(key, requestGeneration, Outcome.MISSING, null);
        }
        if (status != 200) {
            logFailure("Could not download a VanillaSquad web map tile; the server answered HTTP {}", status);
            return new Result(key, requestGeneration, Outcome.FAILED, null);
        }
        try {
            return new Result(key, requestGeneration, Outcome.READY,
                    NativeImage.read(new ByteArrayInputStream(response.body())));
        } catch (IOException | RuntimeException exception) {
            logFailure("Could not decode a VanillaSquad web map tile", exception);
            return new Result(key, requestGeneration, Outcome.MISSING, null);
        }
    }

    private static void logFailure(String message, Object... args) {
        if (!failureLogged) {
            failureLogged = true;
            VoxyMapClient.LOGGER.warn(message, args);
        }
    }

    public static void tick(Minecraft minecraft) {
        frame++;
        int uploads = 0;
        Result result;
        while (uploads < UPLOADS_PER_FRAME && (result = RESULTS.poll()) != null) {
            Tile tile = TILES.get(result.key());
            if (result.generation() != generation || tile == null) {
                if (result.image() != null) {
                    result.image().close();
                }
                continue;
            }
            tile.loading = false;
            if (result.outcome() == Outcome.READY) {
                Identifier id = Identifier.fromNamespaceAndPath(VoxyMapClient.MOD_ID, "web_map/" + nextTextureId++);
                minecraft.getTextureManager().register(id, new DynamicTexture(() -> "VoxyMap web map tile", result.image()));
                tile.texture = id;
                tile.lastUsed = ++useCounter;
                readyTextures++;
                uploads++;
            } else {
                tile.retryAtNanos = System.nanoTime()
                        + (result.outcome() == Outcome.MISSING ? MISSING_RETRY_NANOS : FAILED_RETRY_NANOS);
            }
        }
        sweepExpired();
        evict(minecraft);
    }

    private static void sweepExpired() {
        long now = System.nanoTime();
        if (now - lastSweepNanos < SWEEP_INTERVAL_NANOS) {
            return;
        }
        lastSweepNanos = now;
        for (Iterator<Tile> iterator = TILES.values().iterator(); iterator.hasNext(); ) {
            Tile tile = iterator.next();
            if (tile.texture == null && !tile.loading && now - tile.retryAtNanos >= 0L) {
                iterator.remove();
            }
        }
    }

    private static void evict(Minecraft minecraft) {
        if (readyTextures <= MAX_TEXTURES) {
            return;
        }
        List<Map.Entry<TileKey, Tile>> candidates = new ArrayList<>();
        for (Map.Entry<TileKey, Tile> entry : TILES.entrySet()) {
            Tile tile = entry.getValue();
            if (tile.texture != null && tile.lastFrame < frame - 1L) {
                candidates.add(entry);
            }
        }
        candidates.sort(Comparator.comparingLong(entry -> entry.getValue().lastUsed));
        int excess = Math.min(readyTextures - MAX_TEXTURES, candidates.size());
        for (int i = 0; i < excess; i++) {
            Map.Entry<TileKey, Tile> victim = candidates.get(i);
            minecraft.getTextureManager().release(victim.getValue().texture);
            TILES.remove(victim.getKey());
            readyTextures--;
        }
    }

    public static void clear(Minecraft minecraft) {
        generation++;
        inFlight = new AtomicInteger();
        for (CompletableFuture<?> request : REQUESTS) {
            request.cancel(true);
        }
        REQUESTS.clear();
        for (Tile tile : TILES.values()) {
            if (tile.texture != null) {
                minecraft.getTextureManager().release(tile.texture);
            }
        }
        TILES.clear();
        readyTextures = 0;
        Result result;
        while ((result = RESULTS.poll()) != null) {
            if (result.image() != null) {
                result.image().close();
            }
        }
        failureLogged = false;
    }
}
