package com.voxymap.shared;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WaypointJson {
    private static final int DEFAULT_COLOR = 0xFF62E7FF;
    private static final int MAX_NESTING_DEPTH = 32;

    private WaypointJson() {
    }

    public static String writeList(List<WaypointDto> waypoints) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"waypoints\":[");
        for (int i = 0; i < waypoints.size(); i++) {
            if (i > 0) sb.append(',');
            writeObject(sb, waypoints.get(i));
        }
        sb.append("]}");
        return sb.toString();
    }

    public static String writeOne(WaypointDto waypoint) {
        StringBuilder sb = new StringBuilder();
        writeObject(sb, waypoint);
        return sb.toString();
    }

    public static List<WaypointDto> readList(String json) {
        try {
            return readListStrict(json);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    public static List<WaypointDto> readListStrict(String json) {
        Object root = new Parser(json).parseValue();
        if (!(root instanceof Map<?, ?> map) || !(map.get("waypoints") instanceof List<?> list)) {
            throw new IllegalArgumentException("Expected an object with a waypoints array");
        }
        List<WaypointDto> result = new ArrayList<>();
        for (Object element : list) {
            WaypointDto waypoint = toWaypoint(element, null);
            if (waypoint != null) result.add(waypoint);
        }
        return result;
    }

    public static boolean wellFormedText(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(i + 1))) {
                    return false;
                }
                i++;
            } else if (Character.isLowSurrogate(c)) {
                return false;
            }
        }
        return true;
    }

    public static WaypointDto readOne(String json, String fallbackId) {
        try {
            return toWaypoint(new Parser(json).parseValue(), fallbackId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static WaypointDto toWaypoint(Object element, String fallbackId) {
        if (!(element instanceof Map<?, ?> map)) return null;
        String id = asString(map.get("id"));
        if (id == null || id.isBlank()) id = fallbackId;
        String name = asString(map.get("name"));
        Double x = asDouble(map.get("x"));
        Double y = asDouble(map.get("y"));
        Double z = asDouble(map.get("z"));
        if (id == null || id.isBlank() || name == null || name.isBlank() || x == null || y == null || z == null) {
            return null;
        }
        return new WaypointDto(id, name, x, y, z, parseColor(asString(map.get("color"))));
    }

    private static void writeObject(StringBuilder sb, WaypointDto waypoint) {
        sb.append('{');
        writeStringField(sb, "id", waypoint.id());
        sb.append(',');
        writeStringField(sb, "name", waypoint.name());
        sb.append(',');
        writeNumberField(sb, "x", waypoint.x());
        sb.append(',');
        writeNumberField(sb, "y", waypoint.y());
        sb.append(',');
        writeNumberField(sb, "z", waypoint.z());
        sb.append(',');
        writeStringField(sb, "color", Integer.toHexString(waypoint.color()));
        sb.append('}');
    }

    private static void writeStringField(StringBuilder sb, String key, String value) {
        sb.append('"').append(key).append("\":");
        writeString(sb, value);
    }

    private static void writeNumberField(StringBuilder sb, String key, double value) {
        sb.append('"').append(key).append("\":").append(formatNumber(value));
    }

    private static String formatNumber(double value) {
        if (Double.isFinite(value) && value == Math.rint(value) && Math.abs(value) < 1.0e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static void writeString(StringBuilder sb, String value) {
        sb.append('"');
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
        }
        sb.append('"');
    }

    private static int parseColor(String value) {
        if (value != null) {
            try {
                return (int) Long.parseLong(value, 16);
            } catch (NumberFormatException ignored) {

            }
        }
        return DEFAULT_COLOR;
    }

    private static String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number n) {
            double d = n.doubleValue();
            return Double.isFinite(d) ? d : null;
        }
        return null;
    }

    private static final class Parser {
        private final String text;
        private int pos;
        private int depth;

        Parser(String text) {
            this.text = text;
        }

        Object parseValue() {
            char c = peek();
            if ((c == '{' || c == '[') && depth >= MAX_NESTING_DEPTH) {
                throw new IllegalArgumentException("JSON nested too deeply at " + pos);
            }
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            depth++;
            Map<String, Object> result = new LinkedHashMap<>();
            if (peek() == '}') {
                pos++;
                depth--;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                expect(':');
                result.put(key, parseValue());
                char next = peek();
                pos++;
                if (next == '}') break;
                if (next != ',') throw new IllegalArgumentException("Expected ',' or '}' at " + pos);
            }
            depth--;
            return result;
        }

        private List<Object> parseArray() {
            expect('[');
            depth++;
            List<Object> result = new ArrayList<>();
            if (peek() == ']') {
                pos++;
                depth--;
                return result;
            }
            while (true) {
                result.add(parseValue());
                char next = peek();
                pos++;
                if (next == ']') break;
                if (next != ',') throw new IllegalArgumentException("Expected ',' or ']' at " + pos);
            }
            depth--;
            return result;
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= text.length()) throw new IllegalArgumentException("Unterminated string");
                char c = text.charAt(pos++);
                if (c == '"') break;
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (pos >= text.length()) throw new IllegalArgumentException("Unterminated escape");
                char escape = text.charAt(pos++);
                switch (escape) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (pos + 4 > text.length()) throw new IllegalArgumentException("Bad unicode escape");
                        sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw new IllegalArgumentException("Unknown escape \\" + escape);
                }
            }
            return sb.toString();
        }

        private Double parseNumber() {
            int start = pos;
            if (pos < text.length() && text.charAt(pos) == '-') pos++;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
            if (pos < text.length() && text.charAt(pos) == '.') {
                pos++;
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
            }
            if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                pos++;
                if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) pos++;
                while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
            }
            if (pos == start) throw new IllegalArgumentException("Invalid number at " + pos);
            return Double.parseDouble(text.substring(start, pos));
        }

        private Object parseLiteral(String literal, Object value) {
            if (pos + literal.length() > text.length() || !text.regionMatches(pos, literal, 0, literal.length())) {
                throw new IllegalArgumentException("Invalid literal at " + pos);
            }
            pos += literal.length();
            return value;
        }

        private void expect(char c) {
            skipWhitespace();
            if (pos >= text.length() || text.charAt(pos) != c) {
                throw new IllegalArgumentException("Expected '" + c + "' at " + pos);
            }
            pos++;
        }

        private char peek() {
            skipWhitespace();
            if (pos >= text.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            return text.charAt(pos);
        }

        private void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
        }
    }
}
