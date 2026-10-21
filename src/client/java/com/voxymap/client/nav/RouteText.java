package com.voxymap.client.nav;

import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class RouteText {
    private RouteText() {
    }

    public static Component distance(double blocks) {
        if (blocks >= 1000.0) {
            return Component.translatable("unit.voxymap.kilometers", String.format(Locale.ROOT, "%.1f", blocks / 1000.0));
        }
        return Component.translatable("unit.voxymap.meters", Long.toString(roundedMeters(blocks)));
    }

    static long roundedMeters(double blocks) {
        if (blocks < 100.0) {
            return Math.max(0L, Math.round(blocks));
        }
        return Math.round(blocks / 10.0) * 10L;
    }

    public static Component duration(double seconds) {
        long minutes = Math.round(seconds / 60.0);
        if (minutes < 1L) {
            return Component.translatable("unit.voxymap.under_minute");
        }
        return Component.translatable("unit.voxymap.minutes", Long.toString(minutes));
    }

    public static Component mode(TravelMode mode) {
        return Component.translatable("hud.voxymap.route.mode." + mode.key());
    }
}
