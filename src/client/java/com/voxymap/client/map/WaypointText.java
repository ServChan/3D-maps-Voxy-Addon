package com.voxymap.client.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WaypointText {
    public static final int MAX_SHARED_PER_MESSAGE = 3;
    static final int MAX_NAME_LENGTH = 64;
    static final int[] CHAT_COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };
    private static final String COLON_ESCAPE = "^col^";
    private static final Pattern XAERO = Pattern.compile(
            "xaero-waypoint:([^:]+):([^:]*):(-?\\d+):(~|-?\\d+):(-?\\d+):(\\d+):(?:true|false):(-?\\d+)(?::([^\\s]+))?");

    public enum Dimension {
        OVERWORLD("overworld", 1.0),
        NETHER("nether", 8.0),
        END("end", 1.0);

        private final String id;
        private final double scale;

        Dimension(String id, double scale) {
            this.id = id;
            this.scale = scale;
        }

        public String id() {
            return id;
        }

        public static Dimension fromId(String id) {
            for (Dimension dimension : values()) {
                if (dimension.id.equals(id)) {
                    return dimension;
                }
            }
            return null;
        }

        public static Dimension fromLevelKey(String levelKey) {
            return switch (levelKey) {
                case "minecraft:overworld" -> OVERWORLD;
                case "minecraft:the_nether" -> NETHER;
                case "minecraft:the_end" -> END;
                default -> null;
            };
        }

        static Dimension fromXaeroContainer(String container) {
            if (container == null) {
                return OVERWORLD;
            }
            String lower = container.toLowerCase(Locale.ROOT);
            if (lower.contains("nether") || lower.contains("%-1")) {
                return NETHER;
            }
            if (lower.contains("the_end") || lower.contains("the-end") || lower.contains("%1_")) {
                return END;
            }
            return OVERWORLD;
        }

        String xaeroContainer() {
            return switch (this) {
                case OVERWORLD -> "Internal-overworld-waypoints";
                case NETHER -> "Internal-the_nether-waypoints";
                case END -> "Internal-the_end-waypoints";
            };
        }
    }

    public record SharedWaypoint(String name, int x, Integer y, int z, int colorIndex, Dimension dimension) {
    }

    public record Converted(int x, int z) {
    }

    private WaypointText() {
    }

    public static String initial(String name) {
        if (name != null) {
            for (int i = 0; i < name.length(); ) {
                int codePoint = name.codePointAt(i);
                if (Character.isLetterOrDigit(codePoint)) {
                    return new String(Character.toChars(Character.toUpperCase(codePoint)));
                }
                i += Character.charCount(codePoint);
            }
        }
        return "?";
    }

    public static int nearestChatColor(int argb) {
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        int best = 15;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < CHAT_COLORS.length; i++) {
            int candidate = CHAT_COLORS[i];
            long dr = red - ((candidate >>> 16) & 0xFF);
            long dg = green - ((candidate >>> 8) & 0xFF);
            long db = blue - (candidate & 0xFF);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    public static int chatColorArgb(int index) {
        return 0xFF000000 | CHAT_COLORS[Math.floorMod(index, CHAT_COLORS.length)];
    }

    public static String toXaero(String name, double x, double y, double z, int argb, Dimension dimension) {
        String safeName = cleanName(name).replace(":", COLON_ESCAPE);
        return "xaero-waypoint:" + safeName + ":" + initial(name) + ":" + (int) Math.floor(x) + ":"
                + (int) Math.floor(y) + ":" + (int) Math.floor(z) + ":" + nearestChatColor(argb) + ":false:0:"
                + dimension.xaeroContainer();
    }

    public static List<SharedWaypoint> parseXaero(String text) {
        List<SharedWaypoint> found = new ArrayList<>();
        if (text == null || !text.contains("xaero-waypoint:")) {
            return found;
        }
        Matcher matcher = XAERO.matcher(text);
        while (matcher.find() && found.size() < MAX_SHARED_PER_MESSAGE) {
            try {
                String name = cleanName(matcher.group(1).replace(COLON_ESCAPE, ":"));
                int x = Integer.parseInt(matcher.group(3));
                Integer y = "~".equals(matcher.group(4)) ? null : Integer.parseInt(matcher.group(4));
                int z = Integer.parseInt(matcher.group(5));
                int color = Math.floorMod(Integer.parseInt(matcher.group(6)), CHAT_COLORS.length);
                if (name.isEmpty()) {
                    continue;
                }
                found.add(new SharedWaypoint(name, x, y, z, color, Dimension.fromXaeroContainer(matcher.group(8))));
            } catch (NumberFormatException ignored) {

            }
        }
        return found;
    }

    public static double scaleFactor(Dimension from, Dimension to) {
        if (from == null || to == null || from == Dimension.END || to == Dimension.END) {
            return from == to && from != null ? 1.0 : Double.NaN;
        }
        return from.scale / to.scale;
    }

    public static Converted convert(int x, int z, Dimension from, Dimension to) {
        if (from == to) {
            return new Converted(x, z);
        }
        if (from == Dimension.END || to == Dimension.END || from == null || to == null) {
            return null;
        }
        double factor = from.scale / to.scale;
        return new Converted((int) Math.floor(x * factor), (int) Math.floor(z * factor));
    }

    public static boolean useKilometres(double blocks) {
        return blocks >= 999.5;
    }

    public static String kilometres(double blocks) {
        return String.format(Locale.ROOT, "%.1f", blocks / 1000.0);
    }

    public static String cleanSymbol(String symbol) {
        String cleaned = cleanName(symbol).replace(" ", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        int end = cleaned.offsetByCodePoints(0, Math.min(2, cleaned.codePointCount(0, cleaned.length())));
        return cleaned.substring(0, end).toUpperCase(Locale.ROOT);
    }

    public static Integer parseCoordinate(String text) {
        if (text == null) {
            return null;
        }
        try {
            long value = Long.parseLong(text.trim());
            return value < -30_000_000L || value > 30_000_000L ? null : (int) value;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static String cleanName(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        boolean skipCode = false;
        for (int i = 0; i < name.length(); ) {
            int codePoint = name.codePointAt(i);
            i += Character.charCount(codePoint);
            if (skipCode) {
                skipCode = false;
            } else if (codePoint == '§') {
                skipCode = true;
            } else if (!Character.isISOControl(codePoint)) {
                builder.appendCodePoint(codePoint);
            }
        }
        String cleaned = builder.toString().trim();
        return cleaned.length() > MAX_NAME_LENGTH ? cleaned.substring(0, MAX_NAME_LENGTH).trim() : cleaned;
    }
}
