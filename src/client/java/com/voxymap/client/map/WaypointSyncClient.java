package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;
import com.voxymap.shared.WaypointDto;
import com.voxymap.shared.WaypointJson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class WaypointSyncClient {
    private static final Duration REFRESH_INTERVAL = Duration.ofSeconds(4);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final String PASSWORD_HEADER = "X-VoxyMap-Password";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(REQUEST_TIMEOUT)
            .executor(Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "VoxyMap-WaypointSync");
                thread.setDaemon(true);
                return thread;
            }))
            .build();

    private static final ConcurrentHashMap<String, State> STATES = new ConcurrentHashMap<>();
    private static final java.util.Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    enum Status { SYNCING, OK, ERROR }

    private WaypointSyncClient() {
    }

    private static final class State {
        volatile List<WaypointManager.Waypoint> cached = List.of();
        volatile long lastFetchStartNanos = Long.MIN_VALUE;
        volatile boolean inFlight = false;
        volatile Status status = Status.SYNCING;
        volatile String lastError = null;
        final AtomicLong mutations = new AtomicLong();
    }

    static List<WaypointManager.Waypoint> cached(String worldId, String address) {
        State state = stateFor(worldId, address);
        maybeRefresh(worldId, address, state);
        return state.cached;
    }

    static Status status(String worldId, String address) {
        return stateFor(worldId, address).status;
    }

    static String lastError(String worldId, String address) {
        return stateFor(worldId, address).lastError;
    }

    static void add(String worldId, String address, String password, WaypointManager.Waypoint waypoint) {
        State state = stateFor(worldId, address);
        List<WaypointManager.Waypoint> updated = new ArrayList<>(state.cached);
        updated.removeIf(w -> w.id().equals(waypoint.id()));
        updated.add(waypoint);
        state.cached = List.copyOf(updated);
        state.mutations.incrementAndGet();

        mutate(worldId, address, "PUT",
                HttpRequest.BodyPublishers.ofString(WaypointJson.writeOne(toDto(waypoint)), StandardCharsets.UTF_8),
                waypoint.id(), password);
    }

    static void remove(String worldId, String address, String password, String id) {
        State state = stateFor(worldId, address);
        List<WaypointManager.Waypoint> updated = new ArrayList<>(state.cached);
        updated.removeIf(w -> w.id().equals(id));
        state.cached = List.copyOf(updated);
        state.mutations.incrementAndGet();

        mutate(worldId, address, "DELETE", HttpRequest.BodyPublishers.noBody(), id, password);
    }

    private static State stateFor(String worldId, String address) {
        return STATES.computeIfAbsent(worldId + '\u0000' + address, key -> new State());
    }

    private static void maybeRefresh(String worldId, String address, State state) {
        long now = System.nanoTime();
        if (state.inFlight || now - state.lastFetchStartNanos < REFRESH_INTERVAL.toNanos()) {
            return;
        }
        state.inFlight = true;
        state.lastFetchStartNanos = now;
        long mutationsAtStart = state.mutations.get();

        String url = normalize(address) + "/waypoints/" + worldId;
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
        } catch (RuntimeException exception) {
            state.inFlight = false;
            state.status = Status.ERROR;
            state.lastError = "Bad address: " + exception.getMessage();
            if (REPORTED.add(worldId + " " + address + " " + state.lastError)) {
                VoxyMapClient.LOGGER.warn("Could not use waypoint sync address {} for {}", address, worldId, exception);
            }
            return;
        }

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .orTimeout(REQUEST_TIMEOUT.toSeconds() + 1, TimeUnit.SECONDS)
                .whenComplete((response, error) -> {
                    state.inFlight = false;
                    if (error != null) {
                        state.status = Status.ERROR;
                        state.lastError = rootCause(error).toString();
                        if (REPORTED.add(url + " " + state.lastError)) {
                            VoxyMapClient.LOGGER.warn("Could not fetch synced waypoints from {}", url, rootCause(error));
                        }
                        return;
                    }
                    if (response.statusCode() != 200) {
                        state.status = Status.ERROR;
                        state.lastError = "HTTP " + response.statusCode();
                        if (REPORTED.add(url + " " + state.lastError)) {
                            VoxyMapClient.LOGGER.warn("Could not fetch synced waypoints from {}; the server answered HTTP {}", url, response.statusCode());
                        }
                        return;
                    }
                    if (state.mutations.get() != mutationsAtStart) {
                        state.lastFetchStartNanos = Long.MIN_VALUE;
                        return;
                    }
                    state.cached = fromDtos(WaypointJson.readList(response.body()));
                    state.status = Status.OK;
                    state.lastError = null;
                });
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static void mutate(String worldId, String address, String method, HttpRequest.BodyPublisher body,
                               String id, String password) {
        State state = stateFor(worldId, address);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(normalize(address) + "/waypoints/" + worldId + "/" + id))
                    .timeout(REQUEST_TIMEOUT)
                    .header(PASSWORD_HEADER, password == null ? "" : password)
                    .method(method, body)
                    .build();
        } catch (RuntimeException exception) {
            state.status = Status.ERROR;
            state.lastError = "Bad address or password: " + exception.getMessage();
            VoxyMapClient.LOGGER.warn("Could not send waypoint sync {} for {}", method, worldId, exception);
            return;
        }
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .orTimeout(REQUEST_TIMEOUT.toSeconds() + 1, TimeUnit.SECONDS)
                .whenComplete((response, error) -> {
                    if (error != null) {
                        state.lastError = rootCause(error).toString();
                        VoxyMapClient.LOGGER.warn("Could not send waypoint sync {} for {}", method, worldId, rootCause(error));
                        state.status = Status.ERROR;
                    } else if (response.statusCode() >= 400) {
                        state.lastError = "HTTP " + response.statusCode();
                        VoxyMapClient.LOGGER.warn("Waypoint sync server rejected {} for {} with HTTP {}", method, worldId, response.statusCode());
                        state.status = Status.ERROR;
                    }

                    state.mutations.incrementAndGet();
                    state.lastFetchStartNanos = Long.MIN_VALUE;
                });
    }

    private static String normalize(String address) {
        String trimmed = address.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://" + trimmed;
        }
        return trimmed;
    }

    private static List<WaypointManager.Waypoint> fromDtos(List<WaypointDto> dtos) {
        List<WaypointManager.Waypoint> result = new ArrayList<>(dtos.size());
        for (WaypointDto dto : dtos) {
            result.add(new WaypointManager.Waypoint(dto.id(), dto.name(), dto.x(), dto.y(), dto.z(), dto.color(), true));
        }
        return List.copyOf(result);
    }

    private static WaypointDto toDto(WaypointManager.Waypoint waypoint) {
        return new WaypointDto(waypoint.id(), waypoint.name(), waypoint.x(), waypoint.y(), waypoint.z(), waypoint.color());
    }
}
