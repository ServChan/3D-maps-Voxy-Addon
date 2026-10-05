package com.voxymap.client.map;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

final class EnumFlags {
    private EnumFlags() {
    }

    static <E extends Enum<E>> EnumSet<E> parse(Class<E> type, String value, Set<E> fallback) {
        EnumSet<E> result = EnumSet.noneOf(type);
        if (value == null) {
            result.addAll(fallback);
            return result;
        }
        for (String part : value.split(",")) {
            String name = part.trim();
            if (name.isEmpty()) {
                continue;
            }
            try {
                result.add(Enum.valueOf(type, name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {

            }
        }
        return result;
    }

    static <E extends Enum<E>> String format(Set<E> flags) {
        StringJoiner joiner = new StringJoiner(",");
        for (E flag : flags) {
            joiner.add(flag.name().toLowerCase(Locale.ROOT));
        }
        return joiner.toString();
    }
}
