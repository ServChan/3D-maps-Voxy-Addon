package com.voxymap.client.gui;

/**
 * Pure screen-space clamping for player-cluster labels/panels: keeps a box anchored near a point
 * (marker position) fully inside the viewport instead of letting it run off the edge.
 */
final class ClusterLayout {
    private ClusterLayout() {
    }

    /** Clamps a box's origin along one axis so [origin, origin + size) stays within [margin, screenSize - margin]. */
    static int clampOrigin(int desiredOrigin, int size, int screenSize, int margin) {
        int max = Math.max(margin, screenSize - size - margin);
        return Math.max(margin, Math.min(desiredOrigin, max));
    }
}
