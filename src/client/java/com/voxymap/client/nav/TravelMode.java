package com.voxymap.client.nav;

public enum TravelMode {
    WALK(0.20, "walk"),
    SWIM(0.50, "swim"),
    BOAT(0.125, "boat"),
    ELYTRA(1.0 / 30.0, "elytra");

    private final double secondsPerBlock;
    private final String key;

    TravelMode(double secondsPerBlock, String key) {
        this.secondsPerBlock = secondsPerBlock;
        this.key = key;
    }

    public double secondsPerBlock() {
        return secondsPerBlock;
    }

    public String key() {
        return key;
    }
}
