package com.voxymap.client.gui.ui;

public final class UiTheme {
    public static final int SCRIM = 0xA0000000;
    public static final int SURFACE = 0xC8101010;
    public static final int SURFACE_RAISED = 0xE0101010;
    public static final int SURFACE_SUNKEN = 0x70000000;
    public static final int CONTROL = 0x55000000;
    public static final int CONTROL_HOVER = 0x30FFFFFF;
    public static final int CONTROL_PRESSED = 0x50FFFFFF;
    public static final int CONTROL_SELECTED = 0xFF000000;
    public static final int CONTROL_DISABLED = 0x40000000;
    public static final int ROW_HOVER = 0x22FFFFFF;
    public static final int BORDER = 0x40FFFFFF;
    public static final int BORDER_STRONG = 0xFF808080;
    public static final int ACCENT = 0xFFFFFFFF;
    public static final int ACCENT_BRIGHT = 0xFFFFFFFF;
    public static final int FOCUS = 0xFFFFFFFF;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_SECONDARY = 0xFFC6C6C6;
    public static final int TEXT_MUTED = 0xFFA0A0A0;
    public static final int TEXT_DISABLED = 0xFF707070;
    public static final int TEXT_ON_ACCENT = 0xFF000000;
    public static final int SUCCESS = 0xFF55FF55;
    public static final int WARNING = 0xFFFFAA00;
    public static final int DANGER = 0xFFFF5555;
    public static final int DANGER_FILL = 0xE03A1010;
    public static final int DANGER_FILL_HOVER = 0xF0551818;
    public static final int LABEL = 0xC0000000;
    public static final int LABEL_HOVER = 0xE0000000;
    public static final int SEPARATOR_DARK = 0xBF000000;
    public static final int SEPARATOR_LIGHT = 0x33FFFFFF;
    public static final int ROUTE_WALK = 0xFFFFD34D;
    public static final int ROUTE_SWIM = 0xFF7FE6E0;
    public static final int ROUTE_BOAT = 0xFF4DB8FF;
    public static final int ROUTE_ELYTRA = 0xFFC58BFF;
    public static final int ROUTE_UNKNOWN = 0xFF9AA4B2;
    public static final int ROUTE_CASING = 0xC0081018;

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
