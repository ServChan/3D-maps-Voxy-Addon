package com.voxymap.client.gui;

final class ClusterLayout {
    private ClusterLayout() {
    }

    static int clampOrigin(int desiredOrigin, int size, int screenSize, int margin) {
        int max = Math.max(margin, screenSize - size - margin);
        return Math.max(margin, Math.min(desiredOrigin, max));
    }
}
