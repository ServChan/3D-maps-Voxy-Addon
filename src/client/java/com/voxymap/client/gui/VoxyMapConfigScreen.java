package com.voxymap.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.voxymap.client.gui.ui.UiButton;
import com.voxymap.client.gui.ui.UiDraw;
import com.voxymap.client.gui.ui.UiTheme;
import com.voxymap.client.integration.LitematicaPlusBridge;
import com.voxymap.client.integration.RemotePlayerMarkers;
import com.voxymap.client.integration.SpatialGuiCompat;
import com.voxymap.client.integration.VanillaSquadServer;
import com.voxymap.client.map.WaypointWorlds;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class VoxyMapConfigScreen extends Screen implements SettingsModel.Host {
    private static final int TITLE_BAR_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 36;
    private static final int MAX_WIDTH = 560;
    private static final int SIDE_GUTTER = 16;

    private final Screen parent;
    private final SettingsView view;
    private UiButton doneButton;
    private String worldId;

    public VoxyMapConfigScreen(Screen parent) {
        super(Component.translatable("screen.voxymap.config.title"));
        this.parent = parent;
        this.view = new SettingsView(this, new SettingsView.Children() {
            @Override
            public void add(AbstractWidget widget) {
                addWidget(widget);
            }

            @Override
            public void remove(AbstractWidget widget) {
                removeWidget(widget);
            }

            @Override
            public void focus(GuiEventListener widget) {
                setFocused(widget);
            }
        }, this::sections);
    }

    private List<SettingsCatalog.Section> sections() {
        return SettingsCatalog.sections(worldId != null, false, VanillaSquadServer.isConnected(),
                RemotePlayerMarkers.available(), LitematicaPlusBridge.isAvailable(), SpatialGuiCompat.isAvailable());
    }

    @Override
    protected void init() {
        worldId = currentWorldId();
        view.close();
        int viewWidth = Math.min(MAX_WIDTH, width - SIDE_GUTTER * 2);
        view.layout((width - viewWidth) / 2, TITLE_BAR_HEIGHT + UiTheme.SPACE_S, viewWidth,
                height - TITLE_BAR_HEIGHT - FOOTER_HEIGHT - UiTheme.SPACE_S);
        doneButton = addRenderableWidget(new UiButton(width / 2 - 80, height - 28, 160, UiTheme.CONTROL_HEIGHT,
                Component.translatable("gui.done"), UiButton.Variant.PRIMARY, button -> onClose()));
        view.setTrailingFocus(List.of(doneButton));
    }

    private static String currentWorldId() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        WorldIdentifier identifier = WorldIdentifier.of(minecraft.level);
        return identifier == null ? null : WaypointWorlds.effective(identifier.getWorldId());
    }

    @Override
    public String worldId() {
        return worldId;
    }

    @Override
    public void openMinimapLayout() {
        if (minecraft != null) {
            minecraft.setScreenAndShow(new MinimapLayoutScreen(this));
        }
    }

    @Override
    public Component actionValue(SettingsCatalog.Entry entry) {
        return Component.empty();
    }

    @Override
    public void runAction(SettingsCatalog.Entry entry) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, width, height, 0xF00D141F);
        graphics.fill(0, 0, width, TITLE_BAR_HEIGHT, UiTheme.SURFACE_RAISED);
        graphics.fill(0, TITLE_BAR_HEIGHT - 1, width, TITLE_BAR_HEIGHT, UiTheme.BORDER);
        graphics.centeredText(font, title, width / 2, 10, UiTheme.TEXT);
        view.extract(graphics, mouseX, mouseY, tickDelta, getFocused());
        String noteKey = worldId != null ? "screen.voxymap.config.map_note" : "screen.voxymap.config.world_note";
        List<FormattedCharSequence> note = font.split(Component.translatable(noteKey), Math.max(80, width / 2 - 100));
        if (!note.isEmpty() && width >= 420) {
            graphics.text(font, note.getFirst(), SIDE_GUTTER, height - 22, UiTheme.TEXT_MUTED);
        }
        doneButton.extractRenderState(graphics, mouseX, mouseY, tickDelta);
        UiDraw.divider(graphics, 0, height - FOOTER_HEIGHT, width);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (view.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (doneButton.isMouseOver(event.x(), event.y())) {
            return doneButton.mouseClicked(event, doubleClick);
        }
        setFocused(null);
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        return view.mouseDragged(event, deltaX, deltaY) || super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return view.mouseReleased(event) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return view.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (view.keyPressed(event, getFocused())) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return view.charTyped(event, getFocused()) || super.charTyped(event);
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public void removed() {
        view.close();
        super.removed();
    }

    @Override
    public void onClose() {
        view.close();
        if (minecraft != null) {
            minecraft.setScreenAndShow(parent);
        }
    }
}
