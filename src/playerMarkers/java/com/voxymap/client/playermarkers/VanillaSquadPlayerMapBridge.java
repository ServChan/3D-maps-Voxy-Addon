package com.voxymap.client.playermarkers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.voxymap.client.ModLog;
import com.voxymap.client.VoxyMapClient;
import com.voxymap.client.integration.RemotePlayerMarkers;
import com.voxymap.client.integration.RemotePlayerMarkers.Observer;
import com.voxymap.client.integration.RemotePlayerMarkers.RemotePlayer;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public final class VanillaSquadPlayerMapBridge implements RemotePlayerMarkers.Source {
    private static final String PLAYERS_URL_TEMPLATE = "https://vanillasquad.com/map/proxy/%s/tiles/players.json";
    private static final List<String> MAP_IDS = List.of("build", "farm", "art");
    private static final long DEFAULT_INTERVAL_SECONDS = 15L;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long RESPONSE_TIMEOUT_SECONDS = 30;
    private static final int MAX_RESPONSE_CHARS = 2_000_000;
    private static final int MAX_PLAYERS = 2_048;
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_WORLD_LENGTH = 128;
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    private static final Map<String, List<RemotePlayer>> PLAYERS_BY_MAP = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_FETCH_NANOS_BY_MAP = new ConcurrentHashMap<>();
    private static final Map<String, AtomicBoolean> FETCHING_BY_MAP = new ConcurrentHashMap<>();
    private static final Map<String, Long> FETCHED_GENERATION_BY_MAP = new ConcurrentHashMap<>();
    private static final java.util.Set<String> REPORTED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static volatile SelectionContext selectionContext;
    private static volatile String activeMapId = "build";
    private static volatile boolean autoResolved;
    private static WeakReference<Object> lastWorldIdentity = new WeakReference<>(null);
    private static volatile long worldGeneration;

    private static final long LOCAL_RESOLVE_INTERVAL_NANOS = 1_000_000_000L;
    private static volatile long lastLocalResolveNanos;

    public VanillaSquadPlayerMapBridge() {
    }

    @Override
    public void tick(Object worldIdentity, String dimensionId, UUID playerUuid, String playerName,
                            double playerX, double playerZ, List<Observer> observers, String voxyWorldId) {
        if (!VoxyMapSettings.vanillaSquadPlayersActive() && !VoxyMapSettings.vanillaSquadFlagsActive()) {
            return;
        }

        if (worldIdentity != lastWorldIdentity.get()) {

            lastWorldIdentity = new WeakReference<>(worldIdentity);
            worldGeneration++;
            activeMapId = RemotePlayerMarkers.configuredOrDefaultMapId(voxyWorldId);
            autoResolved = false;
            PLAYERS_BY_MAP.clear();
            LAST_FETCH_NANOS_BY_MAP.clear();
            FETCHED_GENERATION_BY_MAP.clear();
        }
        SelectionContext context = new SelectionContext(worldGeneration, dimensionId,
                RemotePlayerMarkers.normalizeUuid(playerUuid == null ? null : playerUuid.toString()),
                playerName == null ? "" : playerName.toLowerCase(Locale.ROOT), playerX, playerZ,
                observers == null ? List.of() : List.copyOf(observers), voxyWorldId);
        selectionContext = context;
        String configuredMapId = VoxyMapSettings.vanillaSquadMap(context == null ? null : context.voxyWorldId()).mapId();
        if (configuredMapId != null) {
            activeMapId = configuredMapId;
            autoResolved = false;
            if (VoxyMapSettings.vanillaSquadPlayersActive()) {
                fetchIfNeeded(configuredMapId, context.generation());
            }
            return;
        }
        for (String mapId : MAP_IDS) {
            fetchIfNeeded(mapId, context.generation());
        }

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
                .orTimeout(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((response, throwable) -> {
                    try {
                        if (generation != worldGeneration) {
                            return;
                        }
                        if (throwable != null) {
                            if (REPORTED.add(mapId + " " + ModLog.describe(throwable))) {
                                VoxyMapClient.LOGGER.warn("Could not fetch VanillaSquad player positions for {}", mapId, throwable);
                            }
                        } else if (response.statusCode() == 200) {
                            PLAYERS_BY_MAP.put(mapId, parse(response.body()));
                        } else {
                            if (REPORTED.add(mapId + " HTTP " + response.statusCode())) {
                                VoxyMapClient.LOGGER.warn("Could not fetch VanillaSquad player positions for {}; the server answered HTTP {}", mapId, response.statusCode());
                            }
                        }
                    } catch (RuntimeException exception) {
                        if (REPORTED.add(mapId + " " + ModLog.describe(exception))) {
                            VoxyMapClient.LOGGER.warn("Could not parse VanillaSquad player positions for {}", mapId, exception);
                        }
                    } finally {
                        if (generation == worldGeneration) {
                            FETCHED_GENERATION_BY_MAP.put(mapId, generation);
                        }
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
            autoResolved = false;
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
            double mapBestSquared = Double.POSITIVE_INFINITY;
            for (RemotePlayer player : playersByMap.getOrDefault(mapId, List.of())) {
                Observer matchingObserver = matchingObserver(player, uuid, normalizedName, playerX, playerZ, observers);
                if (!world.equals(player.world()) || matchingObserver == null) {
                    continue;
                }
                double dx = player.x() - matchingObserver.x();
                double dz = player.z() - matchingObserver.z();
                double distanceSquared = dx * dx + dz * dz;
                if (distanceSquared <= 512.0 * 512.0) {
                    mapBestSquared = Math.min(mapBestSquared, distanceSquared);
                }
            }
            if (mapBestSquared < bestDistanceSquared) {
                secondDistanceSquared = bestDistanceSquared;
                bestDistanceSquared = mapBestSquared;
                bestMap = mapId;
            } else if (mapBestSquared < secondDistanceSquared) {
                secondDistanceSquared = mapBestSquared;
            }
        }

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

    @Override
    public String activeMapId() {
        return activeMapId;
    }

    @Override
    public boolean activeMapResolved(Object worldIdentity) {
        return autoResolved && worldIdentity == lastWorldIdentity.get();
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
                        entry.has("uuid") ? RemotePlayerMarkers.normalizeUuid(entry.get("uuid").getAsString()) : null));
            } catch (RuntimeException ignored) {

            }
        }
        return List.copyOf(parsed);
    }

    @Override
    public List<RemotePlayer> playersInDimension(String dimensionId) {
        String mapId = activeMapId;
        if (dimensionId == null || mapId == null) {
            return List.of();
        }
        SelectionContext context = selectionContext;
        boolean configured = context != null && VoxyMapSettings.vanillaSquadMap(context.voxyWorldId()).mapId() != null;
        if (!autoResolved && !configured) {
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

    private record SelectionContext(long generation, String dimensionId, String uuid, String name,
                                    double playerX, double playerZ, List<Observer> observers, String voxyWorldId) {
    }

    private static String safeText(String value, int maxLength) {
        String cleaned = CONTROL_CHARACTERS.matcher(value == null ? "" : value).replaceAll("").trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }
}
