package com.voxymap.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.voxymap.client.map.WaypointManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class WaypointEditScreen extends Screen {
    private final Screen parent;
    private final WaypointForm form;

    private WaypointEditScreen(Screen parent, String titleKey, FormFactory factory) {
        super(Component.translatable(titleKey));
        this.parent = parent;
        this.form = factory.create(new WaypointForm.Listener() {
            @Override
            public void saved(WaypointManager.Waypoint waypoint) {
                onClose();
            }

            @Override
            public void cancelled() {
                onClose();
            }

            @Override
            public void teleported() {
                if (minecraft != null) {
                    minecraft.setScreenAndShow(null);
                }
            }
        });
    }

    public static WaypointEditScreen create(Screen parent, String worldId, double x, double y, double z) {
        return new WaypointEditScreen(parent, "screen.voxymap.waypoint.add_title",
                listener -> WaypointForm.create(worldId, x, y, z, listener));
    }

    public static WaypointEditScreen edit(Screen parent, String worldId, WaypointManager.Waypoint waypoint) {
        return new WaypointEditScreen(parent, "screen.voxymap.waypoint.edit_title",
                listener -> WaypointForm.edit(worldId, waypoint, listener));
    }

    @Override
    protected void init() {
        for (AbstractWidget widget : form.layout(font, width, height, -1)) {
            addRenderableWidget(widget);
        }
        setInitialFocus(form.nameBox());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        graphics.fill(0, 0, width, height, 0xB0050A12);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        form.extractPanel(graphics, font);
        super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
        form.extractOverlay(graphics, font);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) && !(getFocused() instanceof Button)) {
            form.submit();
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

    @FunctionalInterface
    private interface FormFactory {
        WaypointForm create(WaypointForm.Listener listener);
    }
}
