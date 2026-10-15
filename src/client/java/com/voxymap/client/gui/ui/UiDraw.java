package com.voxymap.client.gui.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class UiDraw {
    private static final String ELLIPSIS = "…";

    private UiDraw() {
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int width, int height, int fill) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (width < 5 || height < 5) {
            g.fill(x, y, x + width, y + height, fill);
            return;
        }
        g.fill(x + 2, y, x + width - 2, y + 1, fill);
        g.fill(x + 1, y + 1, x + width - 1, y + 2, fill);
        g.fill(x, y + 2, x + width, y + height - 2, fill);
        g.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, fill);
        g.fill(x + 2, y + height - 1, x + width - 2, y + height, fill);
    }

    public static void outline(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
        if (width < 5 || height < 5) {
            g.outline(x, y, width, height, color);
            return;
        }
        g.fill(x + 2, y, x + width - 2, y + 1, color);
        g.fill(x + 2, y + height - 1, x + width - 2, y + height, color);
        g.fill(x, y + 2, x + 1, y + height - 2, color);
        g.fill(x + width - 1, y + 2, x + width, y + height - 2, color);
        g.fill(x + 1, y + 1, x + 2, y + 2, color);
        g.fill(x + width - 2, y + 1, x + width - 1, y + 2, color);
        g.fill(x + 1, y + height - 2, x + 2, y + height - 1, color);
        g.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, color);
    }

    public static void card(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        panel(g, x, y, width, height, UiTheme.SURFACE);
        outline(g, x, y, width, height, UiTheme.BORDER);
    }

    public static void focusRing(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        outline(g, x - 2, y - 2, width + 4, height + 4, UiTheme.FOCUS);
    }

    public static void divider(GuiGraphicsExtractor g, int x, int y, int width) {
        g.fill(x, y, x + width, y + 1, UiTheme.BORDER);
    }

    public static String ellipsize(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = font.width(ELLIPSIS);
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end)) + ellipsisWidth > maxWidth) {
            end--;
        }
        return end == 0 ? "" : text.substring(0, end) + ELLIPSIS;
    }

    public static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int maxWidth, int color) {
        g.text(font, ellipsize(font, text, maxWidth), x, y, color);
    }

    public static void centeredText(GuiGraphicsExtractor g, Font font, String text, int centerX, int y, int maxWidth,
                                    int color) {
        String fitted = ellipsize(font, text, maxWidth);
        g.text(font, fitted, centerX - font.width(fitted) / 2, y, color);
    }

    public static int textY(Font font, int y, int height) {
        return y + (height - font.lineHeight) / 2 + 1;
    }

    public static void keyCap(GuiGraphicsExtractor g, Font font, String key, String action, int x, int y) {
        int keyWidth = font.width(key) + 8;
        panel(g, x, y, keyWidth, 14, UiTheme.CONTROL_SELECTED);
        g.text(font, key, x + 4, y + 3, UiTheme.TEXT);
        g.text(font, action, x + keyWidth + 4, y + 3, UiTheme.TEXT_SECONDARY);
    }

    public static int keyCapWidth(Font font, String key, String action) {
        return font.width(key) + 8 + 4 + font.width(action);
    }
}
