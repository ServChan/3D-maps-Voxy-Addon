package com.voxymap.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.voxymap.client.gui.ui.UiButton;
import com.voxymap.client.gui.ui.UiDraw;
import com.voxymap.client.gui.ui.UiIcons;
import com.voxymap.client.gui.ui.UiTheme;
import com.voxymap.client.map.MinimapPlacement;
import com.voxymap.client.map.MinimapRenderer;
import com.voxymap.client.map.VoxyMapSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class MinimapLayoutScreen extends Screen {
    private static final int BUTTON_WIDTH = 104;
    private static final int CARD_WIDTH = 240;
    private static final int CARD_HEIGHT = 74;

    private final Screen parent;
    private boolean dragging;
    private double grabOffsetX;
    private double grabOffsetY;
    private int guideX = MinimapPlacement.Snap.NONE;
    private int guideY = MinimapPlacement.Snap.NONE;

    public MinimapLayoutScreen(Screen parent) {
        super(Component.translatable("screen.voxymap.layout.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cardY = (height - CARD_HEIGHT) / 2;
        int buttonY = cardY + CARD_HEIGHT - UiTheme.CONTROL_HEIGHT - UiTheme.SPACE_M;
        addRenderableWidget(new UiButton(width / 2 - BUTTON_WIDTH - UiTheme.SPACE_S / 2, buttonY, BUTTON_WIDTH,
                UiTheme.CONTROL_HEIGHT, Component.translatable("gui.done"), UiButton.Variant.PRIMARY, button -> onClose()));
        addRenderableWidget(new UiButton(width / 2 + UiTheme.SPACE_S / 2, buttonY, BUTTON_WIDTH, UiTheme.CONTROL_HEIGHT,
                Component.translatable("screen.voxymap.layout.reset"), UiButton.Variant.SECONDARY,
                button -> VoxyMapSettings.resetMinimapPosition()).icon(UiIcons.Icon.RESET));
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
        graphics.fill(0, 0, width, height, 0x50000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        MinimapPlacement.Placement placement = placement();
        int size = mapSize();
        if (dragging) {
            if (guideX != MinimapPlacement.Snap.NONE) {
                drawDashed(graphics, guideX, 0, guideX, height);
            }
            if (guideY != MinimapPlacement.Snap.NONE) {
                drawDashed(graphics, 0, guideY, width, guideY);
            }
        }
        boolean hot = dragging || inside(mouseX, mouseY, placement, size);
        UiDraw.outline(graphics, placement.x() - 4, placement.y() - 4, size + 8, size + 8,
                hot ? UiTheme.FOCUS : UiTheme.ACCENT_BRIGHT);
        String hint = Component.translatable("screen.voxymap.layout.hint").getString();
        String snap = Component.translatable("screen.voxymap.layout.snap").getString();
        int cardWidth = Math.max(CARD_WIDTH, Math.min(width - 32, Math.max(font.width(hint), font.width(snap)) + 24));
        int cardX = (width - cardWidth) / 2;
        int cardY = (height - CARD_HEIGHT) / 2;
        UiDraw.panel(graphics, cardX, cardY, cardWidth, CARD_HEIGHT, UiTheme.SURFACE_RAISED);
        UiDraw.outline(graphics, cardX, cardY, cardWidth, CARD_HEIGHT, UiTheme.BORDER_STRONG);
        UiDraw.centeredText(graphics, font, title.getString(), width / 2, cardY + 9, cardWidth - 16, UiTheme.TEXT);
        UiDraw.centeredText(graphics, font, hint, width / 2, cardY + 22, cardWidth - 16, UiTheme.TEXT_SECONDARY);
        UiDraw.centeredText(graphics, font, snap, width / 2, cardY + 33, cardWidth - 16, UiTheme.TEXT_MUTED);
        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
    }

    private static void drawDashed(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2) {
        boolean vertical = x1 == x2;
        int length = vertical ? y2 - y1 : x2 - x1;
        for (int step = 0; step < length; step += 8) {
            int end = Math.min(length, step + 4);
            if (vertical) {
                graphics.fill(x1, y1 + step, x1 + 1, y1 + end, UiTheme.ACCENT_BRIGHT);
            } else {
                graphics.fill(x1 + step, y1, x1 + end, y1 + 1, UiTheme.ACCENT_BRIGHT);
            }
        }
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
            int size = mapSize();
            int x = (int) Math.round(event.x() - grabOffsetX);
            int y = (int) Math.round(event.y() - grabOffsetY);
            guideX = MinimapPlacement.Snap.NONE;
            guideY = MinimapPlacement.Snap.NONE;
            if (!event.hasAltDown()) {
                MinimapPlacement.Snap snapX = MinimapPlacement.snap(x, width, size);
                MinimapPlacement.Snap snapY = MinimapPlacement.snap(y, height, size);
                x = snapX.position();
                y = snapY.position();
                guideX = snapX.guide();
                guideY = snapY.guide();
            }
            moveTo(x, y, false);
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
