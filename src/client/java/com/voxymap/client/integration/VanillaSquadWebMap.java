package com.voxymap.client.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.voxymap.client.ModLog;
import com.voxymap.client.VoxyMapClient;
import com.voxymap.client.map.MinimapRenderer;
import com.voxymap.client.map.VoxyMapSettings;
import com.voxymap.client.map.WebMapGeometry;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public final class VanillaSquadWebMap {
    private static final String BASE_URL = "https://vanillasquad.com/map/proxy/";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long REFRESH_NANOS = 10L * 60L * 1_000_000_000L;
    private static final long RETRY_NANOS = 30L * 1_000_000_000L;
    private static final int MAX_RESPONSE_CHARS = 200_000;
    private static final int MAX_WORLDS_PER_MAP = 8;
    private static final int MAX_LABEL_LENGTH = 48;
    private static final int DEFAULT_MAX_ZOOM = 3;
    private static final int COORDINATE_LIMIT = 30_000_000;
    private static final Pattern WORLD_NAME = Pattern.compile("[a-z0-9_.-]{1,64}");
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Map<String, List<World>> WORLDS_BY_MAP = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_FETCH_NANOS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> FAILED_BY_MAP = new ConcurrentHashMap<>();
    private static final java.util.Set<String> REPORTED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Map<String, AtomicBoolean> FETCHING_BY_MAP = new ConcurrentHashMap<>();

    private VanillaSquadWebMap() {
    }

    public record World(String mapId, String name, String displayName, String type, int maxZoom, int spawnX,
                        int spawnZ) {
        public String key() {
            return mapId + "/" + name;
        }

        public String dimensionId() {
            return WebMapGeometry.dimensionOf(name);
        }

        public String tileUrl(int zoom, int tileX, int tileZ) {
            return BASE_URL + mapId + "/tiles/" + name + "/" + zoom + "/" + tileX + "_" + tileZ + ".png";
        }
    }

    record WorldSettings(int maxZoom, int spawnX, int spawnZ) {
        static final WorldSettings DEFAULT = new WorldSettings(DEFAULT_MAX_ZOOM, 0, 0);
    }

    public static boolean available() {
        return VanillaSquadServer.isConnected();
    }

    public static List<String> mapIds() {
        List<String> ids = new ArrayList<>();
        for (VoxyMapSettings.VanillaSquadMap map : VoxyMapSettings.VanillaSquadMap.values()) {
            if (map.mapId() != null) {
                ids.add(map.mapId());
            }
        }
        return ids;
    }

    public static void refresh() {
        if (!available()) {
            return;
        }
        for (String mapId : mapIds()) {
            fetchIfDue(mapId);
        }
    }

    public static List<World> worlds() {
        List<World> all = new ArrayList<>();
        for (String mapId : mapIds()) {
            all.addAll(WORLDS_BY_MAP.getOrDefault(mapId, List.of()));
        }
        return all;
    }

    public static boolean loading() {
        for (String mapId : mapIds()) {
            if (!WORLDS_BY_MAP.containsKey(mapId) && !FAILED_BY_MAP.getOrDefault(mapId, false)) {
                return true;
            }
        }
        return false;
    }

    public static boolean failed() {
        return worlds().isEmpty() && !loading();
    }

    public static String currentMapId(Minecraft minecraft) {
        if (!available()) {
            return null;
        }
        return RemotePlayerMarkers.resolvedMapId(minecraft.level, MinimapRenderer.currentVoxyWorldId(minecraft));
    }

    public static void rememberCurrentWorld(Minecraft minecraft) {
        if (minecraft.level == null) {
            return;
        }
        String mapId = currentMapId(minecraft);
        String worldId = MinimapRenderer.currentWorldId(minecraft);
        if (mapId != null && worldId != null) {
            VoxyMapSettings.linkWebMapWorld(mapId,
                    WebMapGeometry.worldNameOf(minecraft.level.dimension().identifier().toString()), worldId);
        }
    }

    private static void fetchIfDue(String mapId) {
        long now = System.nanoTime();
        Long last = LAST_FETCH_NANOS.get(mapId);
        long interval = FAILED_BY_MAP.getOrDefault(mapId, false) ? RETRY_NANOS : REFRESH_NANOS;
        if (last != null && now - last < interval) {
            return;
        }
        AtomicBoolean fetching = FETCHING_BY_MAP.computeIfAbsent(mapId, ignored -> new AtomicBoolean(false));
        if (!fetching.compareAndSet(false, true)) {
            return;
        }
        LAST_FETCH_NANOS.put(mapId, now);
        fetchText(BASE_URL + mapId + "/tiles/settings.json")
                .thenCompose(body -> {
                    List<World> listed = parseWorlds(mapId, body);
                    List<CompletableFuture<World>> detailed = new ArrayList<>();
                    for (World world : listed) {
                        detailed.add(fetchText(BASE_URL + mapId + "/tiles/" + world.name() + "/settings.json")
                                .thenApply(VanillaSquadWebMap::parseWorldSettings)
                                .exceptionally(ignored -> WorldSettings.DEFAULT)
                                .thenApply(settings -> new World(world.mapId(), world.name(), world.displayName(),
                                        world.type(), settings.maxZoom(), settings.spawnX(), settings.spawnZ())));
                    }
                    return CompletableFuture.allOf(detailed.toArray(CompletableFuture[]::new))
                            .thenApply(ignored -> detailed.stream().map(CompletableFuture::join).toList());
                })
                .whenComplete((worlds, throwable) -> {
                    try {
                        if (throwable != null) {
                            FAILED_BY_MAP.put(mapId, true);
                            if (REPORTED.add(mapId + " " + ModLog.describe(throwable))) {
                                VoxyMapClient.LOGGER.warn("Could not load the VanillaSquad {} web map", mapId, throwable);
                            }
                        } else {
                            WORLDS_BY_MAP.put(mapId, List.copyOf(worlds));
                            FAILED_BY_MAP.put(mapId, false);
                        }
                    } finally {
                        fetching.set(false);
                    }
                });
    }

    private static CompletableFuture<String> fetchText(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "VoxyMap")
                .GET()
                .build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.limiting(
                        HttpResponse.BodyHandlers.ofString(), MAX_RESPONSE_CHARS))
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException("HTTP " + response.statusCode() + " for " + url);
                    }
                    return response.body();
                });
    }

    static List<World> parseWorlds(String mapId, String body) {
        if (body == null || body.isBlank() || body.length() > MAX_RESPONSE_CHARS) {
            throw new IllegalArgumentException("Web map settings are empty or too large");
        }
        JsonArray array = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("worlds");
        if (array == null) {
            return List.of();
        }
        record Ordered(World world, int order, int index) {
        }
        List<Ordered> ordered = new ArrayList<>();
        for (JsonElement element : array) {
            if (ordered.size() >= MAX_WORLDS_PER_MAP) {
                break;
            }
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            try {
                String name = entry.has("name") ? entry.get("name").getAsString() : "";
                if (!WORLD_NAME.matcher(name).matches()) {
                    continue;
                }
                String displayName = entry.has("display_name") ? safeText(entry.get("display_name").getAsString()) : "";
                String type = entry.has("type") ? safeText(entry.get("type").getAsString()) : "";
                int order = entry.has("order") ? entry.get("order").getAsInt() : 0;
                ordered.add(new Ordered(new World(mapId, name, displayName.isEmpty() ? name : displayName, type,
                        DEFAULT_MAX_ZOOM, 0, 0), order, ordered.size()));
            } catch (RuntimeException ignored) {

            }
        }
        ordered.sort(Comparator.comparingInt(Ordered::order).thenComparingInt(Ordered::index));
        return ordered.stream().map(Ordered::world).toList();
    }

    static WorldSettings parseWorldSettings(String body) {
        if (body == null || body.isBlank() || body.length() > MAX_RESPONSE_CHARS) {
            return WorldSettings.DEFAULT;
        }
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            int maxZoom = DEFAULT_MAX_ZOOM;
            if (root.has("zoom") && root.get("zoom").isJsonObject()) {
                JsonObject zoom = root.getAsJsonObject("zoom");
                if (zoom.has("max")) {
                    maxZoom = Math.max(0, Math.min(WebMapGeometry.MAX_ZOOM_LIMIT, zoom.get("max").getAsInt()));
                }
            }
            int spawnX = 0;
            int spawnZ = 0;
            if (root.has("spawn") && root.get("spawn").isJsonObject()) {
                JsonObject spawn = root.getAsJsonObject("spawn");
                spawnX = clampCoordinate(spawn.has("x") ? spawn.get("x").getAsInt() : 0);
                spawnZ = clampCoordinate(spawn.has("z") ? spawn.get("z").getAsInt() : 0);
            }
            return new WorldSettings(maxZoom, spawnX, spawnZ);
        } catch (RuntimeException exception) {
            return WorldSettings.DEFAULT;
        }
    }

    private static int clampCoordinate(int value) {
        return Math.max(-COORDINATE_LIMIT, Math.min(COORDINATE_LIMIT, value));
    }

    private static String safeText(String value) {
        String cleaned = CONTROL_CHARACTERS.matcher(value == null ? "" : value).replaceAll("").trim();
        return cleaned.length() <= MAX_LABEL_LENGTH ? cleaned : cleaned.substring(0, MAX_LABEL_LENGTH);
    }
}
