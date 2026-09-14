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
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Pulls global player positions from VanillaSquad's public squaremap live-map endpoint, so the 3D map
 * can show players anywhere on the server instead of only the ones the client happens to have loaded
 * nearby. Polled on a fixed interval rather than per-frame since it's a network round trip to a
 * third-party site; only runs when the player has explicitly enabled a VanillaSquad overlay.
 */
public final class VanillaSquadPlayerMapBridge {
    private static final String PLAYERS_URL_TEMPLATE = "https://vanillasquad.com/map/proxy/%s/tiles/players.json";
    private static final List<String> MAP_IDS = List.of("build", "farm", "art");
    private static final long DEFAULT_INTERVAL_SECONDS = 15L;
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

    private static final Map<String, List<RemotePlayer>> PLAYERS_BY_MAP = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_FETCH_NANOS_BY_MAP = new ConcurrentHashMap<>();
    private static final Map<String, AtomicBoolean> FETCHING_BY_MAP = new ConcurrentHashMap<>();
    private static final Map<String, Long> FETCHED_GENERATION_BY_MAP = new ConcurrentHashMap<>();
    private static volatile SelectionContext selectionContext;
    private static volatile String activeMapId = "build";
    private static volatile boolean autoResolved;
    private static WeakReference<Object> lastWorldIdentity = new WeakReference<>(null);
    private static volatile long worldGeneration;
    // selectActiveMap is already re-run from fetchIfNeeded's async completion callback whenever fresh
    // remote data actually arrives - the per-tick call in tick() below only exists to catch up with the
    // LOCAL player moving between overlapping map regions between fetches, so once a map has been
    // resolved it doesn't need to redo that full players x maps x observers search at full 20Hz tick
    // rate. See tick().
    private static final long LOCAL_RESOLVE_INTERVAL_NANOS = 1_000_000_000L;
    private static volatile long lastLocalResolveNanos;

    private VanillaSquadPlayerMapBridge() {
    }

    public static void tick(Object worldIdentity, String dimensionId, UUID playerUuid, String playerName,
                            double playerX, double playerZ, List<Observer> observers, String voxyWorldId) {
        if (!VoxyMapSettings.vanillaSquadPlayersEnabled() && !VoxyMapSettings.vanillaSquadFlagsEnabled()) {
            return;
        }

        if (worldIdentity != lastWorldIdentity.get()) {
            // Do not keep an old ClientLevel alive after disconnecting or changing proxy worlds.
            lastWorldIdentity = new WeakReference<>(worldIdentity);
            worldGeneration++;
            activeMapId = fallbackMapId(voxyWorldId);
            autoResolved = false;
            PLAYERS_BY_MAP.clear();
            LAST_FETCH_NANOS_BY_MAP.clear();
            FETCHED_GENERATION_BY_MAP.clear();
        }
        SelectionContext context = new SelectionContext(worldGeneration, dimensionId,
                normalizeUuid(playerUuid == null ? null : playerUuid.toString()),
                playerName == null ? "" : playerName.toLowerCase(Locale.ROOT), playerX, playerZ,
                observers == null ? List.of() : List.copyOf(observers), voxyWorldId);
        selectionContext = context;
        String configuredMapId = VoxyMapSettings.vanillaSquadMap(context == null ? null : context.voxyWorldId()).mapId();
        if (configuredMapId != null) {
            activeMapId = configuredMapId;
            if (VoxyMapSettings.vanillaSquadPlayersEnabled()) {
                fetchIfNeeded(configuredMapId, context.generation());
            }
            return;
        }
        for (String mapId : MAP_IDS) {
            fetchIfNeeded(mapId, context.generation());
        }
        // Resolve promptly (every tick) until the first successful auto-detection, then throttle -
        // see LOCAL_RESOLVE_INTERVAL_NANOS's comment.
        long now = System.nanoTime();
        if (!autoResolved || now - lastLocalResolveNanos >= LOCAL_RESOLVE_INTERVAL_NANOS) {
            lastLocalResolveNanos = now;
            selectActiveMap(context);
        }
    }

