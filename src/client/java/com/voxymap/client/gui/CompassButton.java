package com.voxymap.client.gui;

import com.voxymap.client.gui.ui.UiControl;
import com.voxymap.client.gui.ui.UiDraw;
import com.voxymap.client.gui.ui.UiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleSupplier;

final class CompassButton extends UiControl {
    private final DoubleSupplier northAngle;
    private final Runnable alignNorth;

    CompassButton(int size, DoubleSupplier northAngle, Runnable alignNorth) {
        super(0, 0, size, size, Component.translatable("overlay.voxymap.compass"));
        this.northAngle = northAngle;
        this.alignNorth = alignNorth;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        alignNorth.run();
    }

    @Override
    protected void drawControl(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, double seconds) {
        int x = getX();
        int y = getY();
        int size = getWidth();
        UiDraw.panel(g, x, y, size, size, UiTheme.mix(UiTheme.SURFACE, UiTheme.CONTROL_HOVER, hoverAmount));
        UiDraw.outline(g, x, y, size, size, UiTheme.mix(UiTheme.BORDER, UiTheme.BORDER_STRONG, hoverAmount));
        double centerX = x + size / 2.0;
        double centerY = y + size / 2.0;
        double angle = northAngle.getAsDouble();
        double dirX = Math.sin(angle);
        double dirY = -Math.cos(angle);
        double radius = size / 2.0 - 11.0;
        for (int step = 0; step <= radius; step++) {
            int px = (int) Math.round(centerX + dirX * step);
            int py = (int) Math.round(centerY + dirY * step);
            g.fill(px - 1, py - 1, px + 1, py + 1, UiTheme.DANGER);
            int tx = (int) Math.round(centerX - dirX * step * 0.6);
            int ty = (int) Math.round(centerY - dirY * step * 0.6);
            g.fill(tx, ty, tx + 1, ty + 1, UiTheme.TEXT_MUTED);
        }
        double letterDistance = size / 2.0 - 7.0;
        int letterX = (int) Math.round(centerX + dirX * letterDistance);
        int letterY = (int) Math.round(centerY + dirY * letterDistance);
        g.text(font, "N", letterX - font.width("N") / 2, letterY - 4, UiTheme.TEXT);
    }
}
