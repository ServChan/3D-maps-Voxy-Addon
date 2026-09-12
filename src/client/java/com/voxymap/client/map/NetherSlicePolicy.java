package com.voxymap.client.map;

/** Chooses a useful horizontal Nether map slice without depending on loaded vanilla chunks. */
public final class NetherSlicePolicy {
    static final float PLAYER_HEADROOM = 8.0f;
    static final float ROOF_MARGIN = 8.0f;

    private NetherSlicePolicy() {
    }

    /**
     * Keeps a little terrain above the player while ensuring the bedrock roof is always removed.
     * The shader applies this plane to all cached Voxy geometry, not merely the local room mask.
     */
    public static float cutoff(int minY, int logicalHeight, double playerY) {
        float worldBottom = minY;
        float belowBedrockRoof = minY + logicalHeight - ROOF_MARGIN;
        float playerSlice = (float) Math.floor(playerY) + PLAYER_HEADROOM;
        return Math.max(worldBottom, Math.min(belowBedrockRoof, playerSlice));
    }
}
