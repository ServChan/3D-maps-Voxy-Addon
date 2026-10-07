package com.voxymap.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
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
        toggleY = 72;
        String toggleLabel = Component.translatable("screen.voxymap.objects").getString();
        toggleW = Math.max(92, font.width(toggleLabel) + 24);
        panelX = 12;
        panelY = 101;
        panelH = Math.max(128, screenHeight - panelY - 38);

        drawPanel(g, toggleX, toggleY, toggleW, 22, 0xCC030914, open ? 0xFFFFC94D : 0xFF3BA4FF);
        g.text(font, (open ? "− " : "+ ") + toggleLabel, toggleX + 8, toggleY + 7, 0xFFE9F2FF);
        if (!open) return;

        drawPanel(g, panelX, panelY, WIDTH, panelH, 0xE6030914, 0xFF3BA4FF);
        g.text(font, Component.translatable("screen.voxymap.objects.title"), panelX + 10, panelY + 9, 0xFFFFFFFF);

        int searchY = panelY + 27;
        g.fill(panelX + 8, searchY, panelX + WIDTH - 8, searchY + 20,
                searchFocused ? 0xFF102A38 : 0xCC07111F);
        g.fill(panelX + 8, searchY + 19, panelX + WIDTH - 8, searchY + 20,
                searchFocused ? 0xFFFFC94D : 0xFF36536C);
        String search = query.isEmpty() && !searchFocused
                ? Component.translatable("screen.voxymap.objects.search").getString()
                : query.toString() + (searchFocused ? "_" : "");
        g.text(font, trim(font, search, WIDTH - 28), panelX + 13, searchY + 6,
                query.isEmpty() && !searchFocused ? 0xFF7894AA : 0xFFE9F2FF);

        int filterY = searchY + 26;
        String filterLabel = Component.translatable("screen.voxymap.objects.filter." + filter.key).getString();
        g.fill(panelX + 8, filterY, panelX + WIDTH - 8, filterY + 19, 0xCC102A38);
        g.text(font, filterLabel, panelX + 13, filterY + 6, 0xFF9FD8FF);

        listY = filterY + 25;
        Entry selected = selected();
        int detailsH = selected == null ? 24 : 68;
        listH = Math.max(27, panelH - (listY - panelY) - detailsH - 8);
        int rows = Math.max(1, listH / ROW_H);
        int end = Math.min(visible.size(), scroll + rows);
        for (int i = scroll; i < end; i++) {
            Entry entry = visible.get(i);
            int y = listY + (i - scroll) * ROW_H;
            boolean selectedRow = entry.id.equals(selectedId);
            boolean hovered = mouseX >= panelX + 8 && mouseX < panelX + WIDTH - 8
                    && mouseY >= y && mouseY < y + ROW_H - 2;
            g.fill(panelX + 8, y, panelX + WIDTH - 8, y + ROW_H - 2,
                    selectedRow ? 0xEE17334A : hovered ? 0xCC102A38 : 0x9907111F);
            g.fill(panelX + 8, y, panelX + 11, y + ROW_H - 2, entry.color);
            g.text(font, kindGlyph(entry.kind), panelX + 16, y + 5, entry.color);
            g.text(font, trim(font, entry.name, WIDTH - 79), panelX + 30, y + 4, 0xFFE9F2FF);
            g.text(font, trim(font, entry.detail, WIDTH - 79), panelX + 30, y + 15, 0xFF7894AA);
            if (entry.id.equals(followedId)) {
                g.text(font, "●", panelX + WIDTH - 20, y + 8, 0xFF55FF88);
            }
        }
        if (visible.isEmpty()) {
            g.text(font, Component.translatable("screen.voxymap.objects.empty"), panelX + 14, listY + 8, 0xFF7894AA);
        }

        int detailsY = panelY + panelH - detailsH;
        g.fill(panelX + 8, detailsY, panelX + WIDTH - 8, detailsY + 1, 0x553BA4FF);
        if (selected == null) {
            g.text(font, Component.translatable("screen.voxymap.objects.select"), panelX + 12, detailsY + 8, 0xFF7894AA);
            return;
        }
        g.text(font, trim(font, selected.name, WIDTH - 28), panelX + 12, detailsY + 7, 0xFFFFFFFF);
        g.text(font, trim(font, selected.detail, WIDTH - 28), panelX + 12, detailsY + 19, 0xFF9FD8FF);
        int buttonY = detailsY + 40;
        drawActionButton(g, font, panelX + 9, buttonY, 51, "screen.voxymap.objects.center", true);
        drawActionButton(g, font, panelX + 63, buttonY, 51, "screen.voxymap.objects.follow", selected.followable);
        drawActionButton(g, font, panelX + 117, buttonY, 51, "screen.voxymap.objects.edit", selected.editable);
        drawActionButton(g, font, panelX + 171, buttonY, 72, "screen.voxymap.objects.delete",
                selected.editable && selected.kind != Kind.SCHEMATIC);
    }

    Action click(double mouseX, double mouseY, int button) {
        if (inside(mouseX, mouseY, toggleX, toggleY, toggleW, 22)) {
            if (button == InputConstants.MOUSE_BUTTON_LEFT) toggle();
            return Action.consumed();
        }
        if (!open || !inside(mouseX, mouseY, panelX, panelY, WIDTH, panelH)) return null;
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return Action.consumed();
        int searchY = panelY + 27;
        if (inside(mouseX, mouseY, panelX + 8, searchY, WIDTH - 16, 20)) {
            searchFocused = true;
            return Action.consumed();
        }
        searchFocused = false;
        int filterY = searchY + 26;
        if (inside(mouseX, mouseY, panelX + 8, filterY, WIDTH - 16, 19)) {
            filter = filter.next();
            scroll = 0;
            return new Action(ActionType.REFILTER, null);
        }
        if (mouseY >= listY && mouseY < listY + listH) {
            int index = scroll + (int) ((mouseY - listY) / ROW_H);
            if (index >= 0 && index < visible.size()) {
                Entry entry = visible.get(index);
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
            int buttonY = panelY + panelH - 28;
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

    private static void drawPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, int bg, int accent) {
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y, x + 2, y + h, accent);
        g.fill(x, y, x + w, y + 1, 0x33FFFFFF);
    }

    private static void drawActionButton(GuiGraphicsExtractor g, Font font, int x, int y, int w, String key, boolean enabled) {
        g.fill(x, y, x + w, y + BUTTON_H, enabled ? 0xDD102A38 : 0x88202028);
        String text = Component.translatable(key).getString();
        g.centeredText(font, trim(font, text, w - 6), x + w / 2, y + 6, enabled ? 0xFFE9F2FF : 0xFF66717A);
    }

    private static String trim(Font font, String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        String suffix = "…";
        int end = value.length();
        while (end > 0 && font.width(value.substring(0, end) + suffix) > maxWidth) end--;
        return value.substring(0, end) + suffix;
    }

    private static String kindGlyph(Kind kind) {
        return switch (kind) {
            case PLAYER -> "◆";
            case WAYPOINT -> "◇";
            case TERRITORY -> "□";
            case SCHEMATIC -> "▣";
        };
    }

    enum Kind { PLAYER, WAYPOINT, TERRITORY, SCHEMATIC }

    enum ActionType { CONSUMED, REFILTER, SELECT, CENTER, FOLLOW, EDIT, DELETE }

    record Entry(String id, Kind kind, String name, String detail, double x, double y, double z,
                 int color, boolean followable, boolean editable) {
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

        Filter next(boolean schematicsInstalled) {
            Filter[] values = values();
            Filter candidate = values[(ordinal() + 1) % values.length];
            return candidate == SCHEMATICS && !schematicsInstalled ? values[(candidate.ordinal() + 1) % values.length]
                    : candidate;
        }
    }
}
