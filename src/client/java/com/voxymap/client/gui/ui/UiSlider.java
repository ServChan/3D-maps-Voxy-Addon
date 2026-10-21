package com.voxymap.client.gui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public class UiSlider extends AbstractSliderButton {
    private static final int KNOB_WIDTH = 8;
    private static final Identifier TRACK = Identifier.withDefaultNamespace("widget/slider");
    private static final Identifier TRACK_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/slider_highlighted");
    private static final Identifier HANDLE = Identifier.withDefaultNamespace("widget/slider_handle");
    private static final Identifier HANDLE_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/slider_handle_highlighted");

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
        int w = getWidth();
        int h = getHeight();
        boolean hovered = active && isHoveredOrFocused();
        g.blitSprite(RenderPipelines.GUI_TEXTURED, hovered ? TRACK_HIGHLIGHTED : TRACK, x, y, w, h);
        int knobX = x + (int) Math.round((w - KNOB_WIDTH) * value);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, hovered ? HANDLE_HIGHLIGHTED : HANDLE, knobX, y, KNOB_WIDTH, h);
        String text = UiDraw.ellipsize(font, valueText.get().getString(), w - 8);
        g.text(font, text, x + w / 2 - font.width(text) / 2, UiDraw.textY(font, y, h),
                active ? UiTheme.TEXT : UiTheme.TEXT_DISABLED, true);
    }

    public void refresh(double fraction) {
        value = Math.max(0.0, Math.min(1.0, fraction));
        updateMessage();
    }
}
