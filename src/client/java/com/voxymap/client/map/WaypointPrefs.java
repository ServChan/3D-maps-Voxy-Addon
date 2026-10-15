package com.voxymap.client.map;

import com.voxymap.client.VoxyMapClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public final class WaypointPrefs {
    public static final int MAX_SYMBOL_LENGTH = 2;
    private static final String TEMPORARY_ID_PREFIX = ".temp-";
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("voxymap-waypoint-prefs.properties");
    private static final Path BACKUP_PATH = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".bak");
    private static final Path TEMP_PATH = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
    private static final String LEGACY_HIDDEN_PREFIX = "hidden.";
    private static final String DISPLAY_PREFIX = "display.";
    private static final String SYMBOL_PREFIX = "symbol.";

    private static final Map<String, WaypointDisplay> DISPLAYS = new ConcurrentHashMap<>();
    private static final Map<String, String> SYMBOLS = new ConcurrentHashMap<>();
    private static volatile boolean loaded;
    private static volatile int version;

    private WaypointPrefs() {
    }

    public static WaypointDisplay display(String id) {
        load();
        if (id == null) return WaypointDisplay.EVERYWHERE;
        return DISPLAYS.getOrDefault(id, WaypointDisplay.EVERYWHERE);
    }

    public static boolean shownOn(String id, WaypointDisplay.Surface surface) {
        return display(id).shows(surface);
    }

    public static boolean hidden(String id) {
        return display(id) == WaypointDisplay.HIDDEN;
    }

    public static void setDisplay(String id, WaypointDisplay display) {
        load();
        if (id == null) return;
        WaypointDisplay target = display == null ? WaypointDisplay.EVERYWHERE : display;
        WaypointDisplay previous = target == WaypointDisplay.EVERYWHERE ? DISPLAYS.remove(id) : DISPLAYS.put(id, target);
        if ((previous == null ? WaypointDisplay.EVERYWHERE : previous) != target) {
            save();
        }
    }

    public static String customSymbol(String id) {
        load();
        return id == null ? null : SYMBOLS.get(id);
    }

    public static String symbol(String id, String name) {
        String custom = customSymbol(id);
        return custom != null ? custom : WaypointText.initial(name);
    }

    public static void setSymbol(String id, String symbol) {
        load();
        if (id == null) return;
        String cleaned = WaypointText.cleanSymbol(symbol);
        String previous = cleaned == null ? SYMBOLS.remove(id) : SYMBOLS.put(id, cleaned);
        if (!Objects.equals(previous, cleaned)) {
            save();
        }
    }

    public static void forget(String id) {
        load();
        if (id == null) return;
        boolean changed = DISPLAYS.remove(id) != null;
        changed |= SYMBOLS.remove(id) != null;
        if (changed) {
            save();
        }
    }

    public static void preload() {
        load();
    }

    private static void load() {
        if (loaded) return;
        synchronized (WaypointPrefs.class) {
            if (loaded) return;
            Properties properties = AtomicPropertiesWriter.looksTruncated(CONFIG_PATH) ? null : read(CONFIG_PATH);
            if (properties == null) {
                properties = read(BACKUP_PATH);
            }
            if (properties != null) {
                for (String key : properties.stringPropertyNames()) {
                    String value = properties.getProperty(key);
                    if (key.contains(TEMPORARY_ID_PREFIX)) {
                        continue;
                    }
                    if (key.startsWith(LEGACY_HIDDEN_PREFIX) && "true".equals(value)) {
                        DISPLAYS.putIfAbsent(key.substring(LEGACY_HIDDEN_PREFIX.length()), WaypointDisplay.HIDDEN);
                    } else if (key.startsWith(DISPLAY_PREFIX)) {
                        WaypointDisplay display = WaypointDisplay.parse(value);
                        if (display != null && display != WaypointDisplay.EVERYWHERE) {
                            DISPLAYS.put(key.substring(DISPLAY_PREFIX.length()), display);
                        }
                    } else if (key.startsWith(SYMBOL_PREFIX)) {
                        String symbol = WaypointText.cleanSymbol(value);
                        if (symbol != null) {
                            SYMBOLS.put(key.substring(SYMBOL_PREFIX.length()), symbol);
                        }
                    }
                }
            }
            loaded = true;
        }
    }

    public static int version() {
        return version;
    }

    private static void save() {
        version++;
        Properties properties = new Properties();
        DISPLAYS.forEach((id, display) -> properties.setProperty(DISPLAY_PREFIX + id, display.key()));
        SYMBOLS.forEach((id, symbol) -> properties.setProperty(SYMBOL_PREFIX + id, symbol));
        AtomicPropertiesWriter.save(CONFIG_PATH, BACKUP_PATH, TEMP_PATH, properties, "VoxyMap waypoint display settings");
    }

    private static Properties read(Path path) {
        if (!Files.exists(path)) return null;
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException | IllegalArgumentException exception) {
            VoxyMapClient.LOGGER.warn("Could not read waypoint display settings from {}", path, exception);
            return null;
        }
    }
}
