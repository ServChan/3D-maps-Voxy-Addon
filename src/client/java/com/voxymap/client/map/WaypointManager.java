package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WaypointManager {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("voxymap-waypoints.properties");
    private static final Path BACKUP_PATH = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".bak");
    private static final Path TEMP_PATH = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");

    private static boolean loaded = false;
    private static final Map<String, List<Waypoint>> byWorld = new ConcurrentHashMap<>();
    private static final Map<String, List<Waypoint>> temporaryByWorld = new ConcurrentHashMap<>();
    private static final String TEMPORARY_PREFIX = "temp-";
    private static final double ARRIVAL_RADIUS_BLOCKS = 6.0;
    private static final Set<String> armedTemporaryIds = ConcurrentHashMap.newKeySet();

    private static volatile int localVersion = 0;
    private static final Map<String, MergedCacheEntry> mergedCache = new ConcurrentHashMap<>();

    private WaypointManager() {
    }

    public record Waypoint(String id, String name, double x, double y, double z, int color, boolean shared) {
        public boolean temporary() {
            return id.startsWith(TEMPORARY_PREFIX);
        }

        public String initial() {
            return WaypointPrefs.symbol(id, name);
        }
    }

    public enum SyncStatus { DISABLED, READ_ONLY, SYNCING, OK, ERROR }

    private record MergedCacheEntry(int localVersion, List<Waypoint> remoteSnapshot, List<Waypoint> merged) {
    }

    public static List<Waypoint> forWorld(String worldId) {
        load();
        if (!WaypointCodec.validWorldId(worldId)) return List.of();

        String address = WaypointSyncSettings.address(worldId);
        List<Waypoint> remote = address != null ? WaypointSyncClient.cached(worldId, address) : List.of();
        int version = localVersion;

        MergedCacheEntry cached = mergedCache.get(worldId);
        if (cached != null && cached.localVersion() == version && cached.remoteSnapshot() == remote) {
            return cached.merged();
        }

        List<Waypoint> local = byWorld.getOrDefault(worldId, List.of());
        List<Waypoint> temporary = temporaryByWorld.getOrDefault(worldId, List.of());
        List<Waypoint> result = new ArrayList<>(local.size() + temporary.size() + remote.size());
        result.addAll(local);
        result.addAll(temporary);
        for (Waypoint entry : remote) {
            result.add(entry.shared() ? entry : new Waypoint(entry.id(), entry.name(), entry.x(), entry.y(), entry.z(), entry.color(), true));
        }
        List<Waypoint> merged = List.copyOf(result);
        mergedCache.put(worldId, new MergedCacheEntry(version, remote, merged));
        return merged;
    }

    public static List<Waypoint> visibleForWorld(String worldId, WaypointDisplay.Surface surface) {
        List<Waypoint> all = forWorld(worldId);
        List<Waypoint> visible = new ArrayList<>(all.size());
        for (Waypoint waypoint : all) {
            if (WaypointPrefs.shownOn(waypoint.id(), surface)) {
                visible.add(waypoint);
            }
        }
        return visible;
    }

    public static boolean canShare(String worldId) {
        load();
        return WaypointSyncSettings.address(worldId) != null && WaypointSyncSettings.password(worldId) != null;
    }

    public static SyncStatus syncStatus(String worldId) {
        load();
        String address = WaypointSyncSettings.address(worldId);
        if (address == null) return SyncStatus.DISABLED;
        WaypointSyncClient.cached(worldId, address);
        boolean writable = WaypointSyncSettings.password(worldId) != null;
        return switch (WaypointSyncClient.status(worldId, address)) {
            case SYNCING -> SyncStatus.SYNCING;
            case ERROR -> SyncStatus.ERROR;
            case OK -> writable ? SyncStatus.OK : SyncStatus.READ_ONLY;
        };
    }

    public static String syncErrorDetail(String worldId) {
        load();
        String address = WaypointSyncSettings.address(worldId);
        return address == null ? null : WaypointSyncClient.lastError(worldId, address);
    }

    public static Waypoint add(String worldId, String name, double x, double y, double z, int color, boolean shared) {
        load();
        if (!WaypointCodec.validWorldId(worldId) || name == null || name.isBlank()) return null;

        if (shared) {
            String address = WaypointSyncSettings.address(worldId);
            String password = address != null ? WaypointSyncSettings.password(worldId) : null;
            if (password == null) return null;
            Waypoint waypoint = new Waypoint(UUID.randomUUID().toString(), name.trim(), x, y, z, color, true);
            WaypointSyncClient.add(worldId, address, password, waypoint);
            return waypoint;
        }

        List<Waypoint> list = byWorld.computeIfAbsent(worldId, key -> new CopyOnWriteArrayList<>());
        Waypoint waypoint = new Waypoint(UUID.randomUUID().toString(), name.trim(), x, y, z, color, false);
        list.add(waypoint);
        localVersion++;
        save();
        return waypoint;
    }

    public static Waypoint addTemporary(String worldId, String name, double x, double y, double z, int color) {
        load();
        if (!WaypointCodec.validWorldId(worldId) || name == null || name.isBlank()) return null;
        Waypoint waypoint = new Waypoint(TEMPORARY_PREFIX + UUID.randomUUID(), name.trim(), x, y, z, color, false);
        temporaryByWorld.computeIfAbsent(worldId, key -> new CopyOnWriteArrayList<>()).add(waypoint);
        localVersion++;
        return waypoint;
    }

    public static List<Waypoint> removeArrived(String worldId, double x, double z) {
        List<Waypoint> temporary = temporaryByWorld.get(worldId);
        if (temporary == null || temporary.isEmpty()) return List.of();
        List<Waypoint> arrived = new ArrayList<>();
        for (Waypoint waypoint : temporary) {
            double distance = Math.hypot(waypoint.x() - x, waypoint.z() - z);
            if (distance > ARRIVAL_RADIUS_BLOCKS * 2) {
                armedTemporaryIds.add(waypoint.id());
            } else if (distance <= ARRIVAL_RADIUS_BLOCKS && armedTemporaryIds.contains(waypoint.id())) {
                arrived.add(waypoint);
            }
        }
        if (!arrived.isEmpty()) {
            temporary.removeAll(arrived);
            arrived.forEach(waypoint -> armedTemporaryIds.remove(waypoint.id()));
            localVersion++;
        }
        return arrived;
    }

    public static boolean editable(String worldId, Waypoint waypoint) {
        return waypoint != null && (!waypoint.shared() || canShare(worldId));
    }

    public static void update(String worldId, String id, String name, double x, double y, double z, int color) {
        load();
        if (!WaypointCodec.validWorldId(worldId) || id == null || name == null || name.isBlank()) return;

        for (Map<String, List<Waypoint>> store : List.of(byWorld, temporaryByWorld)) {
            List<Waypoint> list = store.get(worldId);
            if (list == null) continue;
            for (int i = 0; i < list.size(); i++) {
                Waypoint current = list.get(i);
                if (current.id().equals(id)) {
                    list.set(i, new Waypoint(current.id(), name.trim(), x, y, z, color, false));
                    localVersion++;
                    if (store == byWorld) {
                        save();
                    }
                    return;
                }
            }
        }

        String address = WaypointSyncSettings.address(worldId);
        if (address == null) return;
        String password = WaypointSyncSettings.password(worldId);
        if (password == null) return;
        for (Waypoint current : WaypointSyncClient.cached(worldId, address)) {
            if (current.id().equals(id)) {
                WaypointSyncClient.add(worldId, address, password, new Waypoint(current.id(), name.trim(), x, y, z, color, true));
                return;
            }
        }
    }

    public static int suggestedColor(String worldId) {
        load();
        int count = forWorld(worldId).size();
        return WaypointCodec.PALETTE[count % WaypointCodec.PALETTE.length];
    }

    public static int[] palette() {
        return WaypointCodec.PALETTE.clone();
    }

    public static void remove(String worldId, String id) {
        load();
        if (!WaypointCodec.validWorldId(worldId) || id == null) return;
        WaypointPrefs.forget(id);

        List<Waypoint> temporary = temporaryByWorld.get(worldId);
        if (temporary != null && temporary.removeIf(w -> w.id().equals(id))) {
            localVersion++;
            return;
        }

        List<Waypoint> list = byWorld.get(worldId);
        if (list != null && list.removeIf(w -> w.id().equals(id))) {
            localVersion++;
            save();
            return;
        }

        String address = WaypointSyncSettings.address(worldId);
        if (address == null) return;
        String password = WaypointSyncSettings.password(worldId);
        if (password == null) return;
        WaypointSyncClient.remove(worldId, address, password, id);
    }

    public static boolean restore(String worldId, Waypoint waypoint) {
        load();
        if (!WaypointCodec.validWorldId(worldId) || waypoint == null) return false;
        if (forWorld(worldId).stream().anyMatch(existing -> existing.id().equals(waypoint.id()))) return true;
        if (waypoint.shared()) {
            String address = WaypointSyncSettings.address(worldId);
            String password = address != null ? WaypointSyncSettings.password(worldId) : null;
            if (password == null) return false;
            WaypointSyncClient.add(worldId, address, password, waypoint);
            return true;
        }
        if (waypoint.temporary()) {
            temporaryByWorld.computeIfAbsent(worldId, key -> new CopyOnWriteArrayList<>()).add(waypoint);
            localVersion++;
            return true;
        }
        byWorld.computeIfAbsent(worldId, key -> new CopyOnWriteArrayList<>()).add(waypoint);
        localVersion++;
        save();
        return true;
    }

    public static void load() {
        if (loaded) return;
        loaded = true;

        Properties properties = readProperties(CONFIG_PATH);
        if (properties == null) {
            properties = readProperties(BACKUP_PATH);
            if (properties != null) {
                VoxyMapClient.LOGGER.warn("[VoxyMap] Loaded waypoints from backup after the primary file failed.");
            }
        }
        if (properties == null) properties = new Properties();

        byWorld.clear();
        WaypointCodec.parse(properties).forEach((worldId, list) -> byWorld.put(worldId, new CopyOnWriteArrayList<>(list)));
        localVersion++;
    }

    public static void save() {
        Properties properties = WaypointCodec.serialize(byWorld);
        AtomicPropertiesWriter.save(CONFIG_PATH, BACKUP_PATH, TEMP_PATH, properties, "VoxyMap waypoints");
    }

    private static Properties readProperties(Path path) {
        return readProperties(path, true);
    }

    private static Properties readProperties(Path path, boolean logFailure) {
        if (!Files.exists(path)) return null;
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException | IllegalArgumentException exception) {
            if (logFailure) {
                VoxyMapClient.LOGGER.warn("[VoxyMap] Could not read waypoints from {}", path, exception);
            }
            return null;
        }
    }

}
