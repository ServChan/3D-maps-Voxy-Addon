package com.voxymap.client.gui.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.Supplier;

public class UiCycler extends UiControl {
    private final Component label;
    private final Supplier<Component> value;
    private final IntConsumer step;
    private UiIcons.Icon icon;

    public UiCycler(int x, int y, int width, int height, Component label, Supplier<Component> value, IntConsumer step) {
        super(x, y, width, height, label);
        this.label = label;
        this.value = value;
        this.step = step;
    }

    public UiCycler icon(UiIcons.Icon value) {
        icon = value;
        return this;
    }

    @Override
    public Component getMessage() {
        return Component.empty().append(label).append(": ").append(value.get());
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (input instanceof MouseButtonEvent mouse && mouse.x() < getX() + getWidth() / 3.0) {
            step.accept(-1);
        } else {
            step.accept(1);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (active && event.isLeft()) {
            step.accept(-1);
            return true;
        }
        if (active && event.isRight()) {
            step.accept(1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void drawControl(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, double seconds) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        UiDraw.panel(g, x, y, w, h, fill(UiTheme.CONTROL, UiTheme.CONTROL_HOVER, UiTheme.CONTROL_PRESSED));
        UiDraw.outline(g, x, y, w, h, UiTheme.mix(UiTheme.BORDER, UiTheme.BORDER_STRONG, hoverAmount));
        int arrowColor = active ? UiTheme.mix(UiTheme.TEXT_MUTED, UiTheme.ACCENT_BRIGHT, hoverAmount) : UiTheme.TEXT_DISABLED;
        int iconY = y + (h - UiTheme.ICON_SIZE) / 2;
        UiIcons.draw(g, UiIcons.Icon.CHEVRON_LEFT, x + 2, iconY, arrowColor);
        UiIcons.draw(g, UiIcons.Icon.CHEVRON_RIGHT, x + w - UiTheme.ICON_SIZE - 2, iconY, arrowColor);
        int textColor = active ? UiTheme.TEXT : UiTheme.TEXT_DISABLED;
        int inner = w - (UiTheme.ICON_SIZE + 4) * 2;
        String text = value.get().getString();
        if (icon != null) {
            String fitted = UiDraw.ellipsize(font, text, inner - UiTheme.ICON_SIZE - UiTheme.SPACE_S);
            int contentWidth = UiTheme.ICON_SIZE + UiTheme.SPACE_S + font.width(fitted);
            int startX = x + (w - contentWidth) / 2;
            UiIcons.draw(g, icon, startX, iconY, UiTheme.TEXT_SECONDARY);
            g.text(font, fitted, startX + UiTheme.ICON_SIZE + UiTheme.SPACE_S, UiDraw.textY(font, y, h), textColor);
            return;
        }
        UiDraw.centeredText(g, font, text, x + w / 2, UiDraw.textY(font, y, h), inner, textColor);
    }
}
