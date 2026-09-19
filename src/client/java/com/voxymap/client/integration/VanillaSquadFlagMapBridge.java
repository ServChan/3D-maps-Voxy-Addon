package com.voxymap.client.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.voxymap.client.VoxyMapClient;
import com.voxymap.client.map.VoxyMapSettings;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Pulls the "banners" (claim/nation flag) marker layer from VanillaSquad's public squaremap endpoint,
 * one such layer per dimension. Polled on a fixed interval, and only when the player has explicitly
 * enabled it in the map settings — same shape as {@link VanillaSquadPlayerMapBridge}.
 */
public final class VanillaSquadFlagMapBridge {
    private static final String MARKERS_URL_TEMPLATE = "https://vanillasquad.com/map/proxy/%s/tiles/%s/markers.json";
    private static final String BANNER_SET_ID = "banners";
    private static final long DEFAULT_INTERVAL_SECONDS = 60L;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RESPONSE_CHARS = 8_000_000;
    private static final int MAX_FLAGS = 25_000;
    private static final int MAX_LABEL_LENGTH = 96;
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    private static final Map<String, List<Territory>> TERRITORIES_BY_WORLD = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_FETCH_NANOS_BY_WORLD = new ConcurrentHashMap<>();
    private static final Map<String, AtomicBoolean> FETCHING_BY_WORLD = new ConcurrentHashMap<>();

    private VanillaSquadFlagMapBridge() {
    }

    /** @param dimensionId e.g. {@code "minecraft:overworld"} */
    public static void tick(String mapId, String dimensionId) {
        if (!VoxyMapSettings.vanillaSquadFlagsActive() || mapId == null || dimensionId == null) {
            return;
        }

        String world = dimensionId.replace(':', '_');
        String cacheKey = mapId + "/" + world;
        long now = System.nanoTime();
        Long lastFetch = LAST_FETCH_NANOS_BY_WORLD.get(cacheKey);
        if (lastFetch != null && now - lastFetch < DEFAULT_INTERVAL_SECONDS * 1_000_000_000L) {
            return;
        }

        AtomicBoolean fetching = FETCHING_BY_WORLD.computeIfAbsent(cacheKey, ignored -> new AtomicBoolean(false));
        if (!fetching.compareAndSet(false, true)) {
            return;
        }
        LAST_FETCH_NANOS_BY_WORLD.put(cacheKey, now);

        HttpRequest request = HttpRequest.newBuilder(URI.create(MARKERS_URL_TEMPLATE.formatted(mapId, world)))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.limiting(
                        HttpResponse.BodyHandlers.ofString(), MAX_RESPONSE_CHARS))
                .whenComplete((response, throwable) -> {
                    try {
                        if (throwable != null) {
                            VoxyMapClient.LOGGER.warn("[VoxyMap] Could not fetch VanillaSquad flags for {}.", world, throwable);
                        } else if (response.statusCode() == 200) {
                            // Clustering is done once here, off the render thread, not per frame.
                            TERRITORIES_BY_WORLD.put(cacheKey, TerritoryClusterer.cluster(parse(response.body())));
                        } else {
                            VoxyMapClient.LOGGER.warn("[VoxyMap] VanillaSquad markers endpoint returned HTTP {} for {}.", response.statusCode(), world);
                        }
                    } catch (RuntimeException exception) {
                        VoxyMapClient.LOGGER.warn("[VoxyMap] Could not parse VanillaSquad flags for {}.", world, exception);
                    } finally {
                        fetching.set(false);
                    }
                });
    }

    static List<Flag> parse(String body) {
        if (body == null || body.length() > MAX_RESPONSE_CHARS) {
            throw new IllegalArgumentException("VanillaSquad marker response is empty or too large");
        }
        JsonArray sets = JsonParser.parseString(body).getAsJsonArray();
        for (JsonElement setElement : sets) {
            if (!setElement.isJsonObject()) {
                continue;
            }
            JsonObject set = setElement.getAsJsonObject();
            String id;
            try {
                id = set.has("id") ? set.get("id").getAsString() : "";
            } catch (RuntimeException ignored) {
                continue;
            }
            if (!BANNER_SET_ID.equals(id)) {
                continue;
            }
            JsonArray markers;
            try {
                markers = set.getAsJsonArray("markers");
            } catch (RuntimeException ignored) {
                return List.of();
            }
            if (markers == null) {
                return List.of();
            }

            List<Flag> flags = new ArrayList<>(Math.min(markers.size(), MAX_FLAGS));
            for (JsonElement markerElement : markers) {
                if (flags.size() >= MAX_FLAGS) {
                    break;
                }
                if (!markerElement.isJsonObject()) continue;
                JsonObject marker = markerElement.getAsJsonObject();
                JsonObject point = marker.has("point") ? marker.getAsJsonObject("point") : null;
                if (point == null || !point.has("x") || !point.has("z")) {
                    continue;
                }
                try {
                    double x = point.get("x").getAsDouble();
                    double z = point.get("z").getAsDouble();
                    if (!Double.isFinite(x) || !Double.isFinite(z)) {
                        continue;
                    }
                    String icon = marker.has("icon") ? marker.get("icon").getAsString().toUpperCase(Locale.ROOT) : "WHITE";
                    String tooltip = marker.has("tooltip") ? stripHtml(marker.get("tooltip").getAsString()) : "";
                    flags.add(new Flag(x, z, icon, tooltip));
                } catch (RuntimeException ignored) {
                    // Keep valid markers when one public-map entry has the wrong JSON type.
                }
            }
            return List.copyOf(flags);
        }
        return List.of();
    }

    private static String stripHtml(String html) {
        String cleaned = HTML_TAG.matcher(html == null ? "" : html).replaceAll("");
        cleaned = CONTROL_CHARACTERS.matcher(cleaned).replaceAll("").trim();
        return cleaned.length() <= MAX_LABEL_LENGTH ? cleaned : cleaned.substring(0, MAX_LABEL_LENGTH);
    }

    /** All currently known territory outlines in the given dimension (e.g. {@code "minecraft:the_nether"}). */
    public static List<Territory> territoriesInDimension(String mapId, String dimensionId) {
        if (mapId == null || dimensionId == null) {
            return List.of();
        }
        return TERRITORIES_BY_WORLD.getOrDefault(mapId + "/" + dimensionId.replace(':', '_'), List.of());
    }

    public enum Color {
        RED(0xFFE05B5B), BLACK(0xFF2A2A2A), MAGENTA(0xFFD463D6), LIGHT_BLUE(0xFF6ED4F0),
        BLUE(0xFF4B6FE0), YELLOW(0xFFE8D95B), GREEN(0xFF4E9E52), LIME(0xFF8FDB4A), WHITE(0xFFF2F2F2);

        public final int argb;

        Color(int argb) {
            this.argb = argb;
        }
    }

    public static int colorFor(String icon) {
        if (icon == null) {
            return Color.WHITE.argb;
        }
        try {
            return Color.valueOf(icon.toUpperCase(Locale.ROOT)).argb;
        } catch (IllegalArgumentException exception) {
            return Color.WHITE.argb;
        }
    }

    public record Flag(double x, double z, String icon, String tooltip) {
    }

    /**
     * A named cluster of same-coloured banner markers, reduced to a convex-hull outline (or the raw
     * points, for clusters too small to form one) plus a centroid for the label.
     */
    public record Territory(String name, int color, List<double[]> outline, double centroidX, double centroidZ) {
    }
}
