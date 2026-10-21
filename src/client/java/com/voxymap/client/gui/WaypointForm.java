package com.voxymap.client.gui;

import com.voxymap.client.map.MinimapRenderer;
import com.voxymap.client.map.VoxyMapSettings;
import com.voxymap.client.map.WaypointDisplay;
import com.voxymap.client.map.WaypointManager;
import com.voxymap.client.map.WaypointPrefs;
import com.voxymap.client.map.WaypointText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import com.voxymap.client.map.TeleportCommand;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WaypointForm {
    public interface Listener {
        void saved(WaypointManager.Waypoint waypoint);

        void cancelled();

        void teleported();
    }

    public static final int NAME_MAX_LENGTH = 64;
    private static final int PANEL_MAX_WIDTH = 320;
    private static final int FIELD_HEIGHT = 20;
    private static final int ROW_GAP = 4;
    private static final int SWATCH_SIZE = 16;
    private static final int SWATCH_GAP = 4;
    private static final int MESSAGE_LINES = 2;
    private static final int LABEL_COLOR = com.voxymap.client.gui.ui.UiTheme.TEXT_MUTED;
    private static final int HINT_COLOR = com.voxymap.client.gui.ui.UiTheme.TEXT_SECONDARY;
    private static final int ERROR_COLOR = com.voxymap.client.gui.ui.UiTheme.DANGER;

    private final String worldId;
    private final WaypointManager.Waypoint existing;
    private final Listener listener;
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private String name;
    private String symbol;
    private String xText;
    private String yText;
    private String zText;
    private int color;
    private WaypointDisplay display;
    private boolean temporary;
    private boolean shared;
    private Component error;
    private Component hint;
    private boolean selectNameOnFocus = true;

    private EditBox nameBox;
    private EditBox symbolBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private Button sharedButton;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final List<Button> swatches = new ArrayList<>();
    private final List<Integer> swatchColors = new ArrayList<>();
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int symbolX;
    private int coordinatesLabelY;
    private int colorLabelY;
    private int messageY;

    private WaypointForm(String worldId, WaypointManager.Waypoint existing, double x, double y, double z,
                         String name, int color, Listener listener) {
        this.worldId = worldId;
        this.existing = existing;
        this.listener = listener;
        this.anchorX = x;
        this.anchorY = y;
        this.anchorZ = z;
        this.name = name;
        this.symbol = existing == null ? "" : nullToEmpty(WaypointPrefs.customSymbol(existing.id()));
        this.xText = blockText(x);
        this.yText = blockText(y);
        this.zText = blockText(z);
        this.color = color;
        this.display = existing == null ? VoxyMapSettings.newWaypointDisplay() : WaypointPrefs.display(existing.id());
        this.shared = existing == null && WaypointManager.canShare(worldId);
        this.hint = Component.translatable("screen.voxymap.waypoint.hint");
    }

    public static WaypointForm create(String worldId, double x, double y, double z, Listener listener) {
        int index = WaypointManager.forWorld(worldId).size() + 1;
        String name = Component.translatable("screen.voxymap.waypoint.default_name", index).getString();
        return new WaypointForm(worldId, null, x, y, z, name, WaypointManager.suggestedColor(worldId), listener);
    }

    public static WaypointForm edit(String worldId, WaypointManager.Waypoint waypoint, Listener listener) {
        return new WaypointForm(worldId, waypoint, waypoint.x(), waypoint.y(), waypoint.z(), waypoint.name(),
                waypoint.color(), listener);
    }

    public Component title() {
        return Component.translatable(existing == null
                ? "screen.voxymap.waypoint.add_title" : "screen.voxymap.waypoint.edit_title");
    }

    public boolean isNew() {
        return existing == null;
    }

    public String editedId() {
        return existing == null ? null : existing.id();
    }

    public int color() {
        return color;
    }

    public void setHint(Component hint) {
        this.hint = hint;
    }

    public List<AbstractWidget> layout(Font font, int screenWidth, int screenHeight, int preferredTop) {
        keepValues();
        widgets.clear();
        swatches.clear();
        swatchColors.clear();
        sharedButton = null;

        panelW = Math.max(160, Math.min(PANEL_MAX_WIDTH, screenWidth - 32));
        int inner = panelW - 20;
        int[] palette = WaypointManager.palette();
        int perRow = Math.max(1, Math.min(palette.length, (inner + SWATCH_GAP) / (SWATCH_SIZE + SWATCH_GAP)));
        int swatchRows = (palette.length + perRow - 1) / perRow;
        boolean showShare = existing == null && WaypointManager.canShare(worldId);
        int toggleRows = 1 + (existing == null ? 1 : 0) + (showShare ? 1 : 0);
        int messageH = MESSAGE_LINES * (font.lineHeight + 2);
        panelH = 24 + 34 + 34 + 12 + swatchRows * (SWATCH_SIZE + SWATCH_GAP) + toggleRows * (FIELD_HEIGHT + ROW_GAP)
                + messageH + FIELD_HEIGHT + 10;
        panelX = (screenWidth - panelW) / 2;
        int top = preferredTop >= 0 ? preferredTop : (screenHeight - panelH) / 2;
        panelY = Math.max(8, Math.min(top, screenHeight - panelH - 8));
        int left = panelX + 10;
        int y = panelY + 24;

        int symbolW = 44;
        nameBox = add(new EditBox(font, left, y + 10, inner - symbolW - 6, FIELD_HEIGHT,
                Component.translatable("screen.voxymap.waypoint.field.name")));
        nameBox.setMaxLength(NAME_MAX_LENGTH);
        nameBox.setValue(name);
        symbolX = left + inner - symbolW;
        symbolBox = add(new EditBox(font, symbolX, y + 10, symbolW, FIELD_HEIGHT,
                Component.translatable("screen.voxymap.waypoint.field.symbol")));
        symbolBox.setMaxLength(WaypointPrefs.MAX_SYMBOL_LENGTH);
        symbolBox.setValue(symbol);
        symbolBox.setHint(Component.literal(WaypointText.initial(name)));
        symbolBox.setTooltip(Tooltip.create(Component.translatable("screen.voxymap.waypoint.field.symbol.tooltip")));
        nameBox.setResponder(value -> {
            name = value;
            symbolBox.setHint(Component.literal(WaypointText.initial(value)));
        });
        if (selectNameOnFocus) {
            nameBox.moveCursorToEnd(false);
            nameBox.setHighlightPos(0);
        }
        y += 34;

        coordinatesLabelY = y;
        int coordW = (inner - 8) / 3;
        xBox = add(coordinateBox(font, left, y + 10, coordW, "x", xText));
        yBox = add(coordinateBox(font, left + coordW + 4, y + 10, coordW, "y", yText));
        zBox = add(coordinateBox(font, left + (coordW + 4) * 2, y + 10, coordW, "z", zText));
        y += 34;

        colorLabelY = y;
        y += 12;
        for (int i = 0; i < palette.length; i++) {
            int swatchColor = palette[i];
            int sx = left + (i % perRow) * (SWATCH_SIZE + SWATCH_GAP);
            int sy = y + (i / perRow) * (SWATCH_SIZE + SWATCH_GAP);
            Button swatch = add(Button.builder(
                            Component.literal(String.format(Locale.ROOT, "#%06X", swatchColor & 0xFFFFFF)),
                            pressed -> color = swatchColor)
                    .bounds(sx, sy, SWATCH_SIZE, SWATCH_SIZE).build());
            swatches.add(swatch);
            swatchColors.add(swatchColor);
        }
        y += swatchRows * (SWATCH_SIZE + SWATCH_GAP);

        Button displayButton = add(Button.builder(displayLabel(), pressed -> {
            display = display.next();
            pressed.setMessage(displayLabel());
            pressed.setTooltip(displayTooltip());
        }).bounds(left, y, inner, FIELD_HEIGHT).build());
        displayButton.setTooltip(displayTooltip());
        y += FIELD_HEIGHT + ROW_GAP;

        if (existing == null) {
            add(Button.builder(temporaryLabel(), pressed -> {
                temporary = !temporary;
                pressed.setMessage(temporaryLabel());
                if (sharedButton != null) {
                    sharedButton.active = !temporary;
                }
            }).bounds(left, y, inner, FIELD_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable("screen.voxymap.waypoint.temporary.tooltip"))).build());
            y += FIELD_HEIGHT + ROW_GAP;
            if (showShare) {
                sharedButton = add(Button.builder(sharedLabel(), pressed -> {
                    shared = !shared;
                    pressed.setMessage(sharedLabel());
                }).bounds(left, y, inner, FIELD_HEIGHT).build());
                sharedButton.active = !temporary;
                y += FIELD_HEIGHT + ROW_GAP;
            }
        }

        messageY = y + 2;
        y += messageH;

        boolean teleport = canTeleport();
        int buttons = teleport ? 3 : 2;
        int buttonW = (inner - 6 * (buttons - 1)) / buttons;
        int buttonX = left;
        add(Button.builder(Component.translatable("screen.voxymap.waypoint.save"), pressed -> submit())
                .bounds(buttonX, y, buttonW, FIELD_HEIGHT).build());
        buttonX += buttonW + 6;
        if (teleport) {
            add(Button.builder(Component.translatable("screen.voxymap.waypoint.teleport"), pressed -> teleport())
                    .bounds(buttonX, y, buttonW, FIELD_HEIGHT).build());
            buttonX += buttonW + 6;
        }
        add(Button.builder(Component.translatable("gui.cancel"), pressed -> listener.cancelled())
                .bounds(buttonX, y, buttonW, FIELD_HEIGHT).build());
        return List.copyOf(widgets);
    }

    public List<AbstractWidget> widgets() {
        return List.copyOf(widgets);
    }

    public EditBox nameBox() {
        return nameBox;
    }

    public boolean owns(GuiEventListener listener) {
        return listener instanceof AbstractWidget widget && widgets.contains(widget);
    }

    public boolean typing(GuiEventListener focused) {
        return focused instanceof EditBox && owns(focused);
    }

    public boolean contains(double x, double y) {
        return x >= panelX && x < panelX + panelW && y >= panelY && y < panelY + panelH;
    }

    public void moveTo(double x, double y, double z) {
        anchorX = x;
        anchorY = y;
        anchorZ = z;
        xText = blockText(x);
        yText = blockText(y);
        zText = blockText(z);
        if (xBox != null) {
            xBox.setValue(xText);
            yBox.setValue(yText);
            zBox.setValue(zText);
        }
        error = null;
    }

    public double[] previewPosition() {
        Integer x = WaypointText.parseCoordinate(xBox == null ? xText : xBox.getValue());
        Integer y = WaypointText.parseCoordinate(yBox == null ? yText : yBox.getValue());
        Integer z = WaypointText.parseCoordinate(zBox == null ? zText : zBox.getValue());
        if (x == null || y == null || z == null) {
            return new double[] {anchorX, anchorY, anchorZ};
        }
        return new double[] {
                sameBlock(anchorX, x) ? anchorX : x + 0.5,
                sameBlock(anchorY, y) ? anchorY : y,
                sameBlock(anchorZ, z) ? anchorZ : z + 0.5};
    }

    public void submit() {
        keepValues();
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            error = Component.translatable("screen.voxymap.waypoint.error.name");
            return;
        }
        Integer x = WaypointText.parseCoordinate(xText);
        Integer y = WaypointText.parseCoordinate(yText);
        Integer z = WaypointText.parseCoordinate(zText);
        if (x == null || y == null || z == null) {
            error = Component.translatable("screen.voxymap.waypoint.error.coordinates");
            return;
        }
        double[] position = previewPosition();
        WaypointManager.Waypoint result;
        if (existing != null) {
            result = WaypointManager.update(worldId, existing.id(), trimmed, position[0], position[1], position[2], color)
                    ? existing : null;
        } else if (temporary) {
            result = WaypointManager.addTemporary(worldId, trimmed, position[0], position[1], position[2], color);
        } else {
            result = WaypointManager.add(worldId, trimmed, position[0], position[1], position[2], color, shared);
        }
        if (result == null) {
            error = Component.translatable("screen.voxymap.waypoint.error.save");
            return;
        }
        WaypointPrefs.setSymbol(result.id(), symbol);
        WaypointPrefs.setDisplay(result.id(), display);
        listener.saved(result);
    }

    public void extractPanel(GuiGraphicsExtractor graphics, Font font) {
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, com.voxymap.client.gui.ui.UiTheme.SURFACE_RAISED);
        graphics.outline(panelX, panelY, panelW, panelH, com.voxymap.client.gui.ui.UiTheme.BORDER);
        graphics.text(font, title(), panelX + 10, panelY + 8, 0xFFFFFFFF);
        int left = panelX + 10;
        graphics.text(font, Component.translatable("screen.voxymap.waypoint.field.name"), left, panelY + 24, LABEL_COLOR);
        graphics.text(font, Component.translatable("screen.voxymap.waypoint.field.symbol"), symbolX, panelY + 24, LABEL_COLOR);
        graphics.text(font, Component.translatable("screen.voxymap.waypoint.field.coordinates"), left, coordinatesLabelY, LABEL_COLOR);
        graphics.text(font, Component.translatable("screen.voxymap.waypoint.field.color"), left, colorLabelY, LABEL_COLOR);
    }

    public void extractOverlay(GuiGraphicsExtractor graphics, Font font) {
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
        Component message = error != null ? error : hint;
        if (message == null) return;
        int lineY = messageY;
        List<FormattedCharSequence> lines = font.split(message, panelW - 20);
        for (int i = 0; i < Math.min(MESSAGE_LINES, lines.size()); i++) {
            graphics.text(font, lines.get(i), panelX + 10, lineY, error != null ? ERROR_COLOR : HINT_COLOR);
            lineY += font.lineHeight + 2;
        }
    }

    private <T extends AbstractWidget> T add(T widget) {
        widgets.add(widget);
        return widget;
    }

    private void keepValues() {
        if (nameBox == null) return;
        name = nameBox.getValue();
        symbol = symbolBox.getValue();
        xText = xBox.getValue();
        yText = yBox.getValue();
        zText = zBox.getValue();
        selectNameOnFocus = false;
    }

    private EditBox coordinateBox(Font font, int x, int y, int w, String axis, String value) {
        EditBox box = new EditBox(font, x, y, w, FIELD_HEIGHT, Component.literal(axis.toUpperCase(Locale.ROOT)));
        box.setMaxLength(9);
        box.setValue(value);
        box.setHint(Component.literal(axis.toUpperCase(Locale.ROOT)));
        return box;
    }

    private Component displayLabel() {
        return Component.translatable("screen.voxymap.waypoint.display", Component.translatable(display.translationKey()));
    }

    private Tooltip displayTooltip() {
        return Tooltip.create(Component.translatable(display.tooltipTranslationKey()));
    }

    private Component temporaryLabel() {
        return Component.translatable(temporary ? "screen.voxymap.waypoint.temporary.on" : "screen.voxymap.waypoint.temporary.off")
                .withStyle(temporary ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
    }

    private Component sharedLabel() {
        return Component.translatable(shared ? "screen.voxymap.waypoint.share.shared" : "screen.voxymap.waypoint.share.local")
                .withStyle(shared ? ChatFormatting.GREEN : ChatFormatting.WHITE);
    }

    private boolean canTeleport() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return false;
        }
        return worldId.equals(MinimapRenderer.currentWorldId(minecraft)) && TeleportCommand.available(minecraft);
    }

    private void teleport() {
        keepValues();
        Integer x = WaypointText.parseCoordinate(xText);
        Integer y = WaypointText.parseCoordinate(yText);
        Integer z = WaypointText.parseCoordinate(zText);
        Minecraft minecraft = Minecraft.getInstance();
        if (x == null || y == null || z == null || minecraft.getConnection() == null) {
            error = Component.translatable("screen.voxymap.waypoint.error.coordinates");
            return;
        }
        if (TeleportCommand.send(minecraft, x, y, z)) {
            listener.teleported();
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blockText(double value) {
        return Integer.toString((int) Math.floor(value));
    }

    private static boolean sameBlock(double original, int typed) {
        return (int) Math.floor(original) == typed;
    }
}
