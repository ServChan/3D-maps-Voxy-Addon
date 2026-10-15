package com.voxymap.client.gui.ui;

public final class UiTheme {
    public static final int SCRIM = 0xA0030812;
    public static final int SURFACE = 0xE60A1422;
    public static final int SURFACE_RAISED = 0xF2101E30;
    public static final int SURFACE_SUNKEN = 0xC0060D18;
    public static final int CONTROL = 0xCC132538;
    public static final int CONTROL_HOVER = 0xE01A3350;
    public static final int CONTROL_PRESSED = 0xF0224266;
    public static final int CONTROL_SELECTED = 0xF0174A7A;
    public static final int CONTROL_DISABLED = 0x800E1A28;
    public static final int ROW_HOVER = 0x4A2A5A8A;
    public static final int BORDER = 0x5A3BA4FF;
    public static final int BORDER_STRONG = 0xFF3BA4FF;
    public static final int ACCENT = 0xFF3BA4FF;
    public static final int ACCENT_BRIGHT = 0xFF7DEBFF;
    public static final int FOCUS = 0xFFFFFFFF;
    public static final int TEXT = 0xFFE9F2FF;
    public static final int TEXT_SECONDARY = 0xFFB8C9DB;
    public static final int TEXT_MUTED = 0xFF8A9BB0;
    public static final int TEXT_DISABLED = 0xFF5E6B7A;
    public static final int TEXT_ON_ACCENT = 0xFF04121F;
    public static final int SUCCESS = 0xFF5FD88A;
    public static final int WARNING = 0xFFFFC94D;
    public static final int DANGER = 0xFFFF6B5B;
    public static final int DANGER_FILL = 0xE0A3302D;
    public static final int DANGER_FILL_HOVER = 0xF0C23A36;

    public static final int SPACE_XS = 2;
    public static final int SPACE_S = 4;
    public static final int SPACE_M = 8;
    public static final int SPACE_L = 12;
    public static final int SPACE_XL = 16;
    public static final int CONTROL_HEIGHT = 20;
    public static final int ROW_HEIGHT = 24;
    public static final int BUTTON_HEIGHT = 24;
    public static final int ICON_SIZE = 9;

    private UiTheme() {
    }

    public static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
    }

    public static int scaleAlpha(int argb, float factor) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0f, Math.min(1.0f, factor)));
        return withAlpha(argb, alpha);
    }

    public static int mix(int from, int to, float t) {
        float amount = Math.max(0.0f, Math.min(1.0f, t));
        int a = channel(from, 24, to, amount);
        int r = channel(from, 16, to, amount);
        int g = channel(from, 8, to, amount);
        int b = channel(from, 0, to, amount);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int channel(int from, int shift, int to, float amount) {
        int start = (from >>> shift) & 0xFF;
        int end = (to >>> shift) & 0xFF;
        return Math.round(start + (end - start) * amount);
    }

    public static double luminance(int argb) {
        return 0.2126 * linear((argb >>> 16) & 0xFF) + 0.7152 * linear((argb >>> 8) & 0xFF)
                + 0.0722 * linear(argb & 0xFF);
    }

    public static double contrast(int foreground, int background) {
        double a = luminance(foreground);
        double b = luminance(background);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
