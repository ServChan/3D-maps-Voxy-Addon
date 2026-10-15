package com.voxymap.syncserver;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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

    boolean tryReserve(String address) {
        long now = System.nanoTime();
        boolean[] reserved = new boolean[1];
        windows.compute(address, (ignored, current) -> {
            if (current == null || now - current.startedAtNanos >= windowNanos) {
                reserved[0] = true;
                return new Window(now, 1);
            }
            if (current.attempts >= attemptsPerWindow) {
                return current;
            }
            reserved[0] = true;
            return new Window(current.startedAtNanos, current.attempts + 1);
        });
        return reserved[0];
    }

    void release(String address) {
        windows.computeIfPresent(address, (ignored, current) ->
                current.attempts <= 1 ? null : new Window(current.startedAtNanos, current.attempts - 1));
    }

    void prune() {
        long now = System.nanoTime();
        windows.entrySet().removeIf(entry -> now - entry.getValue().startedAtNanos >= windowNanos);
    }

    private record Window(long startedAtNanos, int attempts) {
    }
}
