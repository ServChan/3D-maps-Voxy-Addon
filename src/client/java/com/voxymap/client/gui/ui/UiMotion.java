package com.voxymap.client.gui.ui;

import com.voxymap.client.map.VoxyMapSettings;

public final class UiMotion {
    private static final double FALLBACK_FRAME_SECONDS = 1.0 / 60.0;
    private static final double MAX_FRAME_SECONDS = 0.1;

    private UiMotion() {
    }

    public static boolean enabled() {
        return VoxyMapSettings.uiAnimations();
    }

    public static float approach(float current, float target, double seconds, double durationSeconds) {
        if (!enabled() || durationSeconds <= 0.0) {
            return target;
        }
        double step = Math.max(0.0, Math.min(MAX_FRAME_SECONDS, seconds));
        double response = 1.0 - Math.exp(-step * 4.0 / durationSeconds);
        float next = (float) (current + (target - current) * response);
        return Math.abs(target - next) < 0.002f ? target : next;
    }

    public static final class Clock {
        private long lastNanos;

        public double tick() {
            long now = System.nanoTime();
            double seconds = lastNanos == 0L ? FALLBACK_FRAME_SECONDS : (now - lastNanos) / 1_000_000_000.0;
            lastNanos = now;
            return seconds;
        }
    }
}
