package com.voxymap.client.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.voxymap.client.VoxyMapClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public final class WaypointImport {
    private static final int MAX_DEPTH = 5;
    private static final long MAX_FILE_BYTES = 4L * 1024 * 1024;

    public enum Source { XAERO, JOURNEYMAP }

    public record Candidate(String name, String symbol, int x, Integer y, int z, int color, boolean disabled) {
    }

    public record Found(Source source, String world, String set, WaypointText.Dimension dimension, List<Candidate> waypoints) {
    }

    public record Result(int imported, int skipped) {
    }

    private WaypointImport() {
    }

    public static CompletableFuture<List<Found>> scan(Path gameDirectory) {
        return CompletableFuture.supplyAsync(() -> {
            List<Found> found = new ArrayList<>();
            scanXaero(gameDirectory.resolve("xaero").resolve("minimap"), found);
            scanXaero(gameDirectory.resolve("XaeroWaypoints"), found);
            scanJourneyMap(gameDirectory.resolve("journeymap").resolve("data"), found);
            found.removeIf(entry -> entry.waypoints().isEmpty());
            return found;
        });
    }

    public static Result importInto(String worldId, List<Candidate> candidates, double defaultY) {
        List<WaypointManager.Waypoint> existing = WaypointManager.forWorld(worldId);
        int imported = 0;
        int skipped = 0;
        for (Candidate candidate : candidates) {
            double y = candidate.y() == null ? defaultY : candidate.y();
            if (alreadyThere(existing, candidate)) {
                skipped++;
                continue;
            }
            WaypointManager.Waypoint added = WaypointManager.add(worldId, candidate.name(), candidate.x() + 0.5, y,
                    candidate.z() + 0.5, candidate.color(), false);
            if (added == null) {
                skipped++;
                continue;
            }
            if (candidate.symbol() != null && !candidate.symbol().equals(WaypointText.initial(candidate.name()))) {
                WaypointPrefs.setSymbol(added.id(), candidate.symbol());
            }
            if (candidate.disabled()) {
                WaypointPrefs.setDisplay(added.id(), WaypointDisplay.HIDDEN);
            }
            imported++;
        }
        return new Result(imported, skipped);
    }

    static boolean alreadyThere(List<WaypointManager.Waypoint> existing, Candidate candidate) {
        for (WaypointManager.Waypoint waypoint : existing) {
            if (waypoint.name().equals(candidate.name()) && (int) Math.floor(waypoint.x()) == candidate.x()
                    && (int) Math.floor(waypoint.z()) == candidate.z()) {
                return true;
            }
        }
        return false;
    }

    private static void scanXaero(Path root, List<Found> found) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> files = Files.walk(root, MAX_DEPTH)) {
            for (Path file : files.filter(WaypointImport::isXaeroFile).sorted().toList()) {
                Path dimensionFolder = file.getParent();
                Path worldFolder = dimensionFolder == null ? null : dimensionFolder.getParent();
                if (worldFolder == null) {
                    continue;
                }
                List<Candidate> waypoints = parseXaero(readLines(file));
                found.add(new Found(Source.XAERO, worldFolder.getFileName().toString(), xaeroSetName(file),
                        xaeroDimension(dimensionFolder.getFileName().toString()), waypoints));
            }
        } catch (IOException | RuntimeException exception) {
            VoxyMapClient.LOGGER.warn("Could not scan Xaero waypoints in {}", root, exception);
        }
    }

    private static boolean isXaeroFile(Path file) {
        String name = file.getFileName().toString();
        return name.startsWith("mw") && name.endsWith(".txt") && Files.isRegularFile(file) && small(file);
    }

    private static void scanJourneyMap(Path root, List<Found> found) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> files = Files.walk(root, MAX_DEPTH)) {
            Map<String, Map<WaypointText.Dimension, List<Candidate>>> byWorld = new LinkedHashMap<>();
            for (Path file : files.filter(WaypointImport::isJourneyMapFile).sorted().toList()) {
                Path waypointsFolder = file.getParent();
                Path worldFolder = waypointsFolder == null ? null : waypointsFolder.getParent();
                if (worldFolder == null) {
                    continue;
                }
                Map<WaypointText.Dimension, List<Candidate>> byDimension = byWorld.computeIfAbsent(
                        worldFolder.getFileName().toString(), key -> new LinkedHashMap<>());
                try {
                    parseJourneyMap(Files.readString(file, StandardCharsets.UTF_8), byDimension);
                } catch (IOException | RuntimeException exception) {
                    VoxyMapClient.LOGGER.debug("Skipped JourneyMap file {}", file, exception);
                }
            }
            byWorld.forEach((world, byDimension) -> byDimension.forEach((dimension, waypoints) ->
                    found.add(new Found(Source.JOURNEYMAP, world, "", dimension, List.copyOf(waypoints)))));
        } catch (IOException | RuntimeException exception) {
            VoxyMapClient.LOGGER.warn("Could not scan JourneyMap waypoints in {}", root, exception);
        }
    }

    private static boolean isJourneyMapFile(Path file) {
        Path parent = file.getParent();
        return file.getFileName().toString().endsWith(".json") && parent != null
                && parent.getFileName().toString().equalsIgnoreCase("waypoints") && Files.isRegularFile(file) && small(file);
    }

    private static boolean small(Path file) {
        try {
            return Files.size(file) <= MAX_FILE_BYTES;
        } catch (IOException exception) {
            return false;
        }
    }

    private static List<String> readLines(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    static String xaeroSetName(Path file) {
        String name = file.getFileName().toString();
        String stem = name.substring(0, name.length() - ".txt".length());
        int dollar = stem.indexOf('$');
        int underscore = stem.lastIndexOf('_');
        if (dollar < 0) {
            return "";
        }
        String set = underscore > dollar ? stem.substring(dollar + 1, underscore) : stem.substring(dollar + 1);
        return "default".equals(set) ? "" : set;
    }

    static WaypointText.Dimension xaeroDimension(String folder) {
        String lower = folder.toLowerCase(Locale.ROOT);
        if (lower.contains("nether") || lower.endsWith("%-1")) {
            return WaypointText.Dimension.NETHER;
        }
        if (lower.contains("the_end") || lower.contains("end") || lower.endsWith("%1")) {
            return WaypointText.Dimension.END;
        }
        return WaypointText.Dimension.OVERWORLD;
    }

    static List<Candidate> parseXaero(List<String> lines) {
        List<Candidate> waypoints = new ArrayList<>();
        for (String line : lines) {
            if (!line.startsWith("waypoint:")) {
                continue;
            }
            String[] parts = line.split(":", -1);
            if (parts.length < 9) {
                continue;
            }
            try {
                String name = WaypointText.cleanName(parts[1].replace("^col^", ":").replace("§§", ":"));
                if (name.isEmpty() || "1".equals(parts[8])) {
                    continue;
                }
                int x = Integer.parseInt(parts[3]);
                Integer y = "~".equals(parts[4]) ? null : Integer.parseInt(parts[4]);
                int z = Integer.parseInt(parts[5]);
                int color = WaypointText.chatColorArgb(Integer.parseInt(parts[6]));
                boolean disabled = "true".equals(parts[7]);
                waypoints.add(new Candidate(name, WaypointText.cleanSymbol(parts[2]), x, y, z, color, disabled));
            } catch (NumberFormatException ignored) {

            }
        }
        return waypoints;
    }

    static void parseJourneyMap(String json, Map<WaypointText.Dimension, List<Candidate>> into) {
        JsonElement root = JsonParser.parseString(json);
        List<JsonObject> objects = new ArrayList<>();
        collectWaypointObjects(root, objects, 0);
        for (JsonObject object : objects) {
            Candidate candidate = journeyMapCandidate(object);
            if (candidate == null) {
                continue;
            }
            for (WaypointText.Dimension dimension : journeyMapDimensions(object)) {
                into.computeIfAbsent(dimension, key -> new ArrayList<>()).add(candidate);
            }
        }
    }

    private static void collectWaypointObjects(JsonElement element, List<JsonObject> into, int depth) {
        if (depth > 4 || element == null) {
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectWaypointObjects(child, into, depth + 1);
            }
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("x") && object.has("z") && object.has("name")) {
                into.add(object);
                return;
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                collectWaypointObjects(entry.getValue(), into, depth + 1);
            }
        }
    }

    private static Candidate journeyMapCandidate(JsonObject object) {
        try {
            String name = WaypointText.cleanName(object.get("name").getAsString());
            if (name.isEmpty() || "Death".equalsIgnoreCase(stringOrNull(object, "type"))) {
                return null;
            }
            int x = (int) Math.floor(object.get("x").getAsDouble());
            Integer y = object.has("y") ? (int) Math.floor(object.get("y").getAsDouble()) : null;
            int z = (int) Math.floor(object.get("z").getAsDouble());
            int color;
            if (object.has("color")) {
                color = 0xFF000000 | object.get("color").getAsInt();
            } else if (object.has("r") && object.has("g") && object.has("b")) {
                color = 0xFF000000 | (object.get("r").getAsInt() & 0xFF) << 16 | (object.get("g").getAsInt() & 0xFF) << 8
                        | (object.get("b").getAsInt() & 0xFF);
            } else {
                color = WaypointCodec.PALETTE[0];
            }
            boolean disabled = object.has("enable") && !object.get("enable").getAsBoolean();
            return new Candidate(name, null, x, y, z, color, disabled);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static List<WaypointText.Dimension> journeyMapDimensions(JsonObject object) {
        List<WaypointText.Dimension> dimensions = new ArrayList<>();
        JsonElement value = object.has("dimensions") ? object.get("dimensions")
                : object.has("dimension") ? object.get("dimension") : null;
        if (value == null) {
            dimensions.add(WaypointText.Dimension.OVERWORLD);
            return dimensions;
        }
        JsonArray array = new JsonArray();
        if (value.isJsonArray()) {
            array = value.getAsJsonArray();
        } else {
            array.add(value);
        }
        for (JsonElement element : array) {
            WaypointText.Dimension dimension = journeyMapDimension(element);
            if (dimension != null && !dimensions.contains(dimension)) {
                dimensions.add(dimension);
            }
        }
        if (dimensions.isEmpty()) {
            dimensions.add(WaypointText.Dimension.OVERWORLD);
        }
        return dimensions;
    }

    private static WaypointText.Dimension journeyMapDimension(JsonElement element) {
        if (!element.isJsonPrimitive()) {
            return null;
        }
        String text = element.getAsString().toLowerCase(Locale.ROOT);
        return switch (text) {
            case "0", "minecraft:overworld", "overworld" -> WaypointText.Dimension.OVERWORLD;
            case "-1", "minecraft:the_nether", "the_nether", "nether" -> WaypointText.Dimension.NETHER;
            case "1", "minecraft:the_end", "the_end", "end" -> WaypointText.Dimension.END;
            default -> null;
        };
    }

    private static String stringOrNull(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    public static List<Found> rank(List<Found> found, String serverAddress, WaypointText.Dimension here) {
        String needle = serverAddress == null ? null : serverAddress.toLowerCase(Locale.ROOT).replace(':', '_');
        List<Found> sorted = new ArrayList<>(found);
        sorted.sort(Comparator.<Found>comparingInt(entry -> entry.dimension() == here ? 0 : 1)
                .thenComparingInt(entry -> needle != null && entry.world().toLowerCase(Locale.ROOT).replace(':', '_')
                        .contains(needle) ? 0 : 1)
                .thenComparing(Found::world, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }
}
