package com.voxymap.client.map;

public final class PlayerViewConeGeometry {
    private PlayerViewConeGeometry() {
    }

    public static double directionX(double yawDegrees) {
        return -Math.sin(Math.toRadians(yawDegrees));
    }

    public static double directionZ(double yawDegrees) {
        return Math.cos(Math.toRadians(yawDegrees));
    }
}
