package com.voxymap.client.gui;

import com.voxymap.client.map.WaypointChatShare;
import com.voxymap.client.map.WaypointImport;
import com.voxymap.client.map.WaypointLabels;
import com.voxymap.client.map.WaypointText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class WaypointImportScreen extends Screen {
    private static final int TITLE_BAR_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 36;
    private static final int ROW_HEIGHT = 28;
    private static final int CONTROL_HEIGHT = 20;
    private static final int MAX_CONTENT_WIDTH = 520;
    private static final int SCROLL_STEP = 28;
    private static final int ACCENT = 0xFF3BA4FF;
    private static final int MUTED = 0xFF9AA7B8;

    private enum State { LOADING, READY, FAILED }

    private record Row(WaypointImport.Found found, int top, Button button) {
    }

    private final Screen parent;
    private final String worldId;
    private CompletableFuture<List<WaypointImport.Found>> scan;
    private List<WaypointImport.Found> found = List.of();
    private final List<WaypointImport.Found> imported = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private State state = State.LOADING;
    private Component status;
    private int contentLeft;
    private int contentWidth;
    private int viewportTop;
    private int viewportBottom;
    private int contentHeight;
    private int scroll;

    public WaypointImportScreen(Screen parent, String worldId) {
        super(Component.translatable("screen.voxymap.import.title"));
        this.parent = parent;
        this.worldId = worldId;
    }

    @Override
    protected void init() {
        if (scan == null && minecraft != null) {
            scan = WaypointImport.scan(minecraft.gameDirectory.toPath());
        }
        rows.clear();
        contentWidth = Math.min(MAX_CONTENT_WIDTH, width - 32);
        contentLeft = (width - contentWidth) / 2;
        viewportTop = TITLE_BAR_HEIGHT + 20;
        viewportBottom = height - FOOTER_HEIGHT;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), pressed -> onClose())
                .bounds(width / 2 - 100, height - 28, 200, CONTROL_HEIGHT).build());

        WaypointText.Dimension here = minecraft == null ? null : WaypointChatShare.currentDimension(minecraft);
        int buttonW = 90;
        int top = 0;
        for (WaypointImport.Found entry : found) {
            boolean sameDimension = entry.dimension() == here;
            boolean done = imported.contains(entry);
            Button button = addRenderableWidget(Button.builder(Component.translatable(done
                            ? "screen.voxymap.import.done" : "screen.voxymap.import.button"), pressed -> importFound(entry))
                    .bounds(contentLeft + contentWidth - buttonW - 4, 0, buttonW, CONTROL_HEIGHT).build());
            button.active = sameDimension && !done && worldId != null;
            if (!sameDimension) {
                button.setTooltip(Tooltip.create(Component.translatable("screen.voxymap.import.other_dimension",
                        WaypointLabels.dimensionName(entry.dimension()))));
            }
            rows.add(new Row(entry, top, button));
            top += ROW_HEIGHT;
        }
        contentHeight = top;
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        layoutRows();
    }

    @Override
    public void tick() {
        super.tick();
        if (state == State.LOADING && scan != null && scan.isDone()) {
            try {
                ServerData server = minecraft == null ? null : minecraft.getCurrentServer();
                found = WaypointImport.rank(scan.join(), server == null ? null : server.ip,
                        minecraft == null ? null : WaypointChatShare.currentDimension(minecraft));
                state = State.READY;
            } catch (RuntimeException exception) {
                state = State.FAILED;
            }
            rebuildWidgets();
        }
    }

    private void importFound(WaypointImport.Found entry) {
        if (worldId == null || minecraft == null || minecraft.player == null) {
            return;
        }
        WaypointImport.Result result = WaypointImport.importInto(worldId, entry.waypoints(), minecraft.player.getY());
        imported.add(entry);
        status = Component.translatable("screen.voxymap.import.result", result.imported(), result.skipped());
        rebuildWidgets();
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (viewportBottom - viewportTop));
    }

    private void layoutRows() {
        for (Row row : rows) {
            int y = viewportTop + row.top() - scroll + (ROW_HEIGHT - CONTROL_HEIGHT) / 2;
            row.button().setY(y);
            row.button().visible = y >= viewportTop && y + CONTROL_HEIGHT <= viewportBottom;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0 || maxScroll() == 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY) * SCROLL_STEP));
        layoutRows();
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, width, height, 0xF00D141F);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, width, TITLE_BAR_HEIGHT, 0xFF182638);
        graphics.centeredText(font, title, width / 2, 9, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("screen.voxymap.import.where"), contentLeft, TITLE_BAR_HEIGHT + 6, MUTED);
        graphics.enableScissor(0, viewportTop, width, viewportBottom);
        WaypointText.Dimension here = minecraft == null ? null : WaypointChatShare.currentDimension(minecraft);
        for (Row row : rows) {
            int y = viewportTop + row.top() - scroll;
            if (y + ROW_HEIGHT < viewportTop || y > viewportBottom) {
                continue;
            }
            WaypointImport.Found entry = row.found();
            boolean sameDimension = entry.dimension() == here;
            graphics.fill(contentLeft, y, contentLeft + contentWidth, y + ROW_HEIGHT - 2, 0x66102034);
            graphics.fill(contentLeft, y, contentLeft + 2, y + ROW_HEIGHT - 2, sameDimension ? ACCENT : MUTED);
            String source = entry.source() == WaypointImport.Source.XAERO ? "Xaero" : "JourneyMap";
            String set = entry.set().isEmpty() ? "" : " / " + entry.set();
            graphics.text(font, source + "  " + entry.world() + set, contentLeft + 8, y + 4,
                    sameDimension ? 0xFFFFFFFF : MUTED);
            graphics.text(font, Component.translatable("screen.voxymap.import.count", entry.waypoints().size(),
                    WaypointLabels.dimensionName(entry.dimension())), contentLeft + 8, y + 15, MUTED);
        }
        if (rows.isEmpty()) {
            int centerY = (viewportTop + viewportBottom) / 2;
            Component message = Component.translatable(switch (state) {
                case LOADING -> "screen.voxymap.import.loading";
                case READY -> "screen.voxymap.import.empty";
                case FAILED -> "screen.voxymap.import.failed";
            });
            graphics.centeredText(font, message, width / 2, centerY - 4, 0xFFFFFFFF);
        }
        graphics.disableScissor();
        if (status != null) {
            graphics.text(font, status, contentLeft, height - FOOTER_HEIGHT + 4, 0xFF7DEBFF);
        }
        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
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
