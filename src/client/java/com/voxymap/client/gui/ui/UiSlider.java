package com.voxymap.client.gui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public class UiSlider extends AbstractSliderButton {
    private static final int KNOB_WIDTH = 6;
    private static final int VALUE_WIDTH = 40;

    private final Component label;
    private final Supplier<Component> valueText;
    private final DoubleConsumer apply;
    private double keyStep = 0.05;

    public UiSlider(int x, int y, int width, int height, Component label, DoubleSupplier fraction,
                    Supplier<Component> valueText, DoubleConsumer apply) {
        super(x, y, width, height, label, Math.max(0.0, Math.min(1.0, fraction.getAsDouble())));
        this.label = label;
        this.valueText = valueText;
        this.apply = apply;
        updateMessage();
    }

    public UiSlider keyStep(double step) {
        keyStep = step > 0.0 ? step : keyStep;
        return this;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (active && (event.isLeft() || event.isRight())) {
            double next = Math.max(0.0, Math.min(1.0, value + (event.isLeft() ? -keyStep : keyStep)));
            if (next != value) {
                value = next;
                applyValue();
                updateMessage();
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.empty().append(label).append(": ").append(valueText.get()));
    }

    @Override
    protected void applyValue() {
        apply.accept(value);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int h = getHeight();
        int trackWidth = Math.max(16, getWidth() - VALUE_WIDTH - UiTheme.SPACE_S);
        int trackY = y + h / 2 - 1;
        boolean hovered = active && isHoveredOrFocused();
        UiDraw.panel(g, x, trackY - 1, trackWidth, 4, UiTheme.SURFACE_SUNKEN);
        int filled = (int) Math.round((trackWidth - KNOB_WIDTH) * value);
        g.fill(x + 1, trackY, x + filled + KNOB_WIDTH / 2, trackY + 2, active ? UiTheme.ACCENT : UiTheme.TEXT_DISABLED);
        int knobX = x + filled;
        UiDraw.panel(g, knobX, y + 3, KNOB_WIDTH, h - 6, !active ? UiTheme.TEXT_DISABLED
                : hovered ? UiTheme.ACCENT_BRIGHT : UiTheme.TEXT);
        if (active && isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard()) {
            UiDraw.focusRing(g, x, y, getWidth(), h);
        }
        g.text(font, valueText.get().getString(), x + trackWidth + UiTheme.SPACE_S + 2, UiDraw.textY(font, y, h),
                active ? UiTheme.TEXT : UiTheme.TEXT_DISABLED);
    }

    public void refresh(double fraction) {
        value = Math.max(0.0, Math.min(1.0, fraction));
        updateMessage();
    }
}
