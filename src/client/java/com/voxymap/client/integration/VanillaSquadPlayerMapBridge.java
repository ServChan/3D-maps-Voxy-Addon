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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Pulls global player positions from VanillaSquad's public squaremap live-map endpoint, so the 3D map
 * can show players anywhere on the server instead of only the ones the client happens to have loaded
 * nearby. Polled on a fixed interval rather than per-frame since it's a network round trip to a
 * third-party site; only runs when the player has explicitly enabled it in the map settings.
 */
public final class VanillaSquadPlayerMapBridge {
    private static final String PLAYERS_URL = "https://vanillasquad.com/map/proxy/build/tiles/players.json";
    private static final long DEFAULT_INTERVAL_SECONDS = 60L;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RESPONSE_CHARS = 2_000_000;
    private static final int MAX_PLAYERS = 2_048;
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_WORLD_LENGTH = 128;
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");
    private static final Pattern COMPACT_UUID = Pattern.compile("[0-9a-f]{32}");

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    private static volatile List<RemotePlayer> players = List.of();
    private static volatile long lastFetchNanos = Long.MIN_VALUE;
    private static final AtomicBoolean fetching = new AtomicBoolean(false);

    private VanillaSquadPlayerMapBridge() {
    }

    public static void tick() {
        if (!VoxyMapSettings.vanillaSquadPlayersEnabled()) {
            return;
        }

        long now = System.nanoTime();
        if (lastFetchNanos != Long.MIN_VALUE && now - lastFetchNanos < DEFAULT_INTERVAL_SECONDS * 1_000_000_000L) {
            return;
        }
        if (!fetching.compareAndSet(false, true)) {
            return;
        }
        lastFetchNanos = now;

        HttpRequest request = HttpRequest.newBuilder(URI.create(PLAYERS_URL))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, throwable) -> {
                    try {
                        if (throwable != null) {
                            VoxyMapClient.LOGGER.warn("[VoxyMap] Could not fetch VanillaSquad player positions.", throwable);
                        } else if (response.statusCode() == 200) {
                            players = parse(response.body());
                        } else {
                            VoxyMapClient.LOGGER.warn("[VoxyMap] VanillaSquad player endpoint returned HTTP {}.", response.statusCode());
                        }
                    } catch (RuntimeException exception) {
                        VoxyMapClient.LOGGER.warn("[VoxyMap] Could not parse VanillaSquad player positions.", exception);
                    } finally {
                        fetching.set(false);
                    }
                });
    }

    static List<RemotePlayer> parse(String body) {
        if (body == null || body.length() > MAX_RESPONSE_CHARS) {
            throw new IllegalArgumentException("VanillaSquad player response is empty or too large");
        }
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray array = root.getAsJsonArray("players");
        if (array == null) {
            return List.of();
        }

        List<RemotePlayer> parsed = new ArrayList<>(Math.min(array.size(), MAX_PLAYERS));
        for (JsonElement element : array) {
            if (parsed.size() >= MAX_PLAYERS) {
                break;
            }
            if (!element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("name") || !entry.has("world") || !entry.has("x") || !entry.has("y") || !entry.has("z")) {
                continue;
            }
            try {
                String name = safeText(entry.get("name").getAsString(), MAX_NAME_LENGTH);
                String world = safeText(entry.get("world").getAsString(), MAX_WORLD_LENGTH);
                double x = entry.get("x").getAsDouble();
                double y = entry.get("y").getAsDouble();
                double z = entry.get("z").getAsDouble();
                if (name.isEmpty() || world.isEmpty() || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                    continue;
                }
                parsed.add(new RemotePlayer(
                        name, world, x, y, z,
                        entry.has("uuid") ? normalizeUuid(entry.get("uuid").getAsString()) : null));
            } catch (RuntimeException ignored) {
                // A single malformed public-map entry must not discard the rest of the snapshot.
            }
        }
        return List.copyOf(parsed);
    }

    /** All currently known remote players in the given dimension (e.g. {@code "minecraft:the_nether"}). */
    public static List<RemotePlayer> playersInDimension(String dimensionId) {
        if (dimensionId == null) {
            return List.of();
        }
        String world = dimensionId.replace(':', '_');
        List<RemotePlayer> snapshot = players;
        List<RemotePlayer> filtered = new ArrayList<>();
        for (RemotePlayer player : snapshot) {
            if (world.equals(player.world())) {
                filtered.add(player);
            }
        }
        return filtered;
    }

    /** Strips dashes and lower-cases a UUID so squaremap's compact form can be compared to Java's. */
    public static String normalizeUuid(String uuid) {
        if (uuid == null) return null;
        String compact = uuid.replace("-", "").toLowerCase(Locale.ROOT);
        return COMPACT_UUID.matcher(compact).matches() ? compact : null;
    }

    /** Parses squaremap's dashless UUID form (or a normal one) into a {@link java.util.UUID}. */
    public static java.util.UUID toJavaUuid(String uuid) {
        if (uuid == null) {
            return null;
        }
        String hex = uuid.replace("-", "");
        if (hex.length() != 32) {
            return null;
        }
        String dashed = hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-"
                + hex.substring(16, 20) + "-" + hex.substring(20);
        try {
            return java.util.UUID.fromString(dashed);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public record RemotePlayer(String name, String world, double x, double y, double z, String uuid) {
    }

    private static String safeText(String value, int maxLength) {
        String cleaned = CONTROL_CHARACTERS.matcher(value == null ? "" : value).replaceAll("").trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }
}
