package com.voxymap.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.voxymap.client.gui.ui.UiButton;
import com.voxymap.client.gui.ui.UiCycler;
import com.voxymap.client.gui.ui.UiDraw;
import com.voxymap.client.gui.ui.UiIcons;
import com.voxymap.client.gui.ui.UiSlider;
import com.voxymap.client.gui.ui.UiTextField;
import com.voxymap.client.gui.ui.UiTheme;
import com.voxymap.client.gui.ui.UiToggle;
import com.voxymap.client.map.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class SettingsView {
    public interface Children {
        void add(AbstractWidget widget);

        void remove(AbstractWidget widget);

        void focus(GuiEventListener widget);
    }

    private static final int PADDING = UiTheme.SPACE_M;
    private static final int HEADER_HEIGHT = 16;
    private static final int RESET_SIZE = 14;
    private static final int DESCRIPTION_HEIGHT = 24;
    private static final int SCROLL_STEP = UiTheme.ROW_HEIGHT;
    private static final int MIN_CATEGORY_LAYOUT_WIDTH = 300;

    private enum RowKind { HEADER, SETTING }

    private static final class Row {
        final RowKind kind;
        final SettingsCatalog.Group group;
        final SettingsCatalog.Entry entry;
        final SettingsModel.Control control;
        final SettingsModel.Reset reset;
        AbstractWidget widget;
        UiButton resetButton;
        int top;
        int height;

        Row(SettingsCatalog.Group group) {
            this.kind = RowKind.HEADER;
            this.group = group;
            this.entry = null;
            this.control = null;
            this.reset = null;
            this.height = HEADER_HEIGHT;
        }

        Row(SettingsCatalog.Entry entry, SettingsModel.Control control, SettingsModel.Reset reset) {
            this.kind = RowKind.SETTING;
            this.group = entry.group();
            this.entry = entry;
            this.control = control;
            this.reset = reset;
            this.height = UiTheme.ROW_HEIGHT;
        }
    }

    private final SettingsModel.Host host;
    private final Children children;
    private final Supplier<List<SettingsCatalog.Section>> sections;
    private final List<Row> rows = new ArrayList<>();
    private final List<UiButton> categoryButtons = new ArrayList<>();
    private final List<SettingsCatalog.Group> categoryGroups = new ArrayList<>();
    private UiTextField search;
    private UiButton resetAll;
    private UiCycler categoryCycler;
    private SettingsCatalog.Group selected;
    private String query = "";
    private int x;
    private int y;
    private int width;
    private int height;
    private int categoryWidth;
    private int rowsX;
    private int rowsWidth;
    private int viewportTop;
    private int viewportBottom;
    private int contentHeight;
    private int scroll;
    private int controlWidth;
    private AbstractWidget dragging;
    private SettingsCatalog.Entry described;
    private boolean built;
    private GuiEventListener ensuredFocus;
    private boolean slimeSeedMissingAtBuild;
    private List<AbstractWidget> trailingFocus = List.of();

    public SettingsView(SettingsModel.Host host, Children children, Supplier<List<SettingsCatalog.Section>> sections) {
        this.host = host;
        this.children = children;
        this.sections = sections;
    }

    public void layout(int x, int y, int width, int height) {
        if (built && this.x == x && this.y == y && this.width == width && this.height == height) {
            return;
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        rebuild();
    }

    public boolean contains(double mouseX, double mouseY) {
        return built && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    private Font font() {
        return Minecraft.getInstance().font;
    }

    private void rebuild() {
        commitTextFields();
        clearWidgets();
        built = true;
        Font font = font();
        List<SettingsCatalog.Section> available = sections.get();
        if (selected == null || available.stream().noneMatch(section -> section.group() == selected)) {
            selected = available.isEmpty() ? null : available.getFirst().group();
        }
        categoryWidth = width >= MIN_CATEGORY_LAYOUT_WIDTH ? Math.min(132, Math.max(96, width * 3 / 10)) : 0;

        String resetLabel = Component.translatable("screen.voxymap.settings.reset_all").getString();
        int resetWidth = font.width(resetLabel) + UiTheme.ICON_SIZE + UiTheme.SPACE_XL + UiTheme.SPACE_M;
        int headerY = y + PADDING;
        String previousQuery = query;
        search = new UiTextField(font, x + PADDING, headerY, Math.max(60, width - PADDING * 3 - resetWidth),
                UiTheme.CONTROL_HEIGHT, Component.translatable("screen.voxymap.settings.search"), UiIcons.Icon.SEARCH);
        search.setHint(Component.translatable("screen.voxymap.settings.search").withStyle(Style.EMPTY.withColor(UiTheme.TEXT_MUTED)));
        search.setMaxLength(48);
        search.setValue(previousQuery);
        search.setResponder(value -> {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (!normalized.equals(query)) {
                query = normalized;
                scroll = 0;
                rebuildRows();
            }
        });
        add(search);
        resetAll = new UiButton(x + width - PADDING - resetWidth, headerY, resetWidth, UiTheme.CONTROL_HEIGHT,
                Component.translatable("screen.voxymap.settings.reset_all"), UiButton.Variant.GHOST,
                button -> SettingsModel.resetAll(allEntries())).icon(UiIcons.Icon.RESET);
        add(resetAll);

        int bodyTop = headerY + UiTheme.CONTROL_HEIGHT + PADDING;
        if (categoryWidth > 0) {
            int buttonY = bodyTop;
            for (SettingsCatalog.Section section : available) {
                SettingsCatalog.Group group = section.group();
                UiButton button = new UiButton(x + PADDING, buttonY, categoryWidth - PADDING, UiTheme.CONTROL_HEIGHT,
                        Component.translatable(group.titleKey()), UiButton.Variant.GHOST, pressed -> selectGroup(group))
                        .icon(icon(group)).align(UiButton.Align.START).selected(group == selected);
                categoryButtons.add(button);
                categoryGroups.add(group);
                add(button);
                buttonY += UiTheme.CONTROL_HEIGHT + UiTheme.SPACE_XS;
            }
            rowsX = x + categoryWidth + PADDING;
            rowsWidth = width - categoryWidth - PADDING * 2;
            viewportTop = bodyTop;
        } else {
            rowsX = x + PADDING;
            rowsWidth = width - PADDING * 2;
            categoryCycler = new UiCycler(rowsX, bodyTop, rowsWidth, UiTheme.CONTROL_HEIGHT,
                    Component.translatable("screen.voxymap.settings.category"),
                    () -> selected == null ? Component.empty() : Component.translatable(selected.titleKey()),
                    this::stepGroup);
            add(categoryCycler);
            viewportTop = bodyTop + UiTheme.CONTROL_HEIGHT + PADDING;
        }
        viewportBottom = y + height - PADDING - DESCRIPTION_HEIGHT;
        controlWidth = Math.max(96, Math.min(156, rowsWidth * 9 / 20));
        rebuildRows();
    }

    private static UiIcons.Icon icon(SettingsCatalog.Group group) {
        return switch (group) {
            case GENERAL -> UiIcons.Icon.SLIDERS;
            case LAYERS -> UiIcons.Icon.LAYERS;
            case WAYPOINT_LOOK -> UiIcons.Icon.PIN;
            case MINIMAP -> UiIcons.Icon.MINIMAP;
            case INFO -> UiIcons.Icon.INFO;
            case RADAR -> UiIcons.Icon.RADAR;
            case OVERLAYS -> UiIcons.Icon.GRID;
            case SYNC -> UiIcons.Icon.SYNC;
            case DIAGNOSTICS -> UiIcons.Icon.PULSE;
        };
    }

    private void selectGroup(SettingsCatalog.Group group) {
        selected = group;
        scroll = 0;
        if (!query.isEmpty()) {
            search.setValue("");
        }
        for (int i = 0; i < categoryButtons.size(); i++) {
            categoryButtons.get(i).selected(categoryGroups.get(i) == group);
        }
        rebuildRows();
    }

    private void stepGroup(int direction) {
        List<SettingsCatalog.Section> available = sections.get();
        if (available.isEmpty()) {
            return;
        }
        int index = 0;
        for (int i = 0; i < available.size(); i++) {
            if (available.get(i).group() == selected) {
                index = i;
            }
        }
        selectGroup(available.get(Math.floorMod(index + direction, available.size())).group());
    }

    private List<SettingsCatalog.Entry> allEntries() {
        List<SettingsCatalog.Entry> entries = new ArrayList<>();
        for (SettingsCatalog.Section section : sections.get()) {
            entries.addAll(section.entries());
        }
        return entries;
    }

    private void rebuildRows() {
        commitTextFields();
        for (Row row : rows) {
            if (row.widget != null) {
                remove(row.widget);
            }
            if (row.resetButton != null) {
                remove(row.resetButton);
            }
        }
        rows.clear();
        slimeSeedMissingAtBuild = SettingsValues.slimeSeedMissing();
        Font font = font();
        boolean searching = !query.isEmpty();
        int top = 0;
        for (SettingsCatalog.Section section : sections.get()) {
            if (!searching && section.group() != selected) {
                continue;
            }
            List<SettingsCatalog.Entry> matches = new ArrayList<>();
            for (SettingsCatalog.Entry entry : section.entries()) {
                if (!searching || Component.translatable(entry.labelKey()).getString().toLowerCase(Locale.ROOT).contains(query)) {
                    matches.add(entry);
                }
            }
            if (matches.isEmpty()) {
                continue;
            }
            if (searching) {
                Row header = new Row(section.group());
                header.top = top;
                rows.add(header);
                top += header.height;
            }
            for (SettingsCatalog.Entry entry : matches) {
                Row row = new Row(entry, SettingsModel.control(entry, host), SettingsModel.reset(entry));
                row.top = top;
                row.widget = createWidget(row, font);
                if (row.widget != null) {
                    add(row.widget);
                }
                if (row.reset != null) {
                    SettingsModel.Reset reset = row.reset;
                    row.resetButton = UiButton.iconButton(0, 0, RESET_SIZE, UiIcons.Icon.RESET,
                            Component.translatable("screen.voxymap.settings.reset_one",
                                    Component.translatable(entry.labelKey())), button -> reset.reset().run());
                    add(row.resetButton);
                }
                rows.add(row);
                top += row.height;
            }
        }
        contentHeight = top;
        scroll = clampScroll(scroll);
    }

    private AbstractWidget createWidget(Row row, Font font) {
        Component label = Component.translatable(row.entry.labelKey());
        int h = UiTheme.CONTROL_HEIGHT;
        return switch (row.control) {
            case SettingsModel.Toggle toggle -> new UiToggle(0, 0, controlWidth, h, label, toggle.value(), toggle.flip());
            case SettingsModel.Cycle cycle -> new UiCycler(0, 0, controlWidth, h, label, cycle.value(), cycle.step());
            case SettingsModel.Slider slider -> new UiSlider(0, 0, controlWidth, h, label, slider.fraction(),
                    slider.value(), slider.apply()).keyStep(slider.keyStep());
            case SettingsModel.Text text -> {
                UiTextField field = new UiTextField(font, 0, 0, controlWidth, h, label, null);
                field.setMaxLength(text.maxLength());
                field.setValue(text.value().get());
                field.setHint(Component.translatable("screen.voxymap.settings.sync.not_set")
                        .withStyle(Style.EMPTY.withColor(UiTheme.TEXT_MUTED)));
                if (text.secret()) {
                    field.addFormatter((value, offset) -> FormattedCharSequence.forward("*".repeat(value.length()), Style.EMPTY));
                }
                yield field;
            }
            case SettingsModel.Action action -> new UiButton(0, 0, controlWidth, h, action.label().get(),
                    UiButton.Variant.SECONDARY, button -> action.run().run()).label(action.label());
            case SettingsModel.Status status -> null;
        };
    }

    private void add(AbstractWidget widget) {
        children.add(widget);
    }

    private void remove(AbstractWidget widget) {
        children.remove(widget);
    }

    private void clearWidgets() {
        for (Row row : rows) {
            if (row.widget != null) {
                remove(row.widget);
            }
            if (row.resetButton != null) {
                remove(row.resetButton);
            }
        }
        rows.clear();
        for (UiButton button : categoryButtons) {
            remove(button);
        }
        categoryButtons.clear();
        categoryGroups.clear();
        if (search != null) {
            remove(search);
            search = null;
        }
        if (resetAll != null) {
            remove(resetAll);
            resetAll = null;
        }
        if (categoryCycler != null) {
            remove(categoryCycler);
            categoryCycler = null;
        }
        dragging = null;
    }

    public void close() {
        commitTextFields();
        clearWidgets();
        built = false;
    }

    private void commitTextFields() {
        for (Row row : rows) {
            if (row.control instanceof SettingsModel.Text text && row.widget instanceof UiTextField field) {
                String value = field.getValue().trim();
                if (!value.equals(text.value().get())) {
                    text.commit().accept(value);
                }
            }
        }
    }

    private int viewportHeight() {
        return Math.max(1, viewportBottom - viewportTop);
    }

    private int clampScroll(int requested) {
        return Math.max(0, Math.min(Math.max(0, contentHeight - viewportHeight()), requested));
    }

    private void placeRows() {
        for (Row row : rows) {
            int rowY = viewportTop + row.top - scroll;
            boolean shown = rowY >= viewportTop - 1 && rowY + row.height <= viewportBottom + 1;
            if (row.widget != null) {
                int widgetX = rowsX + rowsWidth - controlWidth;
                int widgetY = rowY + (row.height - UiTheme.CONTROL_HEIGHT) / 2;
                if (row.widget instanceof UiTextField field) {
                    field.setFrame(widgetX, widgetY, controlWidth, UiTheme.CONTROL_HEIGHT);
                } else {
                    row.widget.setX(widgetX);
                    row.widget.setY(widgetY);
                }
                row.widget.visible = shown;
                if (row.widget instanceof UiSlider uiSlider && row.widget != dragging
                        && row.control instanceof SettingsModel.Slider slider) {
                    uiSlider.refresh(slider.fraction().getAsDouble());
                }
            }
            if (row.resetButton != null) {
                row.resetButton.setX(rowsX + rowsWidth - controlWidth - RESET_SIZE - UiTheme.SPACE_S);
                row.resetButton.setY(rowY + (row.height - RESET_SIZE) / 2);
                row.resetButton.visible = shown && !row.reset.isDefault().getAsBoolean();
            }
        }
    }

    private Row rowOf(GuiEventListener widget) {
        for (Row row : rows) {
            if (row.widget == widget || row.resetButton == widget) {
                return row;
            }
        }
        return null;
    }

    private void ensureVisible(Row row) {
        if (row.top < scroll) {
            scroll = clampScroll(row.top);
        } else if (row.top + row.height > scroll + viewportHeight()) {
            scroll = clampScroll(row.top + row.height - viewportHeight());
        }
    }

    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, GuiEventListener focused) {
        if (!built) {
            return;
        }
        if (SettingsValues.slimeSeedMissing() != slimeSeedMissingAtBuild) {
            rebuildRows();
        }
        Font font = font();
        Row focusedRow = rowOf(focused);
        if (focused != ensuredFocus) {
            ensuredFocus = focused;
            if (focusedRow != null && Minecraft.getInstance().getLastInputType().isKeyboard()) {
                ensureVisible(focusedRow);
            }
        }
        placeRows();
        UiDraw.panel(g, x, y, width, height, UiTheme.SURFACE);
        UiDraw.outline(g, x, y, width, height, UiTheme.BORDER);
        search.extractRenderState(g, mouseX, mouseY, delta);
        resetAll.extractRenderState(g, mouseX, mouseY, delta);
        if (categoryWidth > 0) {
            g.fill(x + categoryWidth, viewportTop, x + categoryWidth + 1, y + height - PADDING, UiTheme.BORDER);
            for (UiButton button : categoryButtons) {
                button.extractRenderState(g, mouseX, mouseY, delta);
            }
        } else if (categoryCycler != null) {
            categoryCycler.extractRenderState(g, mouseX, mouseY, delta);
        }

        described = null;
        g.enableScissor(rowsX - 3, viewportTop, rowsX + rowsWidth + 3, viewportBottom);
        for (Row row : rows) {
            int rowY = viewportTop + row.top - scroll;
            if (rowY + row.height < viewportTop || rowY > viewportBottom) {
                continue;
            }
            if (row.kind == RowKind.HEADER) {
                String title = Component.translatable(row.group.titleKey()).getString();
                g.text(font, title, rowsX, rowY + 5, UiTheme.ACCENT_BRIGHT);
                int lineX = rowsX + font.width(title) + UiTheme.SPACE_S + 2;
                if (lineX < rowsX + rowsWidth) {
                    g.fill(lineX, rowY + 9, rowsX + rowsWidth, rowY + 10, UiTheme.BORDER);
                }
                continue;
            }
            drawSettingRow(g, font, row, rowY, mouseX, mouseY, delta, focused);
        }
        g.disableScissor();
        drawScrollbar(g);
        drawDescription(g, font, focusedRow);
    }

    private void drawSettingRow(GuiGraphicsExtractor g, Font font, Row row, int rowY, int mouseX, int mouseY,
                                float delta, GuiEventListener focused) {
        boolean hovered = mouseX >= rowsX && mouseX < rowsX + rowsWidth && mouseY >= rowY && mouseY < rowY + row.height
                && mouseY >= viewportTop && mouseY < viewportBottom;
        if (hovered) {
            UiDraw.panel(g, rowsX - 2, rowY, rowsWidth + 4, row.height, UiTheme.ROW_HOVER);
            described = row.entry;
        }
        boolean changed = row.reset != null && !row.reset.isDefault().getAsBoolean();
        int labelX = rowsX + UiTheme.SPACE_S;
        if (changed) {
            g.fill(rowsX, rowY + row.height / 2 - 2, rowsX + 3, rowY + row.height / 2 + 2, UiTheme.WARNING);
            labelX += 2;
        }
        int labelLimit = rowsX + rowsWidth - controlWidth - RESET_SIZE - UiTheme.SPACE_M * 2 - labelX;
        UiDraw.text(g, font, Component.translatable(row.entry.labelKey()).getString(), labelX,
                UiDraw.textY(font, rowY, row.height), labelLimit, UiTheme.TEXT);
        if (row.widget != null) {
            row.widget.extractRenderState(g, mouseX, mouseY, delta);
        } else if (row.control instanceof SettingsModel.Status status) {
            String value = status.value().get().getString();
            int valueX = rowsX + rowsWidth - controlWidth;
            UiDraw.panel(g, valueX, rowY + (row.height - UiTheme.CONTROL_HEIGHT) / 2, controlWidth,
                    UiTheme.CONTROL_HEIGHT, UiTheme.SURFACE_SUNKEN);
            UiDraw.text(g, font, value, valueX + UiTheme.SPACE_M, UiDraw.textY(font, rowY, row.height),
                    controlWidth - UiTheme.SPACE_M * 2, status.color().get());
        }
        if (row.resetButton != null && row.resetButton.visible) {
            row.resetButton.extractRenderState(g, mouseX, mouseY, delta);
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor g) {
        int max = Math.max(0, contentHeight - viewportHeight());
        if (max <= 0) {
            return;
        }
        int trackX = rowsX + rowsWidth + 3;
        int trackHeight = viewportHeight();
        int thumbHeight = Math.max(16, trackHeight * trackHeight / Math.max(1, contentHeight));
        int thumbY = viewportTop + (trackHeight - thumbHeight) * scroll / max;
        g.fill(trackX, viewportTop, trackX + 2, viewportBottom, UiTheme.BORDER);
        g.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, UiTheme.ACCENT_BRIGHT);
    }

    private void drawDescription(GuiGraphicsExtractor g, Font font, Row focusedRow) {
        SettingsCatalog.Entry entry = described != null ? described : focusedRow != null ? focusedRow.entry : null;
        int top = viewportBottom + UiTheme.SPACE_S;
        UiDraw.divider(g, rowsX, top - 2, rowsWidth);
        String text = null;
        if (entry == SettingsCatalog.Entry.SYNC_STATUS) {
            String worldId = host.worldId();
            if (worldId != null && WaypointManager.syncStatus(worldId) == WaypointManager.SyncStatus.ERROR) {
                text = WaypointManager.syncErrorDetail(worldId);
            }
        }
        if (text == null && entry != null && Language.getInstance().has(entry.labelKey() + ".desc")) {
            text = Component.translatable(entry.labelKey() + ".desc").getString();
        }
        if (text == null && rows.isEmpty()) {
            text = Component.translatable("screen.voxymap.settings.search.empty").getString();
        }
        if (text == null) {
            text = Component.translatable("screen.voxymap.settings.hint").getString();
        }
        List<FormattedCharSequence> lines = font.split(Component.literal(text), rowsWidth);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.text(font, lines.get(i), rowsX, top + 2 + i * 10, UiTheme.TEXT_MUTED);
        }
    }

    private List<AbstractWidget> focusOrder() {
        List<AbstractWidget> order = new ArrayList<>();
        order.add(search);
        order.add(resetAll);
        if (categoryCycler != null) {
            order.add(categoryCycler);
        }
        order.addAll(categoryButtons);
        for (Row row : rows) {
            if (row.resetButton != null && !row.reset.isDefault().getAsBoolean()) {
                order.add(row.resetButton);
            }
            if (row.widget != null) {
                order.add(row.widget);
            }
        }
        order.addAll(trailingFocus);
        return order;
    }

    public void setTrailingFocus(List<AbstractWidget> widgets) {
        trailingFocus = List.copyOf(widgets);
    }

    public boolean owns(GuiEventListener widget) {
        if (widget == null) {
            return false;
        }
        return widget == search || widget == resetAll || widget == categoryCycler
                || categoryButtons.contains(widget) || rowOf(widget) != null;
    }

    public boolean typing(GuiEventListener focused) {
        return focused instanceof UiTextField && owns(focused);
    }

    public boolean keyPressed(KeyEvent event, GuiEventListener focused) {
        if (!built) {
            return false;
        }
        int key = event.key();
        if (key == InputConstants.KEY_TAB) {
            List<AbstractWidget> order = focusOrder();
            int index = order.indexOf(focused);
            Row focusedRow = index < 0 ? rowOf(focused) : null;
            if (focusedRow != null && focusedRow.widget != null) {
                index = order.indexOf(focusedRow.widget);
            }
            int next = index < 0 ? 0 : Math.floorMod(index + (event.hasShiftDown() ? -1 : 1), order.size());
            AbstractWidget target = order.get(next);
            Row row = rowOf(target);
            if (row != null) {
                ensureVisible(row);
                placeRows();
            }
            children.focus(target);
            return true;
        }
        if (!owns(focused)) {
            if (key == InputConstants.KEY_PAGEDOWN || key == InputConstants.KEY_PAGEUP) {
                scroll = clampScroll(scroll + (key == InputConstants.KEY_PAGEDOWN ? 1 : -1) * viewportHeight());
                return true;
            }
            return false;
        }
        if (focused instanceof UiTextField field && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)) {
            if (field != search) {
                commitTextFields();
            }
            children.focus(null);
            return true;
        }
        if (key == InputConstants.KEY_PAGEDOWN || key == InputConstants.KEY_PAGEUP) {
            scroll = clampScroll(scroll + (key == InputConstants.KEY_PAGEDOWN ? 1 : -1) * viewportHeight());
            return true;
        }
        if (focused instanceof UiTextField) {
            focused.keyPressed(event);
            return true;
        }
        return focused.keyPressed(event);
    }

    public boolean charTyped(CharacterEvent event, GuiEventListener focused) {
        return owns(focused) && focused.charTyped(event);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!contains(event.x(), event.y())) {
            return false;
        }
        placeRows();
        for (AbstractWidget widget : clickTargets(event.y())) {
            if (widget.visible && widget.isMouseOver(event.x(), event.y()) && widget.mouseClicked(event, doubleClick)) {
                if (built) {
                    children.focus(widget);
                    dragging = widget;
                }
                return true;
            }
        }
        if (event.y() >= viewportTop && event.y() < viewportBottom) {
            for (Row row : rows) {
                int rowY = viewportTop + row.top - scroll;
                if (row.kind == RowKind.SETTING && event.y() >= rowY && event.y() < rowY + row.height
                        && event.x() >= rowsX && event.x() < rowsX + rowsWidth - controlWidth - RESET_SIZE) {
                    if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                        activateFromLabel(row);
                    }
                    return true;
                }
            }
        }
        children.focus(null);
        return true;
    }

    private void activateFromLabel(Row row) {
        if (row.control instanceof SettingsModel.Toggle toggle) {
            toggle.flip().run();
        } else if (row.control instanceof SettingsModel.Cycle cycle) {
            cycle.step().accept(1);
        } else if (row.widget != null) {
            children.focus(row.widget);
        }
    }

    private List<AbstractWidget> clickTargets(double mouseY) {
        List<AbstractWidget> targets = new ArrayList<>();
        targets.add(search);
        targets.add(resetAll);
        if (categoryCycler != null) {
            targets.add(categoryCycler);
        }
        targets.addAll(categoryButtons);
        if (mouseY >= viewportTop && mouseY < viewportBottom) {
            for (Row row : rows) {
                if (row.resetButton != null) {
                    targets.add(row.resetButton);
                }
                if (row.widget != null) {
                    targets.add(row.widget);
                }
            }
        }
        return targets;
    }

    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        return dragging != null && dragging.mouseDragged(event, deltaX, deltaY);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging == null) {
            return false;
        }
        AbstractWidget widget = dragging;
        dragging = null;
        widget.mouseReleased(event);
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        if (scrollY != 0.0) {
            scroll = clampScroll(scroll - (int) Math.signum(scrollY) * SCROLL_STEP);
        }
        return true;
    }

    public SettingsCatalog.Entry entryAt(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY) || mouseY < viewportTop || mouseY >= viewportBottom) {
            return null;
        }
        for (Row row : rows) {
            int rowY = viewportTop + row.top - scroll;
            if (row.kind == RowKind.SETTING && mouseY >= rowY && mouseY < rowY + row.height) {
                return row.entry;
            }
        }
        return null;
    }

    public void refresh() {
        if (built) {
            rebuildRows();
        }
    }
}
