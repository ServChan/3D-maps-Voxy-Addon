package com.voxymap.client.gui.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class UiDraw {
    private static final String ELLIPSIS = "…";
    private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
    private static final Identifier BUTTON_DISABLED = Identifier.withDefaultNamespace("widget/button_disabled");
    private static final Identifier BUTTON_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/button_highlighted");
    private static final Identifier FIELD = Identifier.withDefaultNamespace("widget/text_field");
    private static final Identifier FIELD_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/text_field_highlighted");

    private UiDraw() {
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int width, int height, int fill) {
        if (width <= 0 || height <= 0) {
            return;
        }
        g.fill(x, y, x + width, y + height, fill);
    }

    public static void outline(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        g.outline(x, y, width, height, color);
    }

    public static void card(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        panel(g, x, y, width, height, UiTheme.LABEL);
    }

    public static void button(GuiGraphicsExtractor g, int x, int y, int width, int height, boolean highlighted,
                              boolean enabled) {
        if (width <= 0 || height <= 0) {
            return;
        }
        g.blitSprite(RenderPipelines.GUI_TEXTURED, !enabled ? BUTTON_DISABLED : highlighted ? BUTTON_HIGHLIGHTED : BUTTON,
                x, y, width, height);
    }

    public static void field(GuiGraphicsExtractor g, int x, int y, int width, int height, boolean focused) {
        if (width <= 0 || height <= 0) {
            return;
        }
        g.blitSprite(RenderPipelines.GUI_TEXTURED, focused ? FIELD_HIGHLIGHTED : FIELD, x, y, width, height);
    }

    public static void focusRing(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        outline(g, x - 1, y - 1, width + 2, height + 2, UiTheme.FOCUS);
    }

    public static void divider(GuiGraphicsExtractor g, int x, int y, int width) {
        g.fill(x, y, x + width, y + 1, UiTheme.SEPARATOR_DARK);
        g.fill(x, y + 1, x + width, y + 2, UiTheme.SEPARATOR_LIGHT);
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
        panel(g, x, y, keyWidth, 14, 0xFF000000);
        outline(g, x, y, keyWidth, 14, UiTheme.BORDER_STRONG);
        g.text(font, key, x + 4, y + 3, UiTheme.TEXT);
        g.text(font, action, x + keyWidth + 4, y + 3, UiTheme.TEXT_SECONDARY);
    }

    public static int keyCapWidth(Font font, String key, String action) {
        return font.width(key) + 8 + 4 + font.width(action);
    }
}
