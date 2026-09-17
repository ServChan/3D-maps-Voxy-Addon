package com.voxymap.client.gui;

import com.voxymap.client.map.OreMaskController;
import com.voxymap.client.map.RoomCutawayShaderPatcher;
import com.voxymap.client.map.VoxyMapSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.BooleanSupplier;

/** Accessible global settings surface used by Mod Menu; world-specific sync remains in MapScreen. */
public final class VoxyMapConfigScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;
    private final Screen parent;

    public VoxyMapConfigScreen(Screen parent) {
        super(Component.translatable("screen.voxymap.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int contentWidth = Math.min(620, width - 32);
        int columnWidth = (contentWidth - GAP) / 2;
        int left = (width - contentWidth) / 2;
        int y = 40;

        addToggle(left, y, columnWidth, "screen.voxymap.settings.pause",
                VoxyMapSettings::pauseSingleplayer, VoxyMapSettings::togglePauseSingleplayer);
        addToggle(left + columnWidth + GAP, y, columnWidth, "screen.voxymap.settings.cutaway",
                VoxyMapSettings::roomCutawayEnabled, VoxyMapSettings::toggleRoomCutaway);
        y += BUTTON_HEIGHT + GAP;
        addToggle(left, y, columnWidth, "screen.voxymap.settings.vanillasquad",
                VoxyMapSettings::vanillaSquadPlayersEnabled, VoxyMapSettings::toggleVanillaSquadPlayers);
        addToggle(left + columnWidth + GAP, y, columnWidth, "screen.voxymap.settings.vanillasquadflags",
                VoxyMapSettings::vanillaSquadFlagsEnabled, VoxyMapSettings::toggleVanillaSquadFlags);
        y += BUTTON_HEIGHT + GAP;
        addToggle(left, y, columnWidth, "screen.voxymap.settings.ghastscanner",
                VoxyMapSettings::ghastScannerRouteEnabled, VoxyMapSettings::toggleGhastScannerRoute);
        addToggle(left + columnWidth + GAP, y, columnWidth, "screen.voxymap.settings.waypoints",
                VoxyMapSettings::waypointsEnabled, VoxyMapSettings::toggleWaypointsVisible);
        y += BUTTON_HEIGHT + GAP;
        addToggle(left, y, columnWidth, "screen.voxymap.settings.minimap",
                VoxyMapSettings::minimapEnabled, VoxyMapSettings::toggleMinimap);
        addToggle(left + columnWidth + GAP, y, columnWidth, "screen.voxymap.settings.mobs",
                VoxyMapSettings::mobsEnabled, VoxyMapSettings::toggleMobs);
        y += BUTTON_HEIGHT + GAP;
        addToggle(left, y, columnWidth, "screen.voxymap.settings.minimaprotate",
                VoxyMapSettings::minimapRotates, VoxyMapSettings::toggleMinimapRotates);
        addToggle(left + columnWidth + GAP, y, columnWidth, "screen.voxymap.settings.chunkgrid",
                VoxyMapSettings::chunkGridEnabled, VoxyMapSettings::toggleChunkGrid);
        y += BUTTON_HEIGHT + GAP;
        addToggle(left, y, columnWidth, "screen.voxymap.settings.deathpoints",
                VoxyMapSettings::deathPointsEnabled, VoxyMapSettings::toggleDeathPoints);
        addToggle(left + columnWidth + GAP, y, columnWidth, "screen.voxymap.settings.fogsuppression",
                VoxyMapSettings::fogSuppressionEnabled, VoxyMapSettings::toggleFogSuppression);
        y += BUTTON_HEIGHT + GAP;
        addRenderableWidget(Button.builder(timeLabel(), button -> {
            VoxyMapSettings.cycleTimeOfDayMode();
            button.setMessage(timeLabel());
        }).bounds(left, y, columnWidth, BUTTON_HEIGHT).build());
        y += BUTTON_HEIGHT + GAP;
        addRenderableWidget(Button.builder(speedLabel(), button -> {
            VoxyMapSettings.cycleCameraSpeed();
            button.setMessage(speedLabel());
        }).bounds(left, y, contentWidth, BUTTON_HEIGHT).build());
        y += BUTTON_HEIGHT + GAP;

        Button diagnostics = addRenderableWidget(Button.builder(diagnosticsLabel(), button -> {
        }).bounds(left, y, contentWidth, BUTTON_HEIGHT).build());
        diagnostics.active = false;

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> closeScreen())
                .bounds(width / 2 - 100, height - 30, 200, BUTTON_HEIGHT).build());
    }

    private void addToggle(int x, int y, int buttonWidth, String key,
                           BooleanSupplier getter, Runnable toggle) {
        addRenderableWidget(Button.builder(toggleLabel(key, getter.getAsBoolean()), button -> {
            toggle.run();
            button.setMessage(toggleLabel(key, getter.getAsBoolean()));
        }).bounds(x, y, buttonWidth, BUTTON_HEIGHT).build());
    }

    private static Component toggleLabel(String key, boolean enabled) {
        return Component.translatable(key).copy().append(": ")
                .append(Component.translatable(enabled
                        ? "screen.voxymap.settings.on" : "screen.voxymap.settings.off"));
    }

    private static Component timeLabel() {
        return Component.translatable("screen.voxymap.settings.timeofday").copy().append(": ")
                .append(Component.translatable(VoxyMapSettings.timeOfDayMode().translationKey()));
    }

    private static Component speedLabel() {
        return Component.translatable("screen.voxymap.settings.speed").copy().append(": ")
                .append(String.format(java.util.Locale.ROOT, "%.2fx", VoxyMapSettings.cameraSpeedMultiplier()));
    }

    private static Component diagnosticsLabel() {
        String value = !RoomCutawayShaderPatcher.isSupported()
                ? "screen.voxymap.diagnostics.shader_unsupported"
                : !OreMaskController.isSupported()
                ? "screen.voxymap.diagnostics.ore_mask_unsupported"
                : "screen.voxymap.diagnostics.ok";
        return Component.translatable("screen.voxymap.settings.diagnostics").copy().append(": ")
                .append(Component.translatable(value));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, width, height, 0xF00D141F);
        graphics.fill(0, 0, width, 28, 0xFF182638);
        graphics.centeredText(font, title, width / 2, 9, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            closeScreen();
            return true;
        }
        return super.keyPressed(event);
    }

    private void closeScreen() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }
}
