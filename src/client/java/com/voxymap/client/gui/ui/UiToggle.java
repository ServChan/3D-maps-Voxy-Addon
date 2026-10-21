package com.voxymap.client.gui.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

public class UiToggle extends UiControl {

    private final Component label;
    private final BooleanSupplier value;
    private final Runnable flip;

    public UiToggle(int x, int y, int width, int height, Component label, BooleanSupplier value, Runnable flip) {
        super(x, y, width, height, label);
        this.label = label;
        this.value = value;
        this.flip = flip;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        flip.run();
    }

    @Override
    public Component getMessage() {
        return Component.empty().append(label).append(": ").append(stateText(value.getAsBoolean()));
    }

    static Component stateText(boolean on) {
        return Component.translatable(on ? "screen.voxymap.settings.on" : "screen.voxymap.settings.off");
    }

    @Override
    protected boolean drawsOwnFocus() {
        return true;
    }

    @Override
    protected void drawControl(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, double seconds) {
        boolean on = value.getAsBoolean();
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        vanillaSprite(g, isHoveredOrFocused());
        int textColor = !active ? UiTheme.TEXT_DISABLED : UiTheme.TEXT;
        UiDraw.centeredText(g, font, stateText(on).getString(), x + w / 2, UiDraw.textY(font, y, h), w - 8, textColor);
    }

}
