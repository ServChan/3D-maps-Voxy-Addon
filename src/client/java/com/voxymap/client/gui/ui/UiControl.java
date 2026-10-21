package com.voxymap.client.gui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public abstract class UiControl extends AbstractButton {
    private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
    private static final Identifier BUTTON_DISABLED = Identifier.withDefaultNamespace("widget/button_disabled");
    private static final Identifier BUTTON_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/button_highlighted");
    private final UiMotion.Clock clock = new UiMotion.Clock();
    protected float hoverAmount;

    protected UiControl(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Override
    protected final void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        double seconds = clock.tick();
        hoverAmount = UiMotion.approach(hoverAmount, active && isHoveredOrFocused() ? 1.0f : 0.0f, seconds, 0.12);
        drawControl(g, font(), mouseX, mouseY, seconds);
        if (active && isFocused() && showsFocusRing() && !drawsOwnFocus()) {
            UiDraw.focusRing(g, getX(), getY(), getWidth(), getHeight());
        }
    }

    protected abstract void drawControl(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, double seconds);

    protected boolean drawsOwnFocus() {
        return false;
    }

    protected boolean showsFocusRing() {
        return Minecraft.getInstance().getLastInputType().isKeyboard();
    }

    protected boolean pressed() {
        return active && isHovered() && Minecraft.getInstance().mouseHandler.isLeftPressed();
    }

    protected int fill(int normal, int hovered, int pressedColor) {
        if (!active) {
            return UiTheme.CONTROL_DISABLED;
        }
        if (pressed()) {
            return pressedColor;
        }
        return UiTheme.mix(normal, hovered, hoverAmount);
    }

    protected void vanillaSprite(GuiGraphicsExtractor g, boolean highlighted) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, !active ? BUTTON_DISABLED : highlighted ? BUTTON_HIGHLIGHTED : BUTTON,
                getX(), getY(), getWidth(), getHeight());
    }

    protected static Font font() {
        return Minecraft.getInstance().font;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
