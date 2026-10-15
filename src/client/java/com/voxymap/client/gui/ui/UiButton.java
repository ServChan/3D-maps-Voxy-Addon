package com.voxymap.client.gui.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class UiButton extends UiControl {
    public enum Variant { PRIMARY, SECONDARY, GHOST, DANGER }

    public enum Align { CENTER, START }

    private static final int PRIMARY_FILL = 0xFF2B6FB8;
    private static final int PRIMARY_HOVER = 0xFF3586DA;
    private static final int PRIMARY_PRESSED = 0xFF235C99;

    private final Variant variant;
    private final Consumer<UiButton> action;
    private UiIcons.Icon icon;
    private boolean showLabel = true;
    private boolean selected;
    private boolean loading;
    private Align align = Align.CENTER;
    private java.util.function.Supplier<Component> dynamicLabel;

    public UiButton(int x, int y, int width, int height, Component label, Variant variant, Consumer<UiButton> action) {
        super(x, y, width, height, label);
        this.variant = variant;
        this.action = action;
    }

    public static UiButton iconButton(int x, int y, int size, UiIcons.Icon icon, Component accessibleName,
                                      Consumer<UiButton> action) {
        UiButton button = new UiButton(x, y, size, size, accessibleName, Variant.SECONDARY, action);
        button.icon = icon;
        button.showLabel = false;
        return button;
    }

    public UiButton icon(UiIcons.Icon value) {
        icon = value;
        return this;
    }

    public UiButton label(java.util.function.Supplier<Component> value) {
        dynamicLabel = value;
        return this;
    }

    @Override
    public Component getMessage() {
        return dynamicLabel != null ? dynamicLabel.get() : super.getMessage();
    }

    public UiButton align(Align value) {
        align = value;
        return this;
    }

    public UiButton selected(boolean value) {
        selected = value;
        return this;
    }

    public boolean selected() {
        return selected;
    }

    public void setLoading(boolean value) {
        loading = value;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (!loading) {
            action.accept(this);
        }
    }

    @Override
    protected void drawControl(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, double seconds) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        int background = background();
        if ((background >>> 24) != 0) {
            UiDraw.panel(g, x, y, w, h, background);
        }
        int border = border();
        if ((border >>> 24) != 0) {
            UiDraw.outline(g, x, y, w, h, border);
        }
        int textColor = textColor();
        String label = showLabel ? getMessage().getString() : "";
        int iconWidth = icon == null ? 0 : UiTheme.ICON_SIZE;
        int gap = icon != null && !label.isEmpty() ? UiTheme.SPACE_S + 1 : 0;
        int available = w - UiTheme.SPACE_M * 2 - iconWidth - gap;
        String fitted = UiDraw.ellipsize(font, label, available);
        int contentWidth = iconWidth + gap + font.width(fitted);
        int startX = align == Align.CENTER ? x + (w - contentWidth) / 2 : x + UiTheme.SPACE_M;
        int iconY = y + (h - UiTheme.ICON_SIZE) / 2;
        if (loading) {
            drawSpinner(g, x + w / 2, y + h / 2, textColor);
            return;
        }
        if (icon != null) {
            UiIcons.draw(g, icon, startX, iconY, textColor);
        }
        if (!fitted.isEmpty()) {
            g.text(font, fitted, startX + iconWidth + gap, UiDraw.textY(font, y, h), textColor);
        }
    }

    private int background() {
        if (!active) {
            return variant == Variant.GHOST ? 0 : UiTheme.CONTROL_DISABLED;
        }
        if (selected) {
            return fill(UiTheme.CONTROL_SELECTED, UiTheme.CONTROL_SELECTED, UiTheme.CONTROL_PRESSED);
        }
        return switch (variant) {
            case PRIMARY -> fill(PRIMARY_FILL, PRIMARY_HOVER, PRIMARY_PRESSED);
            case SECONDARY -> fill(UiTheme.CONTROL, UiTheme.CONTROL_HOVER, UiTheme.CONTROL_PRESSED);
            case GHOST -> fill(0x00000000, UiTheme.ROW_HOVER, UiTheme.CONTROL_PRESSED);
            case DANGER -> fill(UiTheme.DANGER_FILL, UiTheme.DANGER_FILL_HOVER, UiTheme.DANGER_FILL);
        };
    }

    private int border() {
        if (!active) {
            return 0;
        }
        if (selected) {
            return UiTheme.ACCENT_BRIGHT;
        }
        return switch (variant) {
            case SECONDARY -> UiTheme.mix(UiTheme.BORDER, UiTheme.BORDER_STRONG, hoverAmount);
            case GHOST -> 0;
            case PRIMARY, DANGER -> UiTheme.withAlpha(0xFFFFFF, Math.round(40 * hoverAmount));
        };
    }

    private int textColor() {
        if (!active) {
            return UiTheme.TEXT_DISABLED;
        }
        if (variant == Variant.GHOST && !selected) {
            return UiTheme.mix(UiTheme.TEXT_SECONDARY, UiTheme.TEXT, hoverAmount);
        }
        return UiTheme.TEXT;
    }

    private static void drawSpinner(GuiGraphicsExtractor g, int centerX, int centerY, int color) {
        int phase = (int) ((System.nanoTime() / 150_000_000L) % 3L);
        for (int i = 0; i < 3; i++) {
            int dotColor = i == phase ? color : UiTheme.scaleAlpha(color, 0.35f);
            int dotX = centerX - 5 + i * 4;
            g.fill(dotX, centerY - 1, dotX + 2, centerY + 1, dotColor);
        }
    }
}
