package com.voxymap.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.voxymap.client.integration.RemotePlayerMarkers;
import com.voxymap.client.integration.VanillaSquadServer;
import com.voxymap.client.map.VoxyMapSettings;
import com.voxymap.client.map.WaypointManager;
import com.voxymap.client.map.WaypointSyncSettings;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class VoxyMapConfigScreen extends Screen {
    private static final int TITLE_BAR_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 36;
    private static final int VIEWPORT_PADDING = 6;
    private static final int HEADER_HEIGHT = 22;
    private static final int ROW_HEIGHT = 24;
    private static final int CONTROL_HEIGHT = 20;
    private static final int SECTION_GAP = 6;
    private static final int MAX_CONTENT_WIDTH = 440;
    private static final int SIDE_GUTTER = 16;
    private static final int SCROLL_STEP = 24;
    private static final int ACCENT = 0xFF3BA4FF;
    private static final int ACCENT_LIGHT = 0xFF7DEBFF;

    private enum Kind {
        HEADER, ROW, NOTE
    }

    private static final class Item {
        final Kind kind;
        final int top;
        final int height;
        final Component text;
        final AbstractWidget control;
        final List<FormattedCharSequence> noteLines;

        Item(Kind kind, int top, int height, Component text, AbstractWidget control,
             List<FormattedCharSequence> noteLines) {
            this.kind = kind;
            this.top = top;
            this.height = height;
            this.text = text;
            this.control = control;
            this.noteLines = noteLines;
        }
    }

    private final Screen parent;
    private final List<Item> items = new ArrayList<>();
    private Button doneButton;
    private EditBox syncAddressBox;
    private EditBox syncPasswordBox;
    private Button syncStatusButton;
    private String worldId;
    private int contentLeft;
    private int contentWidth;
    private int controlX;
    private int contentHeight;
    private int viewportTop;
    private int viewportBottom;
    private int scroll;

    public VoxyMapConfigScreen(Screen parent) {
        super(Component.translatable("screen.voxymap.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        items.clear();
        contentWidth = Math.min(MAX_CONTENT_WIDTH, width - SIDE_GUTTER * 2);
        contentLeft = (width - contentWidth) / 2;
        int controlWidth = Math.max(96, Math.min(170, contentWidth * 2 / 5));
        controlX = contentLeft + contentWidth - controlWidth;
        viewportTop = TITLE_BAR_HEIGHT + VIEWPORT_PADDING;
        viewportBottom = height - FOOTER_HEIGHT;

        worldId = currentWorldId();
        syncAddressBox = null;
        syncPasswordBox = null;
        syncStatusButton = null;

        int y = 0;
        for (SettingsCatalog.Section section : SettingsCatalog.sections(
                worldId != null, false, VanillaSquadServer.isConnected(), RemotePlayerMarkers.available(),
                com.voxymap.client.integration.LitematicaPlusBridge.isAvailable(),
                com.voxymap.client.integration.SpatialGuiCompat.isAvailable())) {
            items.add(new Item(Kind.HEADER, y, HEADER_HEIGHT, Component.translatable(section.group().titleKey()), null, null));
            y += HEADER_HEIGHT;
            for (SettingsCatalog.Entry entry : section.entries()) {
                AbstractWidget control = addRenderableWidget(createControl(entry, controlWidth));
                items.add(new Item(Kind.ROW, y, ROW_HEIGHT, Component.translatable(entry.labelKey()), control, null));
                y += ROW_HEIGHT;
            }
            y += SECTION_GAP;
        }
        String noteKey = worldId != null ? "screen.voxymap.config.map_note" : "screen.voxymap.config.world_note";
        List<FormattedCharSequence> note = font.split(Component.translatable(noteKey), contentWidth);
        items.add(new Item(Kind.NOTE, y, note.size() * font.lineHeight, null, null, note));
        y += note.size() * font.lineHeight + SECTION_GAP;
        contentHeight = y;

        doneButton = addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> closeScreen())
                .bounds(width / 2 - 100, height - 28, 200, CONTROL_HEIGHT).build());

        scroll = clampScroll(scroll);
        layoutControls();
    }

    private AbstractWidget createControl(SettingsCatalog.Entry entry, int controlWidth) {
        return switch (entry) {
            case PAUSE -> toggle(controlWidth, VoxyMapSettings::pauseSingleplayer, VoxyMapSettings::togglePauseSingleplayer);
            case SPEED -> new SpeedSlider(controlWidth);
            case TIME_OF_DAY -> {
                Button button = Button.builder(timeOfDayLabel(), pressed -> {
                    VoxyMapSettings.cycleTimeOfDayMode();
                    pressed.setMessage(timeOfDayLabel());
                }).bounds(controlX, 0, controlWidth, CONTROL_HEIGHT).build();
                yield button;
            }
            case FOG_SUPPRESSION -> Button.builder(fogSuppressionLabel(), pressed -> {
                VoxyMapSettings.cycleFogSuppression();
                pressed.setMessage(fogSuppressionLabel());
            }).bounds(controlX, 0, controlWidth, CONTROL_HEIGHT).build();
            case SCREENSHOT -> Button.builder(screenshotLabel(), pressed -> {
                VoxyMapSettings.cycleScreenshotContent();
                pressed.setMessage(screenshotLabel());
            }).bounds(controlX, 0, controlWidth, CONTROL_HEIGHT).build();
            case VIEW_DIRECTION -> toggle(controlWidth, VoxyMapSettings::viewDirectionEnabled, VoxyMapSettings::toggleViewDirection);
            case WAYPOINTS -> toggle(controlWidth, VoxyMapSettings::waypointsEnabled, VoxyMapSettings::toggleWaypointsVisible);
            case WAYPOINT_LABELS -> cycle(controlWidth,
                    () -> Component.translatable(VoxyMapSettings.waypointLabelMode().translationKey()),
                    VoxyMapSettings::cycleWaypointLabelMode);
            case WAYPOINT_BEAMS -> toggle(controlWidth, VoxyMapSettings::waypointBeams, VoxyMapSettings::toggleWaypointBeams);
            case NEW_WAYPOINT_DISPLAY -> cycle(controlWidth,
                    () -> Component.translatable(VoxyMapSettings.newWaypointDisplay().translationKey()),
                    VoxyMapSettings::cycleNewWaypointDisplay);
            case WAYPOINT_SHAPE -> cycle(controlWidth,
                    () -> Component.translatable(VoxyMapSettings.waypointIconShape().translationKey()),
                    VoxyMapSettings::cycleWaypointIconShape);
            case WAYPOINT_SIZE -> cycle(controlWidth,
                    () -> Component.translatable(VoxyMapSettings.waypointIconSize().translationKey()),
                    VoxyMapSettings::cycleWaypointIconSize);
            case WAYPOINT_LABEL_STYLE -> cycle(controlWidth,
                    () -> Component.translatable(VoxyMapSettings.waypointLabelStyle().translationKey()),
                    VoxyMapSettings::cycleWaypointLabelStyle);
            case WAYPOINT_LABEL_SCALE -> cycle(controlWidth,
                    () -> Component.literal(VoxyMapSettings.waypointLabelScale().label()),
                    VoxyMapSettings::cycleWaypointLabelScale);
            case WAYPOINT_DISTANCE -> toggle(controlWidth, VoxyMapSettings::waypointDistanceShown,
                    VoxyMapSettings::toggleWaypointDistanceShown);
            case WAYPOINT_RANGE -> cycle(controlWidth,
                    () -> Component.literal(SettingsValues.waypointRangeLabel()),
                    VoxyMapSettings::cycleWaypointRange);
            case WAYPOINTS_OTHER_DIMENSION -> toggle(controlWidth, VoxyMapSettings::otherDimensionWaypoints,
                    VoxyMapSettings::toggleOtherDimensionWaypoints);
            case DEATH_POINTS -> toggle(controlWidth, VoxyMapSettings::deathPointsEnabled, VoxyMapSettings::toggleDeathPoints);
            case VANILLASQUAD_PLAYERS -> toggle(controlWidth, VoxyMapSettings::vanillaSquadPlayersEnabled, VoxyMapSettings::toggleVanillaSquadPlayers);
            case VANILLASQUAD_FLAGS -> toggle(controlWidth, VoxyMapSettings::vanillaSquadFlagsEnabled, VoxyMapSettings::toggleVanillaSquadFlags);
            case MINIMAP -> toggle(controlWidth, VoxyMapSettings::minimapEnabled, VoxyMapSettings::toggleMinimap);
            case MINIMAP_ROTATE -> toggle(controlWidth, VoxyMapSettings::minimapRotates, VoxyMapSettings::toggleMinimapRotates);
            case MINIMAP_COLORS -> cycle(controlWidth,
                    () -> Component.translatable(VoxyMapSettings.minimapColors().translationKey()),
                    VoxyMapSettings::cycleMinimapColors);
            case MINIMAP_SHAPE -> cycle(controlWidth,
                    () -> Component.translatable(VoxyMapSettings.minimapShape().translationKey()),
                    VoxyMapSettings::cycleMinimapShape);
            case MINIMAP_SIZE -> cycle(controlWidth,
                    () -> Component.translatable(VoxyMapSettings.minimapSize().translationKey()),
                    VoxyMapSettings::cycleMinimapSize);
            case MINIMAP_CORNER -> cycle(controlWidth,
                    () -> Component.translatable(VoxyMapSettings.minimapCorner().translationKey()),
                    VoxyMapSettings::cycleMinimapCorner);
            case MINIMAP_MOVE -> Button.builder(Component.translatable("screen.voxymap.settings.minimapmove.open"),
                    pressed -> {
                        if (minecraft != null) {
                            minecraft.setScreenAndShow(new MinimapLayoutScreen(this));
                        }
                    }).bounds(controlX, 0, controlWidth, CONTROL_HEIGHT).build();
            case MINIMAP_OPACITY -> cycle(controlWidth,
                    () -> Component.translatable(VoxyMapSettings.minimapOpacity().translationKey()),
                    VoxyMapSettings::cycleMinimapOpacity);
            case MINIMAP_NIGHT -> toggle(controlWidth, VoxyMapSettings::minimapNightShading,
                    VoxyMapSettings::toggleMinimapNightShading);
            case MINIMAP_CAVE -> cycle(controlWidth,
                    () -> Component.translatable(VoxyMapSettings.caveMode().translationKey()),
                    VoxyMapSettings::cycleCaveMode);
            case MINIMAP_SCREENS -> cycle(controlWidth,
                    () -> Component.translatable(VoxyMapSettings.minimapScreenVisibility().translationKey()),
                    VoxyMapSettings::cycleMinimapScreenVisibility);
            case MINIMAP_ZOOM -> cycle(controlWidth,
                    () -> Component.literal(SettingsValues.minimapZoomLabel()),
                    VoxyMapSettings::cycleMinimapZoom);
            case INFO_COORDINATES, INFO_NETHER_COORDINATES, INFO_BIOME, INFO_LIGHT, INFO_TIME, INFO_CHUNK, INFO_FACING -> toggle(controlWidth,
                    () -> VoxyMapSettings.infoLineShown(entry.infoLine()),
                    () -> VoxyMapSettings.toggleInfoLine(entry.infoLine()));
            case RADAR_HOSTILE, RADAR_NEUTRAL, RADAR_PASSIVE, RADAR_ITEMS, RADAR_PLAYERS -> toggle(controlWidth,
                    () -> VoxyMapSettings.radarShows(entry.radarCategory()),
                    () -> VoxyMapSettings.toggleRadarCategory(entry.radarCategory()));
            case CHUNK_GRID -> toggle(controlWidth, VoxyMapSettings::chunkGridEnabled, VoxyMapSettings::toggleChunkGrid);
            case RADAR_ICONS -> cycle(controlWidth,
                    () -> Component.translatable(VoxyMapSettings.radarIcons().translationKey()),
                    VoxyMapSettings::cycleRadarIcons);
            case OVERLAY_SLIME, OVERLAY_LIGHT, OVERLAY_BIOMES -> toggle(controlWidth,
                    () -> VoxyMapSettings.overlayShown(entry.mapOverlay()),
                    () -> VoxyMapSettings.toggleOverlay(entry.mapOverlay()));
            case SCHEMATICS -> toggle(controlWidth, VoxyMapSettings::schematicsEnabled, VoxyMapSettings::toggleSchematics);
            case SPATIAL_GUI_FLAT_MAP -> toggle(controlWidth, VoxyMapSettings::spatialGuiFlatMap, VoxyMapSettings::toggleSpatialGuiFlatMap);
            case SPATIAL_GUI_3D -> toggle(controlWidth, VoxyMapSettings::windowMap3d, VoxyMapSettings::toggleWindowMap3d);
            case DIAGNOSTICS -> {
                Button button = Button.builder(Component.translatable(SettingsValues.diagnosticsKey()), pressed -> {
                }).bounds(controlX, 0, controlWidth, CONTROL_HEIGHT).build();
                button.active = false;
                yield button;
            }
            case VANILLASQUAD_MAP -> Button.builder(vanillaSquadMapLabel(), pressed -> {
                VoxyMapSettings.cycleVanillaSquadMap(worldId);
                pressed.setMessage(vanillaSquadMapLabel());
            }).bounds(controlX, 0, controlWidth, CONTROL_HEIGHT).build();
            case SYNC_ADDRESS -> {
                syncAddressBox = new EditBox(font, controlX, 0, controlWidth, CONTROL_HEIGHT,
                        Component.translatable(entry.labelKey()));
                syncAddressBox.setMaxLength(128);
                String address = WaypointSyncSettings.address(worldId);
                syncAddressBox.setValue(address == null ? "" : address);
                syncAddressBox.setHint(Component.translatable("screen.voxymap.settings.sync.not_set"));
                yield syncAddressBox;
            }
            case SYNC_PASSWORD -> {
                syncPasswordBox = new EditBox(font, controlX, 0, controlWidth, CONTROL_HEIGHT,
                        Component.translatable(entry.labelKey()));
                syncPasswordBox.setMaxLength(128);

                syncPasswordBox.addFormatter((text, offset) ->
                        FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
                String password = WaypointSyncSettings.password(worldId);
                syncPasswordBox.setValue(password == null ? "" : password);
                syncPasswordBox.setHint(Component.translatable("screen.voxymap.settings.sync.not_set"));
                yield syncPasswordBox;
            }
            case SYNC_STATUS -> {
                syncStatusButton = Button.builder(syncStatusLabel(), pressed -> {
                }).bounds(controlX, 0, controlWidth, CONTROL_HEIGHT).build();
                syncStatusButton.active = false;
                yield syncStatusButton;
            }
            case MAP_SHARE, MAP_RECEIVE ->
                    throw new IllegalStateException("Map-only setting listed in the Mod Menu screen: " + entry);
        };
    }

    private static String currentWorldId() {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        WorldIdentifier identifier = WorldIdentifier.of(minecraft.level);
        return identifier == null ? null : com.voxymap.client.map.WaypointWorlds.effective(identifier.getWorldId());
    }

    private static Component screenshotLabel() {
        return Component.translatable(VoxyMapSettings.screenshotContent().translationKey());
    }

    private Component vanillaSquadMapLabel() {
        return Component.translatable(VoxyMapSettings.vanillaSquadMap(worldId).translationKey());
    }

    private Component syncStatusLabel() {
        WaypointManager.SyncStatus status = WaypointManager.syncStatus(worldId);
        String key = switch (status) {
            case DISABLED -> "screen.voxymap.settings.sync.status.disabled";
            case READ_ONLY -> "screen.voxymap.settings.sync.status.read_only";
            case SYNCING -> "screen.voxymap.settings.sync.status.syncing";
            case OK -> "screen.voxymap.settings.sync.status.ok";
            case ERROR -> "screen.voxymap.settings.sync.status.error";
        };
        ChatFormatting color = switch (status) {
            case OK -> ChatFormatting.GREEN;
            case READ_ONLY -> ChatFormatting.YELLOW;
            case SYNCING -> ChatFormatting.AQUA;
            case ERROR -> ChatFormatting.RED;
            case DISABLED -> ChatFormatting.GRAY;
        };
        return Component.translatable(key).withStyle(color);
    }

    private void commitSyncFields() {
        if (worldId == null) {
            return;
        }
        if (syncAddressBox != null) {
            String address = syncAddressBox.getValue().trim();
            String stored = WaypointSyncSettings.address(worldId);
            if (!address.equals(stored == null ? "" : stored)) {
                WaypointSyncSettings.setAddress(worldId, address);
            }
        }
        if (syncPasswordBox != null) {
            String password = syncPasswordBox.getValue().trim();
            String stored = WaypointSyncSettings.password(worldId);
            if (!password.equals(stored == null ? "" : stored)) {
                WaypointSyncSettings.setPassword(worldId, password);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (syncStatusButton != null) {
            syncStatusButton.setMessage(syncStatusLabel());
        }
    }

    @Override
    public void removed() {
        commitSyncFields();
        super.removed();
    }

    private Button cycle(int controlWidth, Supplier<Component> label, Runnable advance) {
        return Button.builder(label.get(), pressed -> {
            advance.run();
            pressed.setMessage(label.get());
        }).bounds(controlX, 0, controlWidth, CONTROL_HEIGHT).build();
    }

    private Button toggle(int controlWidth, BooleanSupplier getter, Runnable flip) {
        return Button.builder(onOffLabel(getter.getAsBoolean()), pressed -> {
            flip.run();
            pressed.setMessage(onOffLabel(getter.getAsBoolean()));
        }).bounds(controlX, 0, controlWidth, CONTROL_HEIGHT).build();
    }

    private static Component onOffLabel(boolean enabled) {
        return Component.translatable(enabled ? "screen.voxymap.settings.on" : "screen.voxymap.settings.off")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static Component fogSuppressionLabel() {
        VoxyMapSettings.FogSuppression mode = VoxyMapSettings.fogSuppressionMode();
        return Component.translatable(mode.translationKey()).withStyle(switch (mode) {
            case ALWAYS -> ChatFormatting.GREEN;
            case NETHER_ONLY -> ChatFormatting.YELLOW;
            case OFF -> ChatFormatting.RED;
        });
    }

    private static Component timeOfDayLabel() {
        return Component.translatable(VoxyMapSettings.timeOfDayMode().translationKey());
    }

    private static Component speedLabel() {
        return Component.literal(String.format(Locale.ROOT, "%.2fx", VoxyMapSettings.cameraSpeedMultiplier()));
    }

    private static final class SpeedSlider extends AbstractSliderButton {
        private static final double RANGE = VoxyMapSettings.CAMERA_SPEED_MAX - VoxyMapSettings.CAMERA_SPEED_MIN;

        SpeedSlider(int controlWidth) {
            super(0, 0, controlWidth, CONTROL_HEIGHT, speedLabel(),
                    (VoxyMapSettings.cameraSpeedMultiplier() - VoxyMapSettings.CAMERA_SPEED_MIN) / RANGE);
        }

        @Override
        protected void updateMessage() {
            setMessage(speedLabel());
        }

        @Override
        protected void applyValue() {
            VoxyMapSettings.setCameraSpeed(VoxyMapSettings.CAMERA_SPEED_MIN + value * RANGE);
        }
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

    private void layoutControls() {
        for (Item item : items) {
            if (item.control != null) {
                item.control.setX(controlX);
                item.control.setY(viewportTop + item.top - scroll + (ROW_HEIGHT - CONTROL_HEIGHT) / 2);
            }
        }
    }

    private void scrollTo(int requested) {
        scroll = clampScroll(requested);
        layoutControls();
    }

    private void ensureVisible(Item item) {
        if (item.top < scroll) {
            scrollTo(item.top);
        } else if (item.top + item.height > scroll + viewportHeight()) {
            scrollTo(item.top + item.height - viewportHeight());
        }
    }

    @Override
    public void setFocused(GuiEventListener focused) {
        super.setFocused(focused);
        if (focused == null) return;

        for (Item item : items) {
            if (item.control == focused) {
                ensureVisible(item);
                return;
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, width, height, 0xF00D141F);

        graphics.enableScissor(0, viewportTop, width, viewportBottom);
        for (Item item : items) {
            int y = viewportTop + item.top - scroll;
            if (y + item.height < viewportTop || y > viewportBottom) continue;
            switch (item.kind) {
                case HEADER -> drawHeader(graphics, item.text, y);
                case ROW -> {
                    drawRow(graphics, item.text, y);
                    item.control.extractRenderState(graphics, mouseX, mouseY, tickDelta);
                }
                case NOTE -> {
                    int lineY = y;
                    for (FormattedCharSequence line : item.noteLines) {
                        graphics.text(font, line, contentLeft, lineY, 0xFF9AA7B8);
                        lineY += font.lineHeight;
                    }
                }
            }
        }
        graphics.disableScissor();

        int maxScroll = maxScroll();
        if (maxScroll > 0) {
            int trackX = contentLeft + contentWidth + 6;
            int trackH = viewportHeight();
            int thumbH = Math.max(18, trackH * trackH / contentHeight);
            int thumbY = viewportTop + (trackH - thumbH) * scroll / maxScroll;
            graphics.fill(trackX, viewportTop, trackX + 2, viewportTop + trackH, 0x553BA4FF);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, ACCENT_LIGHT);
        }

        graphics.fill(0, 0, width, TITLE_BAR_HEIGHT, 0xFF182638);
        graphics.centeredText(font, title, width / 2, 9, 0xFFFFFFFF);
        doneButton.extractRenderState(graphics, mouseX, mouseY, tickDelta);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, Component title, int y) {
        String text = title.getString();
        graphics.text(font, text, contentLeft, y + 8, ACCENT_LIGHT);
        int lineX = contentLeft + font.width(text) + 6;
        int lineRight = contentLeft + contentWidth;
        if (lineRight > lineX) {
            graphics.fill(lineX, y + 12, lineRight, y + 13, 0x553BA4FF);
        }
    }

    private void drawRow(GuiGraphicsExtractor graphics, Component label, int y) {
        graphics.fill(contentLeft, y, contentLeft + contentWidth, y + ROW_HEIGHT - 2, 0x66102034);
        graphics.fill(contentLeft, y, contentLeft + 2, y + ROW_HEIGHT - 2, ACCENT);
        graphics.text(font, fit(label.getString(), controlX - contentLeft - 16),
                contentLeft + 8, y + (ROW_HEIGHT - font.lineHeight) / 2 - 1, 0xFFDFF8FF);
    }

    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {

        if (event.y() < viewportTop || event.y() >= viewportBottom) {
            return doneButton.mouseClicked(event, doubleClick);
        }
        return super.mouseClicked(event, doubleClick);
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
        if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)
                && (getFocused() == syncAddressBox || getFocused() == syncPasswordBox) && getFocused() != null) {
            commitSyncFields();
            setFocused(null);
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            closeScreen();
            return true;
        }
        if (key == InputConstants.KEY_PAGEDOWN) {
            scrollTo(scroll + viewportHeight());
            return true;
        }
        if (key == InputConstants.KEY_PAGEUP) {
            scrollTo(scroll - viewportHeight());
            return true;
        }
        return super.keyPressed(event);
    }

    private void closeScreen() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }
}
