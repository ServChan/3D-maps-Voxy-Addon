package com.voxymap.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.voxymap.client.map.MinimapPlacement;
import com.voxymap.client.map.MinimapRenderer;
import com.voxymap.client.map.VoxyMapSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class MinimapLayoutScreen extends Screen {
    private static final int ACCENT = 0xFF7DEBFF;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;
    private boolean dragging;
    private double grabOffsetX;
    private double grabOffsetY;

    public MinimapLayoutScreen(Screen parent) {
        super(Component.translatable("screen.voxymap.layout.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int y = height / 2 + 20;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), pressed -> onClose())
                .bounds(centerX - BUTTON_WIDTH - 4, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.voxymap.layout.reset"), pressed ->
                        VoxyMapSettings.resetMinimapPosition())
                .bounds(centerX + 4, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private int mapSize() {
        return MinimapRenderer.lastDisplaySize();
    }

    private MinimapPlacement.Placement placement() {
        MinimapPlacement.Placement rendered = MinimapRenderer.lastPlacement();
        if (rendered != null && VoxyMapSettings.minimapEnabled()) {
            return rendered;
        }
        return MinimapPlacement.place(width, height, mapSize(), 0, VoxyMapSettings.minimapCorner(),
                VoxyMapSettings.minimapX(), VoxyMapSettings.minimapY(), 0);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, width, height, 0x40000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        MinimapPlacement.Placement placement = placement();
        int size = mapSize();
        int x = placement.x() - 3;
        int y = placement.y() - 3;
        int outer = size + 6;
        boolean hot = dragging || inside(mouseX, mouseY, placement, size);
        graphics.outline(x, y, outer, outer, hot ? 0xFFFFFFFF : ACCENT);
        graphics.centeredText(font, title, width / 2, height / 2 - 26, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("screen.voxymap.layout.hint"), width / 2, height / 2 - 12,
                0xFFB8C7D9);
        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
    }

    private static boolean inside(double mouseX, double mouseY, MinimapPlacement.Placement placement, int size) {
        return mouseX >= placement.x() && mouseX < placement.x() + size
                && mouseY >= placement.y() && mouseY < placement.y() + size;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        MinimapPlacement.Placement placement = placement();
        if (inside(event.x(), event.y(), placement, mapSize())) {
            dragging = true;
            grabOffsetX = event.x() - placement.x();
            grabOffsetY = event.y() - placement.y();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (dragging) {
            moveTo(event.x() - grabOffsetX, event.y() - grabOffsetY, false);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            MinimapPlacement.Placement placement = placement();
            moveTo(placement.x(), placement.y(), true);
            return true;
        }
        return super.mouseReleased(event);
    }

    private void moveTo(double x, double y, boolean persist) {
        int size = mapSize();
        VoxyMapSettings.setMinimapPosition(MinimapPlacement.fraction((int) Math.round(x), width, size),
                MinimapPlacement.fraction((int) Math.round(y), height, size), persist);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        int step = event.hasShiftDown() ? 10 : 1;
        int dx = key == InputConstants.KEY_LEFT ? -step : key == InputConstants.KEY_RIGHT ? step : 0;
        int dy = key == InputConstants.KEY_UP ? -step : key == InputConstants.KEY_DOWN ? step : 0;
        if (dx != 0 || dy != 0) {
            MinimapPlacement.Placement placement = placement();
            moveTo(placement.x() + dx, placement.y() + dy, true);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreenAndShow(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
