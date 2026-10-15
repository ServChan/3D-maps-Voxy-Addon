package com.voxymap.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.voxymap.client.map.DimensionLinks;
import com.voxymap.client.map.WaypointChatShare;
import com.voxymap.client.map.WaypointDisplay;
import com.voxymap.client.map.WaypointLabels;
import com.voxymap.client.map.WaypointManager;
import com.voxymap.client.map.WaypointPrefs;
import com.voxymap.client.map.WaypointText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class WaypointListScreen extends Screen {
    private static final int TITLE_BAR_HEIGHT = 28;
    private static final int TOOLBAR_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 36;
    private static final int ROW_HEIGHT = 30;
    private static final int CONTROL_HEIGHT = 20;
    private static final int MAX_CONTENT_WIDTH = 520;
    private static final int SIDE_GUTTER = 16;
    private static final int SCROLL_STEP = 30;
    private static final int ICON_SIZE = 16;
    private static final int ACCENT = 0xFF3BA4FF;
    private static final int ACCENT_LIGHT = 0xFF7DEBFF;
    private static final int MUTED = 0xFF9AA7B8;

    public enum Sort {
        DISTANCE("screen.voxymap.waypoints.sort.distance"),
        NAME("screen.voxymap.waypoints.sort.name");

        private final String translationKey;

        Sort(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private record Listed(WaypointManager.Waypoint original, WaypointManager.Waypoint local, String worldId,
                          WaypointText.Dimension source) {
    }

    private record Row(Listed listed, int top, Button edit, Button share, Button visibility, Button delete) {
        WaypointManager.Waypoint waypoint() {
            return listed.local();
        }
    }

    private static Sort sort = Sort.DISTANCE;

    private final Screen parent;
    private final String worldId;
    private final List<Row> rows = new ArrayList<>();
    private EditBox searchBox;
    private Button sortButton;
    private String query = "";
    private String pendingDeleteId;
    private Component status;
    private int contentLeft;
    private int contentWidth;
    private int viewportTop;
    private int viewportBottom;
    private int contentHeight;
    private int scroll;
    private int totalWaypoints;

    public WaypointListScreen(Screen parent, String worldId) {
        super(Component.translatable("screen.voxymap.waypoints.title"));
        this.parent = parent;
        this.worldId = worldId;
    }

    @Override
    protected void init() {
        rows.clear();
        contentWidth = Math.min(MAX_CONTENT_WIDTH, width - SIDE_GUTTER * 2);
        contentLeft = (width - contentWidth) / 2;
        viewportTop = TITLE_BAR_HEIGHT + TOOLBAR_HEIGHT + 4;
        viewportBottom = height - FOOTER_HEIGHT;

        int toolbarY = TITLE_BAR_HEIGHT + 4;
        int sortW = Math.min(110, contentWidth / 5);
        int addW = Math.min(110, contentWidth / 5);
        int importW = Math.min(90, contentWidth / 6);
        int searchW = contentWidth - sortW - addW - importW - 12;
        searchBox = addRenderableWidget(new EditBox(font, contentLeft, toolbarY, searchW, CONTROL_HEIGHT,
                Component.translatable("screen.voxymap.objects.search")));
        searchBox.setHint(Component.translatable("screen.voxymap.objects.search"));
        searchBox.setValue(query);
        searchBox.setResponder(value -> {
            if (!value.equals(query)) {
                query = value;
                rebuildRowsKeepingSearchFocus();
            }
        });
        sortButton = addRenderableWidget(Button.builder(sortLabel(), pressed -> {
            sort = sort == Sort.DISTANCE ? Sort.NAME : Sort.DISTANCE;
            rebuildRowsKeepingSearchFocus();
        }).bounds(contentLeft + searchW + 4, toolbarY, sortW, CONTROL_HEIGHT).build());
        Button add = addRenderableWidget(Button.builder(Component.translatable("screen.voxymap.waypoints.add_here"),
                pressed -> openAddHere()).bounds(contentLeft + searchW + sortW + 8, toolbarY, addW, CONTROL_HEIGHT).build());
        Button importButton = addRenderableWidget(Button.builder(Component.translatable("screen.voxymap.import.open"),
                pressed -> {
                    if (minecraft != null) {
                        minecraft.setScreenAndShow(new WaypointImportScreen(this, worldId));
                    }
                }).bounds(contentLeft + contentWidth - importW, toolbarY, importW, CONTROL_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("screen.voxymap.import.tooltip"))).build());
        importButton.active = worldId != null;
        add.active = worldId != null && minecraft != null && minecraft.player != null;

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), pressed -> onClose())
                .bounds(width / 2 - 100, height - 28, 200, CONTROL_HEIGHT).build());

        List<Listed> waypoints = new ArrayList<>();
        if (worldId != null) {
            for (WaypointManager.Waypoint waypoint : WaypointManager.forWorld(worldId)) {
                waypoints.add(new Listed(waypoint, waypoint, worldId, null));
            }
            if (minecraft != null) {
                for (DimensionLinks.Foreign foreign : DimensionLinks.foreignWaypoints(minecraft)) {
                    waypoints.add(new Listed(foreign.original(), foreign.local(), foreign.sourceWorldId(), foreign.source()));
                }
            }
        }
        totalWaypoints = waypoints.size();
        int buttonW = Math.max(40, Math.min(60, (contentWidth / 2 - 12) / 4));
        int buttonsLeft = contentLeft + contentWidth - (buttonW * 4 + 12) - 4;
        int top = 0;
        for (Listed listed : sorted(filtered(waypoints))) {
            boolean editable = WaypointManager.editable(listed.worldId(), listed.original());
            Button edit = addRenderableWidget(Button.builder(Component.translatable("screen.voxymap.objects.edit"),
                            pressed -> openEdit(listed))
                    .bounds(buttonsLeft, 0, buttonW, CONTROL_HEIGHT).build());
            Button share = addRenderableWidget(Button.builder(Component.translatable("screen.voxymap.waypoints.share"),
                            pressed -> share(listed))
                    .bounds(buttonsLeft + buttonW + 4, 0, buttonW, CONTROL_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable("screen.voxymap.waypoints.share.tooltip"))).build());
            Button visibility = addRenderableWidget(Button.builder(visibilityLabel(listed.original()),
                            pressed -> cycleVisibility(listed, pressed))
                    .bounds(buttonsLeft + (buttonW + 4) * 2, 0, buttonW, CONTROL_HEIGHT)
                    .tooltip(visibilityTooltip(listed.original())).build());
            Button delete = addRenderableWidget(Button.builder(deleteLabel(listed.original()), pressed -> delete(listed))
                    .bounds(buttonsLeft + (buttonW + 4) * 3, 0, buttonW, CONTROL_HEIGHT).build());
            edit.active = editable;
            delete.active = editable;
            if (!editable) {
                Tooltip readOnly = Tooltip.create(Component.translatable("message.voxymap.waypoint.read_only"));
                edit.setTooltip(readOnly);
                delete.setTooltip(readOnly);
            }
            rows.add(new Row(listed, top, edit, share, visibility, delete));
            top += ROW_HEIGHT;
        }
        contentHeight = top;
        scroll = clampScroll(scroll);
        layoutRows();
    }

    private void rebuildRowsKeepingSearchFocus() {
        boolean sortFocused = sortButton != null && getFocused() == sortButton;
        int cursor = searchBox == null ? -1 : searchBox.getCursorPosition();
        rebuildWidgets();
        if (sortFocused) {
            setFocused(sortButton);
            return;
        }
        setFocused(searchBox);
        if (cursor >= 0) {
            searchBox.setCursorPosition(Math.min(cursor, searchBox.getValue().length()));
        }
    }

    private List<Listed> filtered(List<Listed> waypoints) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return waypoints;
        }
        return waypoints.stream().filter(listed -> listed.local().name().toLowerCase(Locale.ROOT).contains(needle)).toList();
    }

    private List<Listed> sorted(List<Listed> waypoints) {
        List<Listed> copy = new ArrayList<>(waypoints);
        Player player = minecraft == null ? null : minecraft.player;
        if (sort == Sort.DISTANCE && player != null) {
            copy.sort(Comparator.comparingDouble(listed -> distance(player, listed.local())));
        } else {
            copy.sort(Comparator.comparing(listed -> listed.local().name().toLowerCase(Locale.ROOT)));
        }
        return copy;
    }

    private static double distance(Player player, WaypointManager.Waypoint waypoint) {
        return Math.sqrt(player.distanceToSqr(waypoint.x(), waypoint.y(), waypoint.z()));
    }

    private Component sortLabel() {
        return Component.translatable(sort.translationKey);
    }

    private Component deleteLabel(WaypointManager.Waypoint waypoint) {
        return waypoint.id().equals(pendingDeleteId)
                ? Component.translatable("screen.voxymap.waypoints.confirm_delete").withStyle(ChatFormatting.RED)
                : Component.translatable("screen.voxymap.objects.delete");
    }

    private Component visibilityLabel(WaypointManager.Waypoint waypoint) {
        return Component.translatable(WaypointPrefs.display(waypoint.id()).shortTranslationKey());
    }

    private Tooltip visibilityTooltip(WaypointManager.Waypoint waypoint) {
        WaypointDisplay display = WaypointPrefs.display(waypoint.id());
        return Tooltip.create(Component.translatable("screen.voxymap.waypoint.display",
                        Component.translatable(display.translationKey()))
                .append("\n").append(Component.translatable(display.tooltipTranslationKey()))
                .append("\n").append(Component.translatable("screen.voxymap.waypoints.display_cycle")));
    }

    private void cycleVisibility(Listed listed, Button button) {
        String id = listed.original().id();
        WaypointPrefs.setDisplay(id, WaypointPrefs.display(id).next());
        button.setMessage(visibilityLabel(listed.original()));
        button.setTooltip(visibilityTooltip(listed.original()));
    }

    private void delete(Listed listed) {
        WaypointManager.Waypoint waypoint = listed.original();
        if (!waypoint.id().equals(pendingDeleteId)) {
            pendingDeleteId = waypoint.id();
            for (Row row : rows) {
                row.delete().setMessage(deleteLabel(row.listed().original()));
            }
            return;
        }
        pendingDeleteId = null;
        WaypointManager.remove(listed.worldId(), waypoint.id());
        status = Component.translatable("screen.voxymap.waypoints.deleted", waypoint.name());
        rebuildWidgets();
    }

    private void share(Listed listed) {
        WaypointManager.Waypoint waypoint = listed.original();
        boolean sent = listed.source() == null ? WaypointChatShare.send(minecraft, waypoint)
                : WaypointChatShare.send(minecraft, waypoint, listed.source());
        status = sent
                ? Component.translatable("screen.voxymap.waypoints.shared", waypoint.name())
                : Component.translatable("screen.voxymap.waypoints.share_failed");
    }

    private void openEdit(Listed listed) {
        if (minecraft != null) {
            minecraft.setScreenAndShow(WaypointEditScreen.edit(this, listed.worldId(), listed.original()));
        }
    }

    private void openAddHere() {
        if (minecraft == null || minecraft.player == null || worldId == null) {
            return;
        }
        Player player = minecraft.player;
        minecraft.setScreenAndShow(WaypointEditScreen.create(this, worldId, player.getX(), player.getY(), player.getZ()));
    }

    private int viewportHeight() {
        return Math.max(1, viewportBottom - viewportTop);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - viewportHeight());
    }

    private int clampScroll(int requested) {
        return Math.max(0, Math.min(maxScroll(), requested));
    }

    private void layoutRows() {
        for (Row row : rows) {
            int y = viewportTop + row.top() - scroll + (ROW_HEIGHT - CONTROL_HEIGHT) / 2;
            boolean inside = y >= viewportTop && y + CONTROL_HEIGHT <= viewportBottom;
            for (Button button : List.of(row.edit(), row.share(), row.visibility(), row.delete())) {
                button.setY(y);
                button.visible = inside;
            }
        }
    }

    private void scrollTo(int requested) {
        scroll = clampScroll(requested);
        layoutRows();
    }

    @Override
    public void setFocused(GuiEventListener focused) {
        super.setFocused(focused);
        if (focused == null) {
            return;
        }
        for (Row row : rows) {
            if (row.edit() == focused || row.share() == focused || row.visibility() == focused || row.delete() == focused) {
                if (row.top() < scroll) {
                    scrollTo(row.top());
                } else if (row.top() + ROW_HEIGHT > scroll + viewportHeight()) {
                    scrollTo(row.top() + ROW_HEIGHT - viewportHeight());
                }
                return;
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, width, height, 0xF00D141F);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, width, TITLE_BAR_HEIGHT, 0xFF182638);
        graphics.centeredText(font, title, width / 2, 9, 0xFFFFFFFF);

        graphics.enableScissor(0, viewportTop, width, viewportBottom);
        Player player = minecraft == null ? null : minecraft.player;
        for (Row row : rows) {
            int y = viewportTop + row.top() - scroll;
            if (y + ROW_HEIGHT < viewportTop || y > viewportBottom) {
                continue;
            }
            drawRow(graphics, row, y, player);
        }
        if (rows.isEmpty()) {
            drawEmptyState(graphics);
        }
        graphics.disableScissor();

        int maxScroll = maxScroll();
        if (maxScroll > 0) {
            int trackX = contentLeft + contentWidth + 6;
            int trackH = viewportHeight();
            int thumbH = Math.max(18, trackH * trackH / Math.max(1, contentHeight));
            int thumbY = viewportTop + (trackH - thumbH) * scroll / maxScroll;
            graphics.fill(trackX, viewportTop, trackX + 2, viewportTop + trackH, 0x553BA4FF);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, ACCENT_LIGHT);
        }

        if (status != null) {
            graphics.text(font, status, contentLeft, height - FOOTER_HEIGHT + 4, ACCENT_LIGHT);
        }
        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
    }

    private void drawRow(GuiGraphicsExtractor graphics, Row row, int y, Player player) {
        WaypointManager.Waypoint waypoint = row.waypoint();
        graphics.fill(contentLeft, y, contentLeft + contentWidth, y + ROW_HEIGHT - 2, 0x66102034);
        graphics.fill(contentLeft, y, contentLeft + 2, y + ROW_HEIGHT - 2, ACCENT);
        int iconX = contentLeft + 8;
        int iconY = y + (ROW_HEIGHT - 2 - ICON_SIZE) / 2;
        boolean foreign = row.listed().source() != null;
        WaypointIcons.draw(graphics, font, iconX, iconY, ICON_SIZE, waypoint.color(), waypoint.initial(),
                foreign ? WaypointIcons.OTHER_DIMENSION_ALPHA : 0xFF);

        int textX = iconX + ICON_SIZE + 8;
        int textRight = row.edit().getX() - 8;
        boolean hidden = WaypointPrefs.hidden(row.listed().original().id());
        String tag = hidden ? Component.translatable("screen.voxymap.waypoints.tag.hidden").getString()
                : foreign ? WaypointLabels.dimensionName(row.listed().source())
                : waypoint.temporary() ? Component.translatable("screen.voxymap.waypoints.tag.temporary").getString()
                : waypoint.shared() ? Component.translatable("screen.voxymap.waypoints.tag.shared").getString() : "";
        int tagWidth = tag.isEmpty() ? 0 : font.width(tag) + 6;
        graphics.text(font, fit(waypoint.name(), textRight - textX - tagWidth), textX, y + 5, hidden ? MUTED : 0xFFFFFFFF);
        if (!tag.isEmpty()) {
            int nameWidth = font.width(fit(waypoint.name(), textRight - textX - tagWidth));
            graphics.text(font, tag, textX + nameWidth + 6, y + 5,
                    hidden ? MUTED : foreign ? 0xFFFF8A5C : waypoint.temporary() ? 0xFFFFC94D : 0xFF55FF55);
        }
        String detail = (int) Math.floor(waypoint.x()) + ", " + (int) Math.floor(waypoint.y()) + ", "
                + (int) Math.floor(waypoint.z());
        if (player != null) {
            detail += "  ·  " + WaypointLabels.distanceText(distance(player, waypoint));
        }
        graphics.text(font, fit(detail, textRight - textX), textX, y + 16, MUTED);
    }

    private void drawEmptyState(GuiGraphicsExtractor graphics) {
        int centerY = (viewportTop + viewportBottom) / 2;
        boolean filtering = totalWaypoints > 0;
        Component headline = Component.translatable(filtering
                ? "screen.voxymap.objects.empty" : "screen.voxymap.waypoints.empty");
        graphics.centeredText(font, headline, width / 2, centerY - 10, 0xFFFFFFFF);
        if (!filtering) {
            graphics.centeredText(font, Component.translatable("screen.voxymap.waypoints.empty_hint"),
                    width / 2, centerY + 4, MUTED);
        }
    }

    private String fit(String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0 || maxScroll() == 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        scrollTo(scroll - (int) Math.signum(scrollY) * SCROLL_STEP);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_PAGEDOWN) {
            scrollTo(scroll + viewportHeight());
            return true;
        }
        if (key == InputConstants.KEY_PAGEUP) {
            scrollTo(scroll - viewportHeight());
            return true;
        }
        if (key == InputConstants.KEY_TAB) {
            for (Row row : rows) {
                for (Button button : List.of(row.edit(), row.share(), row.visibility(), row.delete())) {
                    button.visible = true;
                }
            }
            boolean handled = super.keyPressed(event);
            layoutRows();
            return handled;
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
