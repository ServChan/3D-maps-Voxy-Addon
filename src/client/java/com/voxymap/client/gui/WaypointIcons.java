package com.voxymap.client.gui;

import com.voxymap.client.map.VoxyMapSettings;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class WaypointIcons {
    private static final int OUTLINE = 0xEE0A0A12;
    private static final int DARK_TEXT = 0xFF0A0A12;
    private static final int LIGHT_TEXT = 0xFFFFFFFF;
    public static final int OTHER_DIMENSION_ALPHA = 0x99;

    private WaypointIcons() {
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int size, int color, String initial) {
        draw(graphics, font, x, y, size, color, initial, 0xFF);
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int size, int color, String initial,
                            int alpha) {
        VoxyMapSettings.WaypointIconShape shape = VoxyMapSettings.waypointIconShape();
        fillShape(graphics, shape, x - 1, y - 1, size + 2, withAlpha(OUTLINE, alpha));
        fillShape(graphics, shape, x, y, size, withAlpha(0xFF000000 | color, alpha));
        int textWidth = font.width(initial);
        int textX = x + (size - textWidth) / 2 + 1;
        int textY = y + (size - 8) / 2 + 1;
        graphics.text(font, initial, textX, textY, withAlpha(textColorFor(color), alpha), false);
    }

    public static void drawCentered(GuiGraphicsExtractor graphics, Font font, int centerX, int centerY, int size,
                                    int color, String initial) {
        drawCentered(graphics, font, centerX, centerY, size, color, initial, 0xFF);
    }

    public static void drawCentered(GuiGraphicsExtractor graphics, Font font, int centerX, int centerY, int size,
                                    int color, String initial, int alpha) {
        draw(graphics, font, centerX - size / 2, centerY - size / 2, size, color, initial, alpha);
    }

    private static void fillShape(GuiGraphicsExtractor graphics, VoxyMapSettings.WaypointIconShape shape,
                                  int x, int y, int size, int argb) {
        if (shape == VoxyMapSettings.WaypointIconShape.SQUARE) {
            graphics.fill(x, y, x + size, y + size, argb);
            return;
        }
        for (int row = 0; row < size; row++) {
            int[] span = rowSpan(shape, size, row);
            graphics.fill(x + span[0], y + row, x + span[1], y + row + 1, argb);
        }
    }

    static int[] rowSpan(VoxyMapSettings.WaypointIconShape shape, int size, int row) {
        double center = size / 2.0;
        double offset = Math.abs(row + 0.5 - center);
        double half = switch (shape) {
            case SQUARE -> center;
            case CIRCLE -> Math.sqrt(Math.max(0.0, center * center - offset * offset));
            case DIAMOND -> center - offset + 0.5;
        };
        int left = Math.max(0, Math.min((size - 1) / 2, (int) Math.round(center - half)));
        return new int[]{left, size - left};
    }

    static int withAlpha(int argb, int alpha) {
        int scaled = (((argb >>> 24) & 0xFF) * alpha) / 0xFF;
        return (scaled << 24) | (argb & 0x00FFFFFF);
    }

    static int textColorFor(int argb) {
        double red = ((argb >>> 16) & 0xFF) / 255.0;
        double green = ((argb >>> 8) & 0xFF) / 255.0;
        double blue = (argb & 0xFF) / 255.0;
        double luminance = 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue);
        return luminance > 0.18 ? DARK_TEXT : LIGHT_TEXT;
    }

    private static double linear(double channel) {
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }
}
