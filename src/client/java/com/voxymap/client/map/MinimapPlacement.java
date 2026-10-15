package com.voxymap.client.map;

public final class MinimapPlacement {
    public static final int MARGIN = 8;
    static final double MIN_NIGHT_BRIGHTNESS = 0.55;
    static final int MAX_SKY_DARKEN = 11;

    public record Placement(int x, int y, boolean infoAbove, boolean anchorRight) {
    }

    private MinimapPlacement() {
    }

    public static Placement place(int guiWidth, int guiHeight, int size, int infoHeight,
                                  VoxyMapSettings.MinimapCorner corner, double fractionX, double fractionY, int topOffset) {
        int x;
        int y;
        switch (corner) {
            case TOP_RIGHT -> {
                x = guiWidth - MARGIN - size;
                y = MARGIN + topOffset;
            }
            case BOTTOM_RIGHT -> {
                x = guiWidth - MARGIN - size;
                y = guiHeight - MARGIN - size - infoHeight;
            }
            case CUSTOM -> {
                x = MARGIN + (int) Math.round(clamp01(fractionX) * freeSpace(guiWidth, size));
                y = MARGIN + (int) Math.round(clamp01(fractionY) * freeSpace(guiHeight, size));
            }
            default -> {
                x = MARGIN;
                y = MARGIN;
            }
        }
        boolean infoAbove = infoHeight > 0 && y + size + infoHeight > guiHeight - 2 && y - infoHeight >= 0;
        boolean anchorRight = x + size / 2 > guiWidth / 2;
        return new Placement(x, y, infoAbove, anchorRight);
    }

    public static int freeSpace(int guiExtent, int size) {
        return Math.max(0, guiExtent - 2 * MARGIN - size);
    }

    public static double fraction(int position, int guiExtent, int size) {
        int free = freeSpace(guiExtent, size);
        return free == 0 ? 0.0 : clamp01((position - MARGIN) / (double) free);
    }

    public static final int SNAP_DISTANCE = 6;

    public record Snap(int position, int guide) {
        public static final int NONE = Integer.MIN_VALUE;

        public boolean snapped() {
            return guide != NONE;
        }
    }

    public static Snap snap(int position, int guiExtent, int size) {
        int start = MARGIN;
        int end = guiExtent - MARGIN - size;
        int center = (guiExtent - size) / 2;
        int best = position;
        int guide = Snap.NONE;
        int bestDistance = SNAP_DISTANCE + 1;
        int[][] targets = {{start, start}, {end, end + size}, {center, guiExtent / 2}};
        for (int[] target : targets) {
            int distance = Math.abs(position - target[0]);
            if (distance <= SNAP_DISTANCE && distance < bestDistance) {
                best = target[0];
                guide = target[1];
                bestDistance = distance;
            }
        }
        return new Snap(best, guide);
    }

    public static double nightBrightness(int skyDarken) {
        double darkness = Math.max(0, Math.min(MAX_SKY_DARKEN, skyDarken)) / (double) MAX_SKY_DARKEN;
        return 1.0 - darkness * (1.0 - MIN_NIGHT_BRIGHTNESS);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
