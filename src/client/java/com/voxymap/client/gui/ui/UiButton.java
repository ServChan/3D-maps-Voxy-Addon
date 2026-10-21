package com.voxymap.client.gui.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class UiButton extends UiControl {
    public enum Variant { PRIMARY, SECONDARY, GHOST, DANGER }

    public enum Align { CENTER, START }


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
    protected boolean drawsOwnFocus() {
        return variant != Variant.GHOST;
    }

    @Override
    protected void drawControl(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, double seconds) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        if (variant == Variant.GHOST) {
            if (selected) {
                g.fill(x, y, x + w, y + h, UiTheme.BORDER_STRONG);
                g.fill(x + 1, y + 1, x + w - 1, y + h - 1, UiTheme.CONTROL_SELECTED);
            } else if (active && hoverAmount > 0.0f) {
                g.fill(x, y, x + w, y + h, UiTheme.scaleAlpha(UiTheme.ROW_HOVER, hoverAmount));
            }
        } else {
            vanillaSprite(g, selected || isHoveredOrFocused());
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
            g.text(font, fitted, startX + iconWidth + gap, UiDraw.textY(font, y, h), textColor, true);
            if (selected && variant != Variant.GHOST) {
                int lineY = UiDraw.textY(font, y, h) + 9;
                int lineX = startX + iconWidth + gap;
                g.fill(lineX, lineY, lineX + font.width(fitted), lineY + 1, textColor);
            }
        }
    }

    private int textColor() {
        if (!active) {
            return UiTheme.TEXT_DISABLED;
        }
        if (variant == Variant.DANGER) {
            return UiTheme.DANGER;
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
