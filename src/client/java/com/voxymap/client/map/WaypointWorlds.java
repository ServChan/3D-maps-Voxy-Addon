package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WaypointWorlds {
    public static final String MAIN = "";
    public static final int MAX_NAME_LENGTH = 32;
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("voxymap-waypoint-worlds.properties");
    private static final Path BACKUP_PATH = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".bak");
    private static final Path TEMP_PATH = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
    private static final String WORLD_PREFIX = "world.";
    private static final String SELECTED_PREFIX = "selected.";

    public record SubWorld(String id, String name) {
    }

    private static final Map<String, Map<String, String>> NAMES = new ConcurrentHashMap<>();
    private static final Map<String, String> SELECTED = new ConcurrentHashMap<>();
    private static final Map<String, String> DERIVED = new ConcurrentHashMap<>();
    private static volatile String currentServerKey;
    private static volatile boolean loaded;
    private static volatile int version;

    private WaypointWorlds() {
    }

    public static int version() {
        return version;
    }

    public static String currentServerKey() {
        return currentServerKey;
    }

    public static void refreshServerKey(Minecraft minecraft) {
        currentServerKey = serverKey(minecraft);
    }

    static String serverKey(Minecraft minecraft) {
        if (minecraft == null) return null;
        ServerData server = minecraft.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return "server:" + server.ip.trim().toLowerCase(Locale.ROOT);
        }
        IntegratedServer local = minecraft.getSingleplayerServer();
        if (local != null) {
            return "local:" + local.getWorldData().getLevelName();
        }
        return null;
    }

    public static List<SubWorld> list(String serverKey) {
        load();
        List<SubWorld> result = new ArrayList<>();
        if (serverKey == null) return result;
        NAMES.getOrDefault(serverKey, Map.of()).forEach((id, name) -> result.add(new SubWorld(id, name)));
        result.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return result;
    }

    public static String selected(String serverKey) {
        load();
        if (serverKey == null) return MAIN;
        String id = SELECTED.getOrDefault(serverKey, MAIN);
        return id.equals(MAIN) || NAMES.getOrDefault(serverKey, Map.of()).containsKey(id) ? id : MAIN;
    }

    public static String selectedName(String serverKey) {
        String id = selected(serverKey);
        return id.equals(MAIN) ? null : NAMES.getOrDefault(serverKey, Map.of()).get(id);
    }

    public static void select(String serverKey, String id) {
        load();
        if (serverKey == null) return;
        String target = id == null ? MAIN : id;
        String previous = target.equals(MAIN) ? SELECTED.remove(serverKey) : SELECTED.put(serverKey, target);
        if (!target.equals(previous == null ? MAIN : previous)) {
            save();
        }
    }

    public static SubWorld create(String serverKey, String name) {
        load();
        String cleaned = cleanName(name);
        if (serverKey == null || cleaned == null) return null;
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        NAMES.computeIfAbsent(serverKey, key -> new ConcurrentHashMap<>()).put(id, cleaned);
        save();
        return new SubWorld(id, cleaned);
    }

    public static void rename(String serverKey, String id, String name) {
        load();
        String cleaned = cleanName(name);
        Map<String, String> worlds = serverKey == null ? null : NAMES.get(serverKey);
        if (worlds == null || cleaned == null || !worlds.containsKey(id)) return;
        worlds.put(id, cleaned);
        save();
    }

    public static void delete(String serverKey, String id) {
        load();
        Map<String, String> worlds = serverKey == null ? null : NAMES.get(serverKey);
        if (worlds == null || worlds.remove(id) == null) return;
        if (id.equals(SELECTED.get(serverKey))) {
            SELECTED.remove(serverKey);
        }
        save();
    }

    public static String effective(String voxyWorldId) {
        if (voxyWorldId == null) return null;
        String sub = selected(currentServerKey);
        if (sub.isEmpty()) return voxyWorldId;
        return DERIVED.computeIfAbsent(voxyWorldId + "#" + sub, key -> derive(voxyWorldId, sub));
    }

    static String derive(String voxyWorldId, String subWorldId) {
        if (voxyWorldId == null || subWorldId == null || subWorldId.isEmpty()) return voxyWorldId;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((voxyWorldId + "#" + subWorldId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static String cleanName(String name) {
        if (name == null) return null;
        String trimmed = name.strip();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }

    private static void load() {
        if (loaded) return;
        synchronized (WaypointWorlds.class) {
            if (loaded) return;
            Properties properties = read(CONFIG_PATH);
            if (properties == null) {
                properties = read(BACKUP_PATH);
            }
            if (properties != null) {
                for (String key : properties.stringPropertyNames()) {
                    String value = properties.getProperty(key);
                    if (key.startsWith(SELECTED_PREFIX)) {
                        SELECTED.put(key.substring(SELECTED_PREFIX.length()), value);
                    } else if (key.startsWith(WORLD_PREFIX)) {
                        String rest = key.substring(WORLD_PREFIX.length());
                        int split = rest.lastIndexOf('/');
                        String name = cleanName(value);
                        if (split > 0 && split < rest.length() - 1 && name != null) {
                            NAMES.computeIfAbsent(rest.substring(0, split), server -> new ConcurrentHashMap<>())
                                    .put(rest.substring(split + 1), name);
                        }
                    }
                }
            }
            loaded = true;
        }
    }

    private static void save() {
        version++;
        Properties properties = new Properties();
        SELECTED.forEach((server, id) -> properties.setProperty(SELECTED_PREFIX + server, id));
        NAMES.forEach((server, worlds) -> worlds.forEach((id, name) ->
                properties.setProperty(WORLD_PREFIX + server + "/" + id, name)));
        AtomicPropertiesWriter.save(CONFIG_PATH, BACKUP_PATH, TEMP_PATH, properties, "VoxyMap waypoint worlds per server");
    }

    private static Properties read(Path path) {
        if (!Files.exists(path)) return null;
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException | IllegalArgumentException exception) {
            VoxyMapClient.LOGGER.warn("Could not read waypoint worlds from {}", path, exception);
            return null;
        }
    }
}
