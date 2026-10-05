package com.voxymap.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.voxymap.client.map.MinimapRenderer;
import com.voxymap.client.map.WaypointManager;
import com.voxymap.client.map.WaypointPrefs;
import com.voxymap.client.map.WaypointText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WaypointEditScreen extends Screen {
    private static final int PANEL_MAX_WIDTH = 320;
    private static final int FIELD_HEIGHT = 20;
    private static final int SWATCH_SIZE = 16;
    private static final int SWATCH_GAP = 4;
    private static final int ACCENT = 0xFF3BA4FF;
    private static final int ACCENT_LIGHT = 0xFF7DEBFF;
    private static final int LABEL_COLOR = 0xFF9FD8FF;
    private static final int ERROR_COLOR = 0xFFFF7A6B;

    private final Screen parent;
    private final String worldId;
    private final WaypointManager.Waypoint existing;
    private final double startX;
    private final double startY;
    private final double startZ;
    private final String startName;
    private int color;
    private boolean shared;
    private boolean temporary;

    private EditBox nameBox;
    private EditBox symbolBox;
    private int symbolX;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private final List<Button> swatches = new ArrayList<>();
    private final List<Integer> swatchColors = new ArrayList<>();
    private Component error;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int swatchRowY;

    private WaypointEditScreen(Screen parent, String worldId, WaypointManager.Waypoint existing,
                               double x, double y, double z, String name, int color) {
        super(Component.translatable(existing == null
                ? "screen.voxymap.waypoint.add_title" : "screen.voxymap.waypoint.edit_title"));
        this.parent = parent;
        this.worldId = worldId;
        this.existing = existing;
        this.startX = x;
        this.startY = y;
        this.startZ = z;
        this.startName = name;
        this.color = color;
        this.shared = existing == null && WaypointManager.canShare(worldId);
    }

    public static WaypointEditScreen create(Screen parent, String worldId, double x, double y, double z) {
        int index = WaypointManager.forWorld(worldId).size() + 1;
        String name = Component.translatable("screen.voxymap.waypoint.default_name", index).getString();
        return new WaypointEditScreen(parent, worldId, null, x, y, z, name, WaypointManager.suggestedColor(worldId));
    }

    public static WaypointEditScreen edit(Screen parent, String worldId, WaypointManager.Waypoint waypoint) {
        return new WaypointEditScreen(parent, worldId, waypoint, waypoint.x(), waypoint.y(), waypoint.z(),
                waypoint.name(), waypoint.color());
    }

    @Override
    protected void init() {
        String keptName = nameBox == null ? startName : nameBox.getValue();
        String keptSymbol = symbolBox == null
                ? (existing == null ? "" : nullToEmpty(WaypointPrefs.customSymbol(existing.id())))
                : symbolBox.getValue();
        String keptX = xBox == null ? blockText(startX) : xBox.getValue();
        String keptY = yBox == null ? blockText(startY) : yBox.getValue();
        String keptZ = zBox == null ? blockText(startZ) : zBox.getValue();
        swatches.clear();
        swatchColors.clear();
        panelW = Math.min(PANEL_MAX_WIDTH, width - 32);
        int inner = panelW - 20;
        int[] palette = WaypointManager.palette();
        int perRow = Math.max(1, Math.min(palette.length, (inner + SWATCH_GAP) / (SWATCH_SIZE + SWATCH_GAP)));
        int swatchRows = (palette.length + perRow - 1) / perRow;
        boolean showTemporary = existing == null;
        int toggles = showTemporary ? 1 + (WaypointManager.canShare(worldId) ? 1 : 0) : 0;
        panelH = 24 + 34 + 34 + 14 + swatchRows * (SWATCH_SIZE + SWATCH_GAP) + toggles * 24 + 14 + 28;
        panelX = (width - panelW) / 2;
        panelY = Math.max(8, (height - panelH) / 2);
        int left = panelX + 10;
        int y = panelY + 24;

        int symbolW = 44;
        nameBox = addRenderableWidget(new EditBox(font, left, y + 10, inner - symbolW - 6, FIELD_HEIGHT,
                Component.translatable("screen.voxymap.waypoint.field.name")));
        nameBox.setMaxLength(64);
        nameBox.setValue(keptName);
        symbolX = left + inner - symbolW;
        symbolBox = addRenderableWidget(new EditBox(font, symbolX, y + 10, symbolW, FIELD_HEIGHT,
                Component.translatable("screen.voxymap.waypoint.field.symbol")));
        symbolBox.setMaxLength(WaypointPrefs.MAX_SYMBOL_LENGTH);
        symbolBox.setValue(keptSymbol);
        symbolBox.setHint(Component.literal(WaypointText.initial(keptName)));
        nameBox.setResponder(value -> symbolBox.setHint(Component.literal(WaypointText.initial(value))));
        y += 34;

        int coordW = (inner - 8) / 3;
        xBox = addRenderableWidget(coordinateBox(left, y + 10, coordW, "x", keptX));
        yBox = addRenderableWidget(coordinateBox(left + coordW + 4, y + 10, coordW, "y", keptY));
        zBox = addRenderableWidget(coordinateBox(left + (coordW + 4) * 2, y + 10, coordW, "z", keptZ));
        y += 34;

        y += 14;
        swatchRowY = y;
        for (int i = 0; i < palette.length; i++) {
            int swatchColor = palette[i];
            int sx = left + (i % perRow) * (SWATCH_SIZE + SWATCH_GAP);
            int sy = y + (i / perRow) * (SWATCH_SIZE + SWATCH_GAP);
            Button swatch = addRenderableWidget(Button.builder(
                            Component.literal(String.format(Locale.ROOT, "#%06X", swatchColor & 0xFFFFFF)),
                            pressed -> color = swatchColor)
                    .bounds(sx, sy, SWATCH_SIZE, SWATCH_SIZE).build());
            swatches.add(swatch);
            swatchColors.add(swatchColor);
        }
        y += swatchRows * (SWATCH_SIZE + SWATCH_GAP);

        if (showTemporary) {
            addRenderableWidget(Button.builder(temporaryLabel(), pressed -> {
                temporary = !temporary;
                pressed.setMessage(temporaryLabel());
            }).bounds(left, y, inner, FIELD_HEIGHT).build());
            y += 24;
            if (WaypointManager.canShare(worldId)) {
                addRenderableWidget(Button.builder(sharedLabel(), pressed -> {
                    shared = !shared;
                    pressed.setMessage(sharedLabel());
                }).bounds(left, y, inner, FIELD_HEIGHT).build());
                y += 24;
            }
        }

        int buttonsY = panelY + panelH - 26;
        boolean teleport = canTeleport();
        int buttons = teleport ? 3 : 2;
        int buttonW = (inner - 6 * (buttons - 1)) / buttons;
        int buttonX = left;
        addRenderableWidget(Button.builder(Component.translatable("screen.voxymap.waypoint.save"), pressed -> save())
                .bounds(buttonX, buttonsY, buttonW, FIELD_HEIGHT).build());
        buttonX += buttonW + 6;
        if (teleport) {
            addRenderableWidget(Button.builder(Component.translatable("screen.voxymap.waypoint.teleport"),
                            pressed -> teleport())
                    .bounds(buttonX, buttonsY, buttonW, FIELD_HEIGHT).build());
            buttonX += buttonW + 6;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), pressed -> onClose())
                .bounds(buttonX, buttonsY, buttonW, FIELD_HEIGHT).build());
        setInitialFocus(nameBox);
    }

    private EditBox coordinateBox(int x, int y, int w, String axis, String value) {
        EditBox box = new EditBox(font, x, y, w, FIELD_HEIGHT, Component.literal(axis.toUpperCase(Locale.ROOT)));
        box.setMaxLength(9);
        box.setValue(value);
        box.setHint(Component.literal(axis.toUpperCase(Locale.ROOT)));
        return box;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean canTeleport() {
        if (minecraft == null || minecraft.player == null || minecraft.getConnection() == null || existing == null) {
            return false;
        }
        return worldId.equals(MinimapRenderer.currentWorldId(minecraft))
                && minecraft.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private void teleport() {
        Integer x = WaypointText.parseCoordinate(xBox.getValue());
        Integer y = WaypointText.parseCoordinate(yBox.getValue());
        Integer z = WaypointText.parseCoordinate(zBox.getValue());
        if (x == null || y == null || z == null || minecraft == null || minecraft.getConnection() == null) {
            error = Component.translatable("screen.voxymap.waypoint.error.coordinates");
            return;
        }
        minecraft.getConnection().sendCommand("tp @s " + x + " " + y + " " + z);
        minecraft.setScreenAndShow(null);
    }

    private static String blockText(double value) {
        return Integer.toString((int) Math.floor(value));
    }

    private Component temporaryLabel() {
        return Component.translatable(temporary ? "screen.voxymap.waypoint.temporary.on" : "screen.voxymap.waypoint.temporary.off")
                .withStyle(temporary ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
    }

    private Component sharedLabel() {
        return Component.translatable(shared ? "screen.voxymap.waypoint.share.shared" : "screen.voxymap.waypoint.share.local")
                .withStyle(shared ? ChatFormatting.GREEN : ChatFormatting.WHITE);
    }

    private void save() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            error = Component.translatable("screen.voxymap.waypoint.error.name");
            setFocused(nameBox);
            return;
        }
        Integer x = WaypointText.parseCoordinate(xBox.getValue());
        Integer y = WaypointText.parseCoordinate(yBox.getValue());
        Integer z = WaypointText.parseCoordinate(zBox.getValue());
        if (x == null || y == null || z == null) {
            error = Component.translatable("screen.voxymap.waypoint.error.coordinates");
            return;
        }
        double wx = sameBlock(startX, x) ? startX : x + 0.5;
        double wy = sameBlock(startY, y) ? startY : y;
        double wz = sameBlock(startZ, z) ? startZ : z + 0.5;
        WaypointManager.Waypoint result;
        if (existing != null) {
            WaypointManager.update(worldId, existing.id(), name, wx, wy, wz, color);
            result = existing;
        } else if (temporary) {
            result = WaypointManager.addTemporary(worldId, name, wx, wy, wz, color);
        } else {
            result = WaypointManager.add(worldId, name, wx, wy, wz, color, shared);
        }
        if (result == null) {
            error = Component.translatable("screen.voxymap.waypoint.error.save");
            return;
        }
        WaypointPrefs.setSymbol(result.id(), symbolBox.getValue());
        onClose();
    }

    private static boolean sameBlock(double original, int typed) {
        return (int) Math.floor(original) == typed;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, width, height, 0xB0050A12);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF00D141F);
        graphics.outline(panelX, panelY, panelW, panelH, ACCENT);
        graphics.fill(panelX, panelY, panelX + 3, panelY + panelH, ACCENT);
        graphics.text(font, title, panelX + 10, panelY + 8, 0xFFFFFFFF);
        int left = panelX + 10;
        graphics.text(font, Component.translatable("screen.voxymap.waypoint.field.name"), left, panelY + 24, LABEL_COLOR);
        graphics.text(font, Component.translatable("screen.voxymap.waypoint.field.symbol"), symbolX, panelY + 24, LABEL_COLOR);
        graphics.text(font, Component.translatable("screen.voxymap.waypoint.field.coordinates"), left, panelY + 58, LABEL_COLOR);
        graphics.text(font, Component.translatable("screen.voxymap.waypoint.field.color"), left, swatchRowY - 12, LABEL_COLOR);

        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);

        for (int i = 0; i < swatches.size(); i++) {
            Button swatch = swatches.get(i);
            int swatchColor = swatchColors.get(i);
            int x0 = swatch.getX();
            int y0 = swatch.getY();
            boolean selected = swatchColor == color;
            boolean focused = swatch.isHoveredOrFocused();
            graphics.fill(x0, y0, x0 + SWATCH_SIZE, y0 + SWATCH_SIZE, selected || focused ? 0xFFFFFFFF : 0xFF0A0A12);
            graphics.fill(x0 + 2, y0 + 2, x0 + SWATCH_SIZE - 2, y0 + SWATCH_SIZE - 2, swatchColor);
            if (selected) {
                graphics.fill(x0 + 6, y0 + 6, x0 + SWATCH_SIZE - 6, y0 + SWATCH_SIZE - 6, 0xFF0A0A12);
            }
        }

        if (error != null) {
            graphics.text(font, error, left, panelY + panelH - 38, ERROR_COLOR);
        } else if (existing == null) {
            graphics.text(font, Component.translatable("screen.voxymap.waypoint.hint"), left, panelY + panelH - 38, ACCENT_LIGHT);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) && getFocused() instanceof EditBox) {
            save();
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
