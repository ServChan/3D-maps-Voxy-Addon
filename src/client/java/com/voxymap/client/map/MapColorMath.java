package com.voxymap.client.map;

final class MapColorMath {
    static final int MIN_SAMPLE_ALPHA = 26;
    static final double SLOPE_STEP = 0.07;
    static final double MIN_SLOPE_FACTOR = 0.62;
    static final double MAX_SLOPE_FACTOR = 1.28;
    static final int MAX_WATER_DEPTH = 24;

    private MapColorMath() {
    }

    static int averageArgb(int[] argbPixels) {
        long red = 0;
        long green = 0;
        long blue = 0;
        long weight = 0;
        for (int pixel : argbPixels) {
            int alpha = (pixel >>> 24) & 0xFF;
            if (alpha < MIN_SAMPLE_ALPHA) {
                continue;
            }
            red += (long) ((pixel >>> 16) & 0xFF) * alpha;
            green += (long) ((pixel >>> 8) & 0xFF) * alpha;
            blue += (long) (pixel & 0xFF) * alpha;
            weight += alpha;
        }
        if (weight == 0) {
            return 0;
        }
        return 0xFF000000 | (int) (red / weight) << 16 | (int) (green / weight) << 8 | (int) (blue / weight);
    }

    static int multiply(int argb, int tintRgb) {
        int red = ((argb >>> 16) & 0xFF) * ((tintRgb >>> 16) & 0xFF) / 255;
        int green = ((argb >>> 8) & 0xFF) * ((tintRgb >>> 8) & 0xFF) / 255;
        int blue = (argb & 0xFF) * (tintRgb & 0xFF) / 255;
        return (argb & 0xFF000000) | red << 16 | green << 8 | blue;
    }

    static int scale(int argb, double factor) {
        int red = clampChannel(((argb >>> 16) & 0xFF) * factor);
        int green = clampChannel(((argb >>> 8) & 0xFF) * factor);
        int blue = clampChannel((argb & 0xFF) * factor);
        return (argb & 0xFF000000) | red << 16 | green << 8 | blue;
    }

    static int lerp(int fromArgb, int toArgb, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        int red = clampChannel(((fromArgb >>> 16) & 0xFF) + (((toArgb >>> 16) & 0xFF) - ((fromArgb >>> 16) & 0xFF)) * clamped);
        int green = clampChannel(((fromArgb >>> 8) & 0xFF) + (((toArgb >>> 8) & 0xFF) - ((fromArgb >>> 8) & 0xFF)) * clamped);
        int blue = clampChannel((fromArgb & 0xFF) + ((toArgb & 0xFF) - (fromArgb & 0xFF)) * clamped);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    static double slopeFactor(int height, int northHeight, int westHeight) {
        int rise = (height - northHeight) + (height - westHeight);
        double factor = 1.0 + rise * SLOPE_STEP;
        return Math.max(MIN_SLOPE_FACTOR, Math.min(MAX_SLOPE_FACTOR, factor));
    }

    static double waterOpacity(int depth) {
        return Math.min(0.92, 0.5 + depth * 0.055);
    }

    static int hsv(float hue, float saturation, float value) {
        float h = (hue - (float) Math.floor(hue)) * 6.0f;
        int sector = (int) h;
        float fraction = h - sector;
        float p = value * (1.0f - saturation);
        float q = value * (1.0f - saturation * fraction);
        float t = value * (1.0f - saturation * (1.0f - fraction));
        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            default -> { r = value; g = p; b = q; }
        }
        return 0xFF000000 | clampChannel(r * 255.0) << 16 | clampChannel(g * 255.0) << 8 | clampChannel(b * 255.0);
    }

    private static int clampChannel(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }
}
