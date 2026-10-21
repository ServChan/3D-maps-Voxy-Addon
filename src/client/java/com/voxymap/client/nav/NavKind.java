package com.voxymap.client.nav;

public final class NavKind {
    public static final byte UNKNOWN = 0;
    public static final byte LAND = 1;
    public static final byte FOREST = 2;
    public static final byte ROAD = 3;
    public static final byte WATER = 4;
    public static final byte ICE = 5;
    public static final byte HAZARD = 6;
    public static final byte BLOCKED = 7;

    private NavKind() {
    }

    public static boolean floats(byte kind) {
        return kind == WATER || kind == ICE;
    }
}
