package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;
import com.voxymap.shared.WaypointDto;
import com.voxymap.shared.WaypointJson;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class WaypointSyncClient {
    private static final Duration REFRESH_INTERVAL = Duration.ofSeconds(4);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final long RETRY_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final long MAX_LIST_BYTES = 4L * 1024 * 1024;
    private static final String PASSWORD_HEADER = "X-VoxyMap-Password";
    private static final String PASSWORD_BASE64_HEADER = "X-VoxyMap-Password-B64";
    private static final long NEVER = Long.MIN_VALUE / 2;

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

    enum Outcome { DONE, REJECTED, RETRY_LATER }

    private WaypointSyncClient() {
    }

    private static final class State {
        final String worldId;
        final String address;
        volatile String password;
        volatile List<WaypointManager.Waypoint> server = List.of();
        volatile List<WaypointManager.Waypoint> cached = List.of();
        volatile long lastFetchStartNanos = NEVER;
        volatile long lastRetryNanos = NEVER;
        volatile boolean inFlight = false;
        volatile Status status = Status.SYNCING;
        volatile String lastError = null;
        volatile String mutationError = null;
        final AtomicLong mutations = new AtomicLong();
        final AtomicBoolean sending = new AtomicBoolean();
        final Map<String, WaypointManager.Waypoint> pendingPuts = new ConcurrentHashMap<>();
        final Set<String> pendingDeletes = ConcurrentHashMap.newKeySet();

        State(String worldId, String address) {
            this.worldId = worldId;
            this.address = address;
        }

        synchronized void rebuildCached() {
            List<WaypointManager.Waypoint> merged = new ArrayList<>();
            for (WaypointManager.Waypoint waypoint : server) {
                if (!pendingDeletes.contains(waypoint.id()) && !pendingPuts.containsKey(waypoint.id())) {
                    merged.add(waypoint);
                }
            }
            merged.addAll(pendingPuts.values());
            cached = List.copyOf(merged);
        }

        boolean hasPending() {
            return !pendingPuts.isEmpty() || !pendingDeletes.isEmpty();
        }
    }

    static List<WaypointManager.Waypoint> cached(String worldId, String address) {
        State state = stateFor(worldId, address);
        maybeRefresh(state);
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
        state.password = password;
        state.pendingDeletes.remove(waypoint.id());
        state.pendingPuts.put(waypoint.id(), waypoint);
        state.rebuildCached();
        state.mutations.incrementAndGet();
        pump(state);
    }

    static void remove(String worldId, String address, String password, String id) {
        State state = stateFor(worldId, address);
        state.password = password;
        state.pendingPuts.remove(id);
        state.pendingDeletes.add(id);
        state.rebuildCached();
        state.mutations.incrementAndGet();
        pump(state);
    }

    private static State stateFor(String worldId, String address) {
        return STATES.computeIfAbsent(worldId + '\u0000' + address, key -> new State(worldId, address));
    }

    static boolean due(long now, long last, long intervalNanos) {
        return now - last >= intervalNanos;
    }

    private static void maybeRefresh(State state) {
        long now = System.nanoTime();
        if (state.hasPending() && !state.sending.get() && due(now, state.lastRetryNanos, RETRY_INTERVAL_NANOS)) {
            state.lastRetryNanos = now;
            state.password = WaypointSyncSettings.password(state.worldId);
            pump(state);
        }
        if (state.inFlight || !due(now, state.lastFetchStartNanos, REFRESH_INTERVAL.toNanos())) {
            return;
        }
        state.inFlight = true;
        state.lastFetchStartNanos = now;
        long mutationsAtStart = state.mutations.get();

        String url = normalize(state.address) + "/waypoints/" + state.worldId;
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
            if (REPORTED.add(state.worldId + " " + state.address + " " + state.lastError)) {
                VoxyMapClient.LOGGER.warn("Could not use waypoint sync address {} for {}", state.address, state.worldId, exception);
            }
            return;
        }

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.limiting(
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), MAX_LIST_BYTES))
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
                        state.lastFetchStartNanos = NEVER;
                        return;
                    }
                    List<WaypointDto> parsed;
                    try {
                        parsed = WaypointJson.readListStrict(response.body());
                    } catch (RuntimeException exception) {
                        state.status = Status.ERROR;
                        state.lastError = "The sync server sent an invalid waypoint list";
                        if (REPORTED.add(url + " " + state.lastError)) {
                            VoxyMapClient.LOGGER.warn("Could not read synced waypoints from {}", url, exception);
                        }
                        return;
                    }
                    state.server = fromDtos(parsed);
                    state.rebuildCached();
                    String mutationError = state.mutationError;
                    state.status = mutationError == null ? Status.OK : Status.ERROR;
                    state.lastError = mutationError;
                });
    }

    private static void pump(State state) {
        if (!state.sending.compareAndSet(false, true)) {
            return;
        }
        String deleteId = state.pendingDeletes.stream().findFirst().orElse(null);
        if (deleteId != null) {
            mutate(state, "DELETE", HttpRequest.BodyPublishers.noBody(), deleteId, true,
                    () -> state.pendingDeletes.remove(deleteId), () -> {
                    });
            return;
        }
        WaypointManager.Waypoint put = state.pendingPuts.values().stream().findFirst().orElse(null);
        if (put != null) {
            mutate(state, "PUT",
                    HttpRequest.BodyPublishers.ofString(WaypointJson.writeOne(toDto(put)), StandardCharsets.UTF_8),
                    put.id(), false, () -> state.pendingPuts.remove(put.id(), put), () -> keepLocally(state, put));
            return;
        }
        state.sending.set(false);
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    static boolean asciiPassword(String password) {
        return password == null || password.chars().allMatch(c -> c >= 0x20 && c < 0x7F);
    }

    static String base64Password(String password) {
        return Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));
    }

    static Outcome classify(int statusCode, boolean missingIsDone) {
        if (statusCode < 400 || missingIsDone && statusCode == 404) {
            return Outcome.DONE;
        }
        if (statusCode == 400 || statusCode == 403 || statusCode == 404 || statusCode == 413) {
            return Outcome.REJECTED;
        }
        return Outcome.RETRY_LATER;
    }

    private static void keepLocally(State state, WaypointManager.Waypoint waypoint) {
        boolean onServer = state.server.stream().anyMatch(existing -> existing.id().equals(waypoint.id()));
        if (onServer) {
            return;
        }
        WaypointManager.Waypoint local = new WaypointManager.Waypoint(waypoint.id(), waypoint.name(), waypoint.x(),
                waypoint.y(), waypoint.z(), waypoint.color(), false);
        Minecraft.getInstance().execute(() -> WaypointManager.restore(state.worldId, local));
    }

    private static void mutate(State state, String method, HttpRequest.BodyPublisher body, String id,
                               boolean missingIsDone, Runnable dropPending, Runnable onRejected) {
        HttpRequest request;
        try {
            String password = state.password;
            HttpRequest.Builder builder = HttpRequest.newBuilder(
                            URI.create(normalize(state.address) + "/waypoints/" + state.worldId + "/" + id))
                    .timeout(REQUEST_TIMEOUT)
                    .method(method, body);
            if (asciiPassword(password)) {
                builder.header(PASSWORD_HEADER, password == null ? "" : password);
            } else {
                builder.header(PASSWORD_BASE64_HEADER, base64Password(password));
            }
            request = builder.build();
        } catch (RuntimeException exception) {
            failMutation(state, "Bad address or password: " + exception.getMessage());
            VoxyMapClient.LOGGER.warn("Could not send waypoint sync {} for {}", method, state.worldId, exception);
            state.sending.set(false);
            return;
        }
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .orTimeout(REQUEST_TIMEOUT.toSeconds() + 1, TimeUnit.SECONDS)
                .whenComplete((response, error) -> {
                    Outcome outcome;
                    if (error != null) {
                        outcome = Outcome.RETRY_LATER;
                        failMutation(state, rootCause(error).toString());
                        VoxyMapClient.LOGGER.warn("Could not send waypoint sync {} for {}", method, state.worldId, rootCause(error));
                    } else {
                        outcome = classify(response.statusCode(), missingIsDone);
                        if (outcome != Outcome.DONE) {
                            failMutation(state, "HTTP " + response.statusCode());
                            VoxyMapClient.LOGGER.warn("Waypoint sync server rejected {} for {} with HTTP {}", method,
                                    state.worldId, response.statusCode());
                        }
                    }
                    if (outcome != Outcome.RETRY_LATER) {
                        dropPending.run();
                        state.rebuildCached();
                    }
                    if (outcome == Outcome.REJECTED) {
                        onRejected.run();
                    }
                    if (outcome == Outcome.DONE && !state.hasPending()) {
                        state.mutationError = null;
                    }
                    state.mutations.incrementAndGet();
                    state.lastFetchStartNanos = NEVER;
                    state.sending.set(false);
                    if (outcome != Outcome.RETRY_LATER) {
                        pump(state);
                    }
                });
    }

    private static void failMutation(State state, String message) {
        state.mutationError = message;
        state.lastError = message;
        state.status = Status.ERROR;
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
