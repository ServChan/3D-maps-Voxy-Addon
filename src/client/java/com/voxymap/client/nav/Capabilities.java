package com.voxymap.client.nav;

public record Capabilities(boolean boat, boolean elytra, boolean elytraWorn, int rockets) {
    public static final Capabilities ON_FOOT = new Capabilities(false, false, false, 0);

    public boolean canFly() {
        return elytra && rockets > 0;
    }
}
