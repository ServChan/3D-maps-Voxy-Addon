package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class WaypointSyncSettings {
    private static final Pattern VOXY_WORLD_ID = Pattern.compile("[0-9a-f]{32}");
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("voxymap-sync.properties");
    private static final Path BACKUP_PATH = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".bak");
    private static final Path TEMP_PATH = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");

    private record Config(String address, String password) {
    }

    private static boolean loaded = false;
    private static final ConcurrentHashMap<String, Config> byWorld = new ConcurrentHashMap<>();

    private WaypointSyncSettings() {
    }

    public static String address(String worldId) {
        load();
        if (!validWorldId(worldId)) return null;
        Config config = byWorld.get(worldId);
        return config == null || config.address().isBlank() ? null : config.address();
    }

    public static String password(String worldId) {
        load();
        if (!validWorldId(worldId)) return null;
        Config config = byWorld.get(worldId);
        return config == null || config.password().isBlank() ? null : config.password();
    }

    public static void setAddress(String worldId, String address) {
        load();
        if (!validWorldId(worldId)) return;
        String trimmed = address == null ? "" : address.trim();
        Config current = byWorld.getOrDefault(worldId, new Config("", ""));
        if (trimmed.isEmpty()) {
            byWorld.remove(worldId);
        } else {
            byWorld.put(worldId, new Config(trimmed, current.password()));
        }
        save();
    }

    public static void setPassword(String worldId, String password) {
        load();
        if (!validWorldId(worldId)) return;
        Config current = byWorld.get(worldId);
        if (current == null || current.address().isBlank()) return;
        byWorld.put(worldId, new Config(current.address(), password == null ? "" : password.trim()));
        save();
    }

    private static boolean validWorldId(String worldId) {
        return worldId != null && VOXY_WORLD_ID.matcher(worldId).matches();
    }

    public static void load() {
        if (loaded) return;
        loaded = true;

        Properties properties = readProperties(CONFIG_PATH);
        if (properties == null) {
            properties = readProperties(BACKUP_PATH);
            if (properties != null) {
                VoxyMapClient.LOGGER.warn("[VoxyMap] Loaded sync settings from backup after the primary file failed.");
            }
        }
        if (properties == null) properties = new Properties();

        byWorld.clear();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("sync.") || !key.endsWith(".address")) continue;
            String worldId = key.substring("sync.".length(), key.length() - ".address".length());
            if (!validWorldId(worldId)) continue;
            String address = properties.getProperty(key, "");
            if (address.isBlank()) continue;
            String password = properties.getProperty("sync." + worldId + ".password", "");
            byWorld.put(worldId, new Config(address, password));
        }
    }

    public static void save() {
        Properties properties = new Properties();
        byWorld.forEach((worldId, config) -> {
            properties.setProperty("sync." + worldId + ".address", config.address());
            if (!config.password().isBlank()) {
                properties.setProperty("sync." + worldId + ".password", config.password());
            }
        });

        AtomicPropertiesWriter.save(CONFIG_PATH, BACKUP_PATH, TEMP_PATH, properties,
                "VoxyMap remote waypoint sync settings");
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
                VoxyMapClient.LOGGER.warn("[VoxyMap] Could not read sync settings from {}", path, exception);
            }
            return null;
        }
    }

}
