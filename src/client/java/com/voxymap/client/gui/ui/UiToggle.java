package com.voxymap.client.gui.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

public class UiToggle extends UiControl {
    static final int TRACK_WIDTH = 22;
    static final int TRACK_HEIGHT = 12;

    private final Component label;
    private final BooleanSupplier value;
    private final Runnable flip;
    private float knob;
    private boolean knobReady;

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
    protected void drawControl(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, double seconds) {
        boolean on = value.getAsBoolean();
        float target = on ? 1.0f : 0.0f;
        if (!knobReady) {
            knob = target;
            knobReady = true;
        }
        knob = UiMotion.approach(knob, target, seconds, 0.14);
        int x = getX();
        int y = getY();
        int h = getHeight();
        if (hoverAmount > 0.0f && active) {
            UiDraw.panel(g, x, y, getWidth(), h, UiTheme.scaleAlpha(UiTheme.ROW_HOVER, hoverAmount));
        }
        int trackX = x + UiTheme.SPACE_S;
        int trackY = y + (h - TRACK_HEIGHT) / 2;
        int trackFill = active ? UiTheme.mix(UiTheme.SURFACE_SUNKEN, 0xFF2B6FB8, knob) : UiTheme.CONTROL_DISABLED;
        UiDraw.panel(g, trackX, trackY, TRACK_WIDTH, TRACK_HEIGHT, trackFill);
        UiDraw.outline(g, trackX, trackY, TRACK_WIDTH, TRACK_HEIGHT,
                active ? UiTheme.mix(UiTheme.BORDER_STRONG, UiTheme.ACCENT_BRIGHT, knob) : UiTheme.BORDER);
        int knobSize = TRACK_HEIGHT - 4;
        int knobX = trackX + 2 + Math.round((TRACK_WIDTH - 4 - knobSize) * knob);
        g.fill(knobX, trackY + 2, knobX + knobSize, trackY + 2 + knobSize,
                active ? (on ? UiTheme.TEXT : UiTheme.TEXT_MUTED) : UiTheme.TEXT_DISABLED);
        int textColor = !active ? UiTheme.TEXT_DISABLED : on ? UiTheme.TEXT : UiTheme.TEXT_SECONDARY;
        g.text(font, stateText(on).getString(), trackX + TRACK_WIDTH + UiTheme.SPACE_M, UiDraw.textY(font, y, h),
                textColor);
    }
}
