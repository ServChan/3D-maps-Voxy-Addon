package com.voxymap.syncserver;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Per-address fixed-window limiter for relay code claims. */
final class RelayClaimRateLimiter {
    private final int attemptsPerWindow;
    private final long windowNanos;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    RelayClaimRateLimiter(int attemptsPerWindow, long window, TimeUnit unit) {
        this.attemptsPerWindow = attemptsPerWindow;
        this.windowNanos = unit.toNanos(window);
    }

    boolean allow(String address) {
        long now = System.nanoTime();
        Window result = windows.compute(address, (ignored, current) -> {
            if (current == null || now - current.startedAtNanos >= windowNanos) {
                return new Window(now, 1);
            }
            return new Window(current.startedAtNanos, current.attempts + 1);
        });
        return result.attempts <= attemptsPerWindow;
    }

    void prune() {
        long now = System.nanoTime();
        windows.entrySet().removeIf(entry -> now - entry.getValue().startedAtNanos >= windowNanos);
    }

    private record Window(long startedAtNanos, int attempts) {
    }
}
