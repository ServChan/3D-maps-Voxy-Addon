package com.voxymap.client.map;

/** Chooses a global cut plane that removes only the Nether's top roof layer. */
public final class NetherRoofPolicy {
    static final float ROOF_MARGIN = 8.0f;

    private NetherRoofPolicy() {
    }

    /**
     * Voxy stores exposed surfaces, so discarding the top roof geometry is enough to see through the
     * solid ceiling volume. Keeping the plane near the logical ceiling preserves tall builds below it.
     */
    public static float cutoff(int minY, int logicalHeight) {
        return minY + logicalHeight - ROOF_MARGIN;
    }
}
