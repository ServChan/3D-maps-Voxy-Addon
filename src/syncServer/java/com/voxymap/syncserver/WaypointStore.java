package com.voxymap.syncserver;

import com.voxymap.shared.WaypointDto;
import com.voxymap.shared.WaypointJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

final class WaypointStore {
    private static final Pattern VOXY_WORLD_ID = Pattern.compile("[0-9a-f]{32}");

    private final Path dataDir;
    private final Map<String, List<WaypointDto>> byWorld = new ConcurrentHashMap<>();

    WaypointStore(Path dataDir) throws IOException {
        this.dataDir = dataDir;
        Files.createDirectories(dataDir);
        try (var files = Files.list(dataDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String fileName = file.getFileName().toString();
                String worldId = fileName.substring(0, fileName.length() - ".json".length());
                if (!isValidWorldId(worldId)) continue;
                try {
                    byWorld.put(worldId, new ArrayList<>(WaypointJson.readList(Files.readString(file, StandardCharsets.UTF_8))));
                } catch (IOException exception) {
                    System.err.println("[voxymap-sync-server] Could not read " + file + ": " + exception.getMessage());
                }
            }
        }
    }

    static boolean isValidWorldId(String worldId) {
        return worldId != null && VOXY_WORLD_ID.matcher(worldId).matches();
    }

    List<WaypointDto> list(String worldId) {
        List<WaypointDto> list = byWorld.getOrDefault(worldId, List.of());
        synchronized (list) {
            return List.copyOf(list);
        }
    }

    WaypointDto put(String worldId, WaypointDto waypoint) throws IOException {
        List<WaypointDto> list = byWorld.computeIfAbsent(worldId, key -> new ArrayList<>());
        synchronized (list) {
            list.removeIf(w -> w.id().equals(waypoint.id()));
            list.add(waypoint);
            save(worldId, list);
        }
        return waypoint;
    }

    boolean delete(String worldId, String id) throws IOException {
        List<WaypointDto> list = byWorld.get(worldId);
        if (list == null) {
            return false;
        }
        synchronized (list) {
            if (!list.removeIf(w -> w.id().equals(id))) {
                return false;
            }
            save(worldId, list);
        }
        return true;
    }

    private void save(String worldId, List<WaypointDto> list) throws IOException {
        Path target = dataDir.resolve(worldId + ".json");
        Path temp = dataDir.resolve(worldId + ".json.tmp");
        Files.writeString(temp, WaypointJson.writeList(list), StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