    private static void fetchIfNeeded(String mapId, long generation) {
        long now = System.nanoTime();
        Long lastFetch = LAST_FETCH_NANOS_BY_MAP.get(mapId);
        long intervalSeconds = autoResolved ? DEFAULT_INTERVAL_SECONDS : 5L;
        if (lastFetch != null && now - lastFetch < intervalSeconds * 1_000_000_000L) {
            return;
        }
        AtomicBoolean fetching = FETCHING_BY_MAP.computeIfAbsent(mapId, ignored -> new AtomicBoolean(false));
        if (!fetching.compareAndSet(false, true)) {
            return;
        }
        LAST_FETCH_NANOS_BY_MAP.put(mapId, now);

        HttpRequest request = HttpRequest.newBuilder(URI.create(PLAYERS_URL_TEMPLATE.formatted(mapId)))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.limiting(
                        HttpResponse.BodyHandlers.ofString(), MAX_RESPONSE_CHARS))
                .whenComplete((response, throwable) -> {
                    try {
                        if (throwable != null) {
                            VoxyMapClient.LOGGER.warn("[VoxyMap] Could not fetch VanillaSquad player positions for {}.", mapId, throwable);
                        } else if (response.statusCode() == 200) {
                            PLAYERS_BY_MAP.put(mapId, parse(response.body()));
                        } else {
                            VoxyMapClient.LOGGER.warn("[VoxyMap] VanillaSquad player endpoint returned HTTP {} for {}.", response.statusCode(), mapId);
                        }
                    } catch (RuntimeException exception) {
                        VoxyMapClient.LOGGER.warn("[VoxyMap] Could not parse VanillaSquad player positions for {}.", mapId, exception);
                    } finally {
                        FETCHED_GENERATION_BY_MAP.put(mapId, generation);
                        fetching.set(false);
                        SelectionContext latest = selectionContext;
                        if (latest != null) {
                            selectActiveMap(latest);
                        }
                    }
                });
    }

    private static void selectActiveMap(SelectionContext context) {
        String configuredMapId = context == null ? null
                : VoxyMapSettings.vanillaSquadMap(context.voxyWorldId()).mapId();
        if (configuredMapId != null) {
            activeMapId = configuredMapId;
            return;
        }
        if (context == null || context.generation() != worldGeneration
                || MAP_IDS.stream().anyMatch(mapId -> FETCHED_GENERATION_BY_MAP.getOrDefault(mapId, -1L)
                        != context.generation())) {
            return;
        }
        String selected = selectMapId(PLAYERS_BY_MAP, context.dimensionId(), context.uuid(), context.name(),
                context.playerX(), context.playerZ(), context.observers());
        if (selected != null) {
            activeMapId = selected;
            autoResolved = true;
        }
    }

    static String selectMapId(Map<String, List<RemotePlayer>> playersByMap, String dimensionId,
                              String uuid, String name, double playerX, double playerZ) {
        return selectMapId(playersByMap, dimensionId, uuid, name, playerX, playerZ, List.of());
    }

    static String selectMapId(Map<String, List<RemotePlayer>> playersByMap, String dimensionId,
                              String uuid, String name, double playerX, double playerZ,
                              List<Observer> observers) {
        String world = dimensionId == null ? "" : dimensionId.replace(':', '_');
        String normalizedName = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String bestMap = null;
        double bestDistanceSquared = Double.POSITIVE_INFINITY;
        double secondDistanceSquared = Double.POSITIVE_INFINITY;
        for (String mapId : MAP_IDS) {
            for (RemotePlayer player : playersByMap.getOrDefault(mapId, List.of())) {
                Observer matchingObserver = matchingObserver(player, uuid, normalizedName, playerX, playerZ, observers);
                if (!world.equals(player.world()) || matchingObserver == null) {
                    continue;
                }
                double dx = player.x() - matchingObserver.x();
                double dz = player.z() - matchingObserver.z();
                double distanceSquared = dx * dx + dz * dz;
                if (distanceSquared > 512.0 * 512.0) {
                    continue;
                }
                if (distanceSquared < bestDistanceSquared) {
                    secondDistanceSquared = bestDistanceSquared;
                    bestDistanceSquared = distanceSquared;
                    bestMap = mapId;
                } else if (distanceSquared < secondDistanceSquared) {
                    secondDistanceSquared = distanceSquared;
                }
            }
        }
        // During a proxy transfer squaremap can briefly list the player in both worlds. If the two
        // positions are effectively tied, wait for the next five-second refresh instead of choosing
        // the first map id and flashing an unrelated overlay.
        if (Double.isFinite(secondDistanceSquared)
                && Math.sqrt(secondDistanceSquared) - Math.sqrt(bestDistanceSquared) < 8.0) {
            return null;
        }
        return bestMap;
    }

    private static Observer matchingObserver(RemotePlayer remote, String localUuid, String localName,
                                             double localX, double localZ, List<Observer> observers) {
        if (identityMatches(remote, localUuid, localName)) {
            return new Observer(localUuid, localName, localX, localZ);
        }
        for (Observer observer : observers) {
            if (observer != null && identityMatches(remote, observer.uuid(), observer.name())) {
                return observer;
            }
        }
        return null;
    }

    private static boolean identityMatches(RemotePlayer remote, String uuid, String name) {
        String normalizedName = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return uuid != null && uuid.equals(remote.uuid())
                || !normalizedName.isEmpty() && normalizedName.equals(remote.name().toLowerCase(Locale.ROOT));
    }

    private static String fallbackMapId(String voxyWorldId) {
        String configured = VoxyMapSettings.vanillaSquadMap(voxyWorldId).mapId();
        return configured == null ? "build" : configured;
    }

    public static String activeMapId() {
        return activeMapId;
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
        String mapId = activeMapId;
        if (dimensionId == null || mapId == null) {
            return List.of();
        }
        String world = dimensionId.replace(':', '_');
        List<RemotePlayer> snapshot = PLAYERS_BY_MAP.getOrDefault(mapId, List.of());
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

    public record Observer(String uuid, String name, double x, double z) {
    }

    private record SelectionContext(long generation, String dimensionId, String uuid, String name,
                                    double playerX, double playerZ, List<Observer> observers, String voxyWorldId) {
    }

    private static String safeText(String value, int maxLength) {
        String cleaned = CONTROL_CHARACTERS.matcher(value == null ? "" : value).replaceAll("").trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }
}
