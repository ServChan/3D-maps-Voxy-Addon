package com.voxymap.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.voxymap.client.gui.ui.UiDraw;
import com.voxymap.client.gui.ui.UiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

final class MapContextMenu {
    private static final int ROW_H = 18;
    private static final int HEADER_H = 16;
    private static final int PAD_X = 8;

    record Item(Component label, boolean enabled, Runnable action) {
    }

    private List<Item> items = List.of();
    private Component header = Component.empty();
    private boolean open;
    private int x;
    private int y;
    private int w;
    private int h;
    private int highlighted = -1;
    private boolean keyboardHighlight;

    boolean isOpen() {
        return open;
    }

    void open(double mouseX, double mouseY, Component header, List<Item> items, Font font, int screenWidth, int screenHeight) {
        this.header = header;
        this.items = List.copyOf(items);
        int widest = font.width(header);
        for (Item item : this.items) {
            widest = Math.max(widest, font.width(item.label()));
        }
        w = Math.min(screenWidth - 16, widest + PAD_X * 2);
        h = HEADER_H + this.items.size() * ROW_H + 4;
        x = (int) Math.max(8, Math.min(mouseX, screenWidth - w - 8));
        y = (int) Math.max(8, Math.min(mouseY, screenHeight - h - 8));
        highlighted = -1;
        keyboardHighlight = false;
        open = true;
    }

    void close() {
        open = false;
        items = List.of();
        highlighted = -1;
    }

    void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        if (!open) return;
        int hovered = rowAt(mouseX, mouseY);
        if (hovered >= 0 || !keyboardHighlight) {
            highlighted = hovered;
            keyboardHighlight = false;
        }
        UiDraw.panel(g, x, y, w, h, UiTheme.SURFACE_RAISED);
        UiDraw.outline(g, x, y, w, h, UiTheme.BORDER_STRONG);
        UiDraw.text(g, font, header.getString(), x + PAD_X, y + 5, w - PAD_X * 2, UiTheme.TEXT_MUTED);
        UiDraw.divider(g, x + 4, y + HEADER_H - 1, w - 8);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int rowY = y + HEADER_H + i * ROW_H;
            if (i == highlighted && item.enabled()) {
                UiDraw.panel(g, x + 2, rowY, w - 4, ROW_H, UiTheme.CONTROL_SELECTED);
                if (keyboardHighlight) {
                    UiDraw.outline(g, x + 2, rowY, w - 4, ROW_H, UiTheme.FOCUS);
                }
            }
            g.text(font, item.label(), x + PAD_X, rowY + 5, item.enabled() ? UiTheme.TEXT : UiTheme.TEXT_DISABLED);
        }
    }

    boolean click(double mouseX, double mouseY) {
        if (!open) return false;
        int row = rowAt(mouseX, mouseY);
        if (row >= 0) {
            activate(row);
        } else if (!inside(mouseX, mouseY)) {
            close();
        }
        return true;
    }

    boolean keyPressed(int key) {
        if (!open) return false;
        if (key == InputConstants.KEY_ESCAPE) {
            close();
        } else if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_TAB) {
            moveHighlight(1);
        } else if (key == InputConstants.KEY_UP) {
            moveHighlight(-1);
        } else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_SPACE) {
            if (highlighted >= 0) {
                activate(highlighted);
            }
        }
        return true;
    }

    private void moveHighlight(int step) {
        if (items.isEmpty()) return;
        int index = highlighted >= 0 ? highlighted : step > 0 ? -1 : 0;
        for (int tries = 0; tries < items.size(); tries++) {
            index = Math.floorMod(index + step, items.size());
            if (items.get(index).enabled()) {
                highlighted = index;
                keyboardHighlight = true;
                return;
            }
        }
    }

    private void activate(int row) {
        Item item = items.get(row);
        if (!item.enabled()) return;
        close();
        item.action().run();
    }

    private int rowAt(double mouseX, double mouseY) {
        if (!inside(mouseX, mouseY)) return -1;
        int row = (int) Math.floor((mouseY - y - HEADER_H) / ROW_H);
        return mouseY >= y + HEADER_H && row >= 0 && row < items.size() ? row : -1;
    }

    private boolean inside(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
