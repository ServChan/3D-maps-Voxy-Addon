package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Player-placed map pins, persisted per Voxy world id - the same identifier
 * {@link VoxyMapSettings}'s VanillaSquad map selection is keyed on, since raw block
 * coordinates are only meaningful within a single world. Parsing/serialization is delegated to
 * {@link WaypointCodec}, which has no FabricLoader dependency and stays unit-testable.
 *
 * <p>A world can hold both local-only pins and server-shared pins at once - {@link #forWorld} returns
 * the union, each tagged via {@link Waypoint#shared()}. {@link #rename} and {@link #remove} look the
 * waypoint up by id to find which backend (local file or sync server) actually owns it, rather than
 * assuming "this world has sync configured" means every pin in it is shared.
 */
public final class WaypointManager {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("voxymap-waypoints.properties");
    private static final Path BACKUP_PATH = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".bak");
    private static final Path TEMP_PATH = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");

    private static boolean loaded = false;
    private static final Map<String, List<Waypoint>> byWorld = new ConcurrentHashMap<>();
    // byWorld's lists are mutated in place by add/rename/remove (see those methods), so their
    // references never change - this version instead marks "something local changed" for forWorld's
    // cache below, bumped on every local mutation and on load().
    private static volatile int localVersion = 0;
    private static final Map<String, MergedCacheEntry> mergedCache = new ConcurrentHashMap<>();

    private WaypointManager() {
    }

    public record Waypoint(String id, String name, double x, double y, double z, int color, boolean shared) {
    }

    public enum SyncStatus { DISABLED, READ_ONLY, SYNCING, OK, ERROR }

    /** Caches {@link #forWorld}'s merged local+remote result per world, keyed on the two things that
     * actually change it: {@link #localVersion} (bumped by add/rename/remove/load) and the remote list's
     * own identity ({@link WaypointSyncClient#cached} only ever replaces that reference, never mutates
     * it in place, on a successful background refresh - see that method's own javadoc). Without this,
     * every caller of forWorld() - critically the always-on corner minimap, not just the occasionally-
     * open fullscreen map - reallocated and re-copied the full waypoint list every single render frame,
     * even though the underlying data is almost always unchanged between one frame and the next. */
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
        List<Waypoint> result = new ArrayList<>(local.size() + remote.size());
        result.addAll(local);
        for (Waypoint entry : remote) {
            result.add(entry.shared() ? entry : new Waypoint(entry.id(), entry.name(), entry.x(), entry.y(), entry.z(), entry.color(), true));
        }
        List<Waypoint> merged = List.copyOf(result);
        mergedCache.put(worldId, new MergedCacheEntry(version, remote, merged));
        return merged;
    }

    /** Whether this world can currently accept new shared (server-synced) waypoints - the add-waypoint
     * menu uses this to decide whether to offer the "share to server" option at all. */
    public static boolean canShare(String worldId) {
        load();
        return WaypointSyncSettings.address(worldId) != null && WaypointSyncSettings.password(worldId) != null;
    }

    /** Read-only sync (address without a password) reports {@link SyncStatus#READ_ONLY} rather than
     * {@link SyncStatus#OK} so the settings panel can explain why shared pins can't be added/edited.
     * Also doubles as the "keep the background refresh alive" call for worlds whose pins aren't
     * currently drawn (e.g. while {@code waypointsEnabled} is off but the sync rows are still visible). */
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

    /** The most recent transport-level failure reason for this world's sync connection (timeout,
     * connection refused, HTTP status, ...), or null if the last attempt succeeded or none has run
     * yet. Surfaced in the settings panel so "Ошибка" isn't a dead end. */
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
            if (password == null) return null; // Not shareable: refuse rather than silently keeping it local.
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

    /** A color the add-waypoint menu can preselect, cycling through the palette by how many pins this
     * world already has (local and shared combined) - purely a starting suggestion, not persisted. */
    public static int suggestedColor(String worldId) {
        load();
        int count = forWorld(worldId).size();
        return WaypointCodec.PALETTE[count % WaypointCodec.PALETTE.length];
    }

    /** The fixed color choices the add-waypoint menu offers. */
    public static int[] palette() {
        return WaypointCodec.PALETTE.clone();
    }

    public static void rename(String worldId, String id, String newName) {
        load();
        if (!WaypointCodec.validWorldId(worldId) || id == null || newName == null || newName.isBlank()) return;

        List<Waypoint> list = byWorld.get(worldId);
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                Waypoint current = list.get(i);
                if (current.id().equals(id)) {
                    list.set(i, new Waypoint(current.id(), newName.trim(), current.x(), current.y(), current.z(), current.color(), false));
                    localVersion++;
                    save();
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
                WaypointSyncClient.add(worldId, address, password,
                        new Waypoint(current.id(), newName.trim(), current.x(), current.y(), current.z(), current.color(), true));
                return;
            }
        }
    }

    public static void remove(String worldId, String id) {
        load();
        if (!WaypointCodec.validWorldId(worldId) || id == null) return;

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
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = new OutputStreamWriter(Files.newOutputStream(TEMP_PATH), StandardCharsets.UTF_8)) {
                properties.store(writer, "VoxyMap waypoints");
            }
            // Preserve the last known-good backup if the primary file is corrupt. Blindly copying
            // an unreadable primary here would destroy the only recoverable waypoint snapshot.
            if (readProperties(CONFIG_PATH, false) != null) {
                Files.copy(CONFIG_PATH, BACKUP_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
            replaceAtomically(TEMP_PATH, CONFIG_PATH);
        } catch (IOException exception) {
            VoxyMapClient.LOGGER.error("[VoxyMap] Could not save waypoints to {}", CONFIG_PATH, exception);
            try {
                Files.deleteIfExists(TEMP_PATH);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
        }
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

    private static void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
