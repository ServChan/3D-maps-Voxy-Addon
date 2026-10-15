package com.voxymap.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.voxymap.client.gui.ui.UiDraw;
import com.voxymap.client.gui.ui.UiIcons;
import com.voxymap.client.gui.ui.UiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class MapObjectPanel {
    private static final int WIDTH = 252;
    private static final int ROW_H = 27;
    private static final int BUTTON_H = 19;
    private static final int TOGGLE_H = 22;
    private static final int EYE_SIZE = 15;
    private static final long DOUBLE_CLICK_NANOS = 350_000_000L;

    private boolean open;
    private boolean searchFocused;
    private Filter filter = Filter.ALL;
    private final StringBuilder query = new StringBuilder();
    private int scroll;
    private int panelX;
    private int panelY;
    private int panelH;
    private int listY;
    private int listH;
    private int toggleX;
    private int toggleY;
    private int toggleW;
    private List<Entry> visible = List.of();
    private String selectedId;
    private String followedId;
    private long lastRowClickNanos;
    private String lastRowClickId;

    void setObjects(List<Entry> entries, String selectedId, String followedId) {
        this.selectedId = selectedId;
        this.followedId = followedId;
        String normalized = query.toString().trim().toLowerCase(Locale.ROOT);
        List<Entry> filtered = new ArrayList<>();
        for (Entry entry : entries) {
            if (filter != Filter.ALL && entry.kind != filter.kind) continue;
            if (!normalized.isEmpty() && !entry.name.toLowerCase(Locale.ROOT).contains(normalized)
                    && !entry.detail.toLowerCase(Locale.ROOT).contains(normalized)) continue;
            filtered.add(entry);
        }
        filtered.sort(Comparator.comparing((Entry entry) -> entry.kind.ordinal())
                .thenComparing(entry -> entry.name, String.CASE_INSENSITIVE_ORDER));
        visible = List.copyOf(filtered);
        clampScroll();
    }

    boolean isOpen() {
        return open;
    }

    boolean contains(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, toggleX, toggleY, toggleW, TOGGLE_H)
                || open && inside(mouseX, mouseY, panelX, panelY, WIDTH, panelH);
    }

    boolean searchFocused() {
        return open && searchFocused;
    }

    void blurSearch() {
        searchFocused = false;
    }

    void toggle() {
        open = !open;
        searchFocused = false;
    }

    void render(GuiGraphicsExtractor g, Font font, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        toggleX = 12;
        toggleY = 76;
        String toggleLabel = Component.translatable("screen.voxymap.objects").getString();
        toggleW = Math.max(92, font.width(toggleLabel) + UiTheme.ICON_SIZE + 22);
        panelX = 12;
        panelY = toggleY + TOGGLE_H + UiTheme.SPACE_S;
        panelH = Math.max(128, screenHeight - panelY - 44);

        boolean toggleHovered = inside(mouseX, mouseY, toggleX, toggleY, toggleW, TOGGLE_H);
        UiDraw.panel(g, toggleX, toggleY, toggleW, TOGGLE_H,
                open ? UiTheme.CONTROL_SELECTED : toggleHovered ? UiTheme.CONTROL_HOVER : UiTheme.SURFACE);
        UiDraw.outline(g, toggleX, toggleY, toggleW, TOGGLE_H, open ? UiTheme.ACCENT_BRIGHT : UiTheme.BORDER);
        UiIcons.draw(g, UiIcons.Icon.LAYERS, toggleX + 7, toggleY + (TOGGLE_H - UiTheme.ICON_SIZE) / 2, UiTheme.TEXT_SECONDARY);
        g.text(font, toggleLabel, toggleX + 7 + UiTheme.ICON_SIZE + 5, UiDraw.textY(font, toggleY, TOGGLE_H), UiTheme.TEXT);
        if (!open) return;

        UiDraw.card(g, panelX, panelY, WIDTH, panelH);
        g.text(font, Component.translatable("screen.voxymap.objects.title"), panelX + 10, panelY + 9, UiTheme.TEXT);
        String count = Integer.toString(visible.size());
        g.text(font, count, panelX + WIDTH - 10 - font.width(count), panelY + 9, UiTheme.TEXT_MUTED);

        int searchY = panelY + 24;
        UiDraw.panel(g, panelX + 8, searchY, WIDTH - 16, 20, UiTheme.SURFACE_SUNKEN);
        UiDraw.outline(g, panelX + 8, searchY, WIDTH - 16, 20, searchFocused ? UiTheme.ACCENT_BRIGHT : UiTheme.BORDER);
        UiIcons.draw(g, UiIcons.Icon.SEARCH, panelX + 13, searchY + 6, searchFocused ? UiTheme.TEXT_SECONDARY : UiTheme.TEXT_MUTED);
        boolean placeholder = query.isEmpty() && !searchFocused;
        String search = placeholder
                ? Component.translatable("screen.voxymap.objects.search").getString()
                : query.toString() + (searchFocused && (System.nanoTime() / 500_000_000L) % 2 == 0 ? "_" : "");
        g.text(font, UiDraw.ellipsize(font, search, WIDTH - 44), panelX + 27, searchY + 6,
                placeholder ? UiTheme.TEXT_MUTED : UiTheme.TEXT);

        int filterY = searchY + 25;
        boolean filterHovered = inside(mouseX, mouseY, panelX + 8, filterY, WIDTH - 16, 18);
        UiDraw.panel(g, panelX + 8, filterY, WIDTH - 16, 18, filterHovered ? UiTheme.CONTROL_HOVER : UiTheme.CONTROL);
        UiIcons.draw(g, UiIcons.Icon.CHEVRON_LEFT, panelX + 10, filterY + 5, UiTheme.TEXT_MUTED);
        UiIcons.draw(g, UiIcons.Icon.CHEVRON_RIGHT, panelX + WIDTH - 19, filterY + 5, UiTheme.TEXT_MUTED);
        String filterLabel = Component.translatable("screen.voxymap.objects.filter." + filter.key).getString();
        UiDraw.centeredText(g, font, filterLabel, panelX + WIDTH / 2, filterY + 5, WIDTH - 44, UiTheme.TEXT);

        listY = filterY + 24;
        Entry selected = selected();
        int detailsH = selected == null ? 24 : 70;
        listH = Math.max(ROW_H, panelH - (listY - panelY) - detailsH - 6);
        int rows = Math.max(1, listH / ROW_H);
        int end = Math.min(visible.size(), scroll + rows);
        for (int i = scroll; i < end; i++) {
            Entry entry = visible.get(i);
            int y = listY + (i - scroll) * ROW_H;
            boolean selectedRow = entry.id.equals(selectedId);
            boolean hovered = inside(mouseX, mouseY, panelX + 8, y, WIDTH - 16, ROW_H - 2);
            if (selectedRow || hovered) {
                UiDraw.panel(g, panelX + 8, y, WIDTH - 16, ROW_H - 2, selectedRow ? UiTheme.CONTROL_SELECTED : UiTheme.ROW_HOVER);
            }
            g.fill(panelX + 8, y + 3, panelX + 10, y + ROW_H - 5, entry.color);
            UiIcons.draw(g, kindIcon(entry.kind), panelX + 14, y + 8, entry.hidden ? UiTheme.TEXT_DISABLED : entry.color);
            int textRight = WIDTH - 54;
            Component name = Component.literal(UiDraw.ellipsize(font, entry.name, textRight));
            if (entry.hidden) {
                name = name.copy().withStyle(net.minecraft.ChatFormatting.STRIKETHROUGH);
            }
            g.text(font, name, panelX + 28, y + 4, entry.hidden ? UiTheme.TEXT_MUTED : UiTheme.TEXT);
            g.text(font, UiDraw.ellipsize(font, entry.detail, textRight), panelX + 28, y + 15, UiTheme.TEXT_MUTED);
            if (entry.id.equals(followedId)) {
                UiIcons.draw(g, UiIcons.Icon.EYE, panelX + WIDTH - 36, y + 8, UiTheme.SUCCESS);
            }
            if (entry.kind == Kind.PLAYER && entry.hideable) {
                boolean eyeHovered = inside(mouseX, mouseY, eyeX(), y + 4, EYE_SIZE, EYE_SIZE);
                if (eyeHovered) {
                    UiDraw.panel(g, eyeX(), y + 4, EYE_SIZE, EYE_SIZE, UiTheme.CONTROL_HOVER);
                }
                UiIcons.draw(g, entry.hidden ? UiIcons.Icon.EYE_OFF : UiIcons.Icon.EYE, eyeX() + 3, y + 7,
                        entry.hidden ? UiTheme.WARNING : eyeHovered ? UiTheme.TEXT : UiTheme.TEXT_MUTED);
            }
        }
        if (visible.isEmpty()) {
            UiDraw.text(g, font, Component.translatable("screen.voxymap.objects.empty").getString(), panelX + 14,
                    listY + 8, WIDTH - 28, UiTheme.TEXT_MUTED);
        }

        int detailsY = panelY + panelH - detailsH;
        UiDraw.divider(g, panelX + 8, detailsY, WIDTH - 16);
        if (selected == null) {
            UiDraw.text(g, font, Component.translatable("screen.voxymap.objects.select").getString(), panelX + 12,
                    detailsY + 8, WIDTH - 24, UiTheme.TEXT_MUTED);
            return;
        }
        UiDraw.text(g, font, selected.name, panelX + 12, detailsY + 7, WIDTH - 28, UiTheme.TEXT);
        UiDraw.text(g, font, selected.detail, panelX + 12, detailsY + 19, WIDTH - 28, UiTheme.TEXT_SECONDARY);
        int buttonY = panelY + panelH - BUTTON_H - 8;
        drawActionButton(g, font, panelX + 9, buttonY, 51, "screen.voxymap.objects.center", true, mouseX, mouseY, false);
        drawActionButton(g, font, panelX + 63, buttonY, 51, "screen.voxymap.objects.follow", selected.followable,
                mouseX, mouseY, false);
        drawActionButton(g, font, panelX + 117, buttonY, 51, "screen.voxymap.objects.edit", selected.editable,
                mouseX, mouseY, false);
        drawActionButton(g, font, panelX + 171, buttonY, 72, "screen.voxymap.objects.delete",
                selected.editable && selected.kind != Kind.SCHEMATIC, mouseX, mouseY, true);
    }

    private int eyeX() {
        return panelX + WIDTH - 24;
    }

    Action click(double mouseX, double mouseY, int button) {
        if (inside(mouseX, mouseY, toggleX, toggleY, toggleW, TOGGLE_H)) {
            if (button == InputConstants.MOUSE_BUTTON_LEFT) toggle();
            return Action.consumed();
        }
        if (!open || !inside(mouseX, mouseY, panelX, panelY, WIDTH, panelH)) {
            searchFocused = false;
            return null;
        }
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return Action.consumed();
        int searchY = panelY + 24;
        if (inside(mouseX, mouseY, panelX + 8, searchY, WIDTH - 16, 20)) {
            searchFocused = true;
            return Action.consumed();
        }
        searchFocused = false;
        int filterY = searchY + 25;
        if (inside(mouseX, mouseY, panelX + 8, filterY, WIDTH - 16, 18)) {
            filter = mouseX < panelX + WIDTH / 3.0 ? filter.previous() : filter.next();
            scroll = 0;
            return new Action(ActionType.REFILTER, null);
        }
        if (mouseY >= listY && mouseY < listY + listH) {
            int row = (int) ((mouseY - listY) / ROW_H);
            if (row >= Math.max(1, listH / ROW_H)) {
                return Action.consumed();
            }
            int index = scroll + row;
            if (index >= 0 && index < visible.size()) {
                Entry entry = visible.get(index);
                int rowY = listY + (index - scroll) * ROW_H;
                if (entry.kind == Kind.PLAYER && entry.hideable && inside(mouseX, mouseY, eyeX(), rowY + 4, EYE_SIZE, EYE_SIZE)) {
                    return new Action(ActionType.HIDE, entry);
                }
                long now = System.nanoTime();
                boolean twice = entry.id.equals(lastRowClickId) && now - lastRowClickNanos <= DOUBLE_CLICK_NANOS;
                lastRowClickId = entry.id;
                lastRowClickNanos = now;
                return new Action(twice ? ActionType.CENTER : ActionType.SELECT, entry);
            }
            return Action.consumed();
        }
        Entry selected = selected();
        if (selected != null) {
            int buttonY = panelY + panelH - BUTTON_H - 8;
            if (inside(mouseX, mouseY, panelX + 9, buttonY, 51, BUTTON_H)) return new Action(ActionType.CENTER, selected);
            if (inside(mouseX, mouseY, panelX + 63, buttonY, 51, BUTTON_H) && selected.followable) return new Action(ActionType.FOLLOW, selected);
            if (inside(mouseX, mouseY, panelX + 117, buttonY, 51, BUTTON_H) && selected.editable) return new Action(ActionType.EDIT, selected);
            if (inside(mouseX, mouseY, panelX + 171, buttonY, 72, BUTTON_H) && selected.editable
                    && selected.kind != Kind.SCHEMATIC) return new Action(ActionType.DELETE, selected);
        }
        return Action.consumed();
    }

    boolean scroll(double mouseX, double mouseY, double amount) {
        if (!open || !inside(mouseX, mouseY, panelX, panelY, WIDTH, panelH)) return false;
        scroll += amount > 0 ? -3 : 3;
        clampScroll();
        return true;
    }

    boolean keyPressed(int keyCode) {
        if (!searchFocused) return false;
        if (keyCode == InputConstants.KEY_ESCAPE) {
            searchFocused = false;
        } else if (keyCode == InputConstants.KEY_BACKSPACE && !query.isEmpty()) {
            query.deleteCharAt(query.length() - 1);
            scroll = 0;
        }
        return true;
    }

    boolean charTyped(int codepoint) {
        if (!searchFocused || Character.isISOControl(codepoint) || query.length() >= 64) return searchFocused;
        query.appendCodePoint(codepoint);
        scroll = 0;
        return true;
    }

    private Entry selected() {
        if (selectedId == null) return null;
        for (Entry entry : visible) if (entry.id.equals(selectedId)) return entry;
        return null;
    }

    private void clampScroll() {
        int rows = Math.max(1, listH / ROW_H);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, visible.size() - rows)));
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private static void drawActionButton(GuiGraphicsExtractor g, Font font, int x, int y, int w, String key,
                                         boolean enabled, int mouseX, int mouseY, boolean danger) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, w, BUTTON_H);
        int fill = !enabled ? UiTheme.CONTROL_DISABLED
                : danger ? (hovered ? UiTheme.DANGER_FILL_HOVER : UiTheme.DANGER_FILL)
                : hovered ? UiTheme.CONTROL_HOVER : UiTheme.CONTROL;
        UiDraw.panel(g, x, y, w, BUTTON_H, fill);
        if (enabled && !danger) {
            UiDraw.outline(g, x, y, w, BUTTON_H, hovered ? UiTheme.BORDER_STRONG : UiTheme.BORDER);
        }
        UiDraw.centeredText(g, font, Component.translatable(key).getString(), x + w / 2, UiDraw.textY(font, y, BUTTON_H),
                w - 6, enabled ? UiTheme.TEXT : UiTheme.TEXT_DISABLED);
    }

    private static UiIcons.Icon kindIcon(Kind kind) {
        return switch (kind) {
            case PLAYER -> UiIcons.Icon.PLAYER;
            case WAYPOINT -> UiIcons.Icon.PIN;
            case TERRITORY -> UiIcons.Icon.GRID;
            case SCHEMATIC -> UiIcons.Icon.LAYERS;
        };
    }

    enum Kind { PLAYER, WAYPOINT, TERRITORY, SCHEMATIC }

    enum ActionType { CONSUMED, REFILTER, SELECT, CENTER, FOLLOW, EDIT, DELETE, HIDE }

    record Entry(String id, Kind kind, String name, String detail, double x, double y, double z,
                 int color, boolean followable, boolean editable, boolean hideable, boolean hidden) {
        Entry(String id, Kind kind, String name, String detail, double x, double y, double z,
              int color, boolean followable, boolean editable) {
            this(id, kind, name, detail, x, y, z, color, followable, editable, false, false);
        }
    }

    record Action(ActionType type, Entry entry) {
        private static Action consumed() {
            return new Action(ActionType.CONSUMED, null);
        }
    }

    enum Filter {
        ALL("all", null), PLAYERS("players", Kind.PLAYER), WAYPOINTS("waypoints", Kind.WAYPOINT),
        TERRITORIES("territories", Kind.TERRITORY), SCHEMATICS("schematics", Kind.SCHEMATIC);

        private final String key;
        private final Kind kind;

        Filter(String key, Kind kind) {
            this.key = key;
            this.kind = kind;
        }

        private Filter next() {
            return next(com.voxymap.client.integration.LitematicaPlusBridge.isAvailable());
        }

        private Filter previous() {
            Filter[] values = values();
            Filter candidate = values[Math.floorMod(ordinal() - 1, values.length)];
            return candidate == SCHEMATICS && !com.voxymap.client.integration.LitematicaPlusBridge.isAvailable()
                    ? values[Math.floorMod(candidate.ordinal() - 1, values.length)] : candidate;
        }

        Filter next(boolean schematicsInstalled) {
            Filter[] values = values();
            Filter candidate = values[(ordinal() + 1) % values.length];
            return candidate == SCHEMATICS && !schematicsInstalled ? values[(candidate.ordinal() + 1) % values.length]
                    : candidate;
        }
    }
}
