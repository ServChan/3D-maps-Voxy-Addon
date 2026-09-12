package com.voxymap.client.map;

/** Chooses the lower edge of the Nether roof/anti-xray shell removed from the map. */
public final class NetherRoofPolicy {
    static final float ROOF_MARGIN = 32.0f;

    private NetherRoofPolicy() {
    }

    /**
     * Remote servers can leave several stacked LOD section caps below the literal bedrock layer.
     * Removing complete quads in the upper 32 blocks clears those caps without fragment-slicing the
     * anti-xray volume or flattening the map at the player's current height.
     */
    public static float cutoff(int minY, int logicalHeight) {
        // A datapack could keep the "minecraft:the_nether" id while shrinking logicalHeight below the
        // roof margin. Without this floor the cut plane would fall below minY and remove the entire
        // dimension instead of just its ceiling.
        return Math.max(minY, minY + logicalHeight - ROOF_MARGIN);
    }
}
