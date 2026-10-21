package com.voxymap.client.gui.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class UiTextField extends EditBox {
    private static final int PADDING_X = 5;
    private static final Identifier FIELD = Identifier.withDefaultNamespace("widget/text_field");
    private static final Identifier FIELD_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/text_field_highlighted");

    private final UiIcons.Icon icon;
    private int frameX;
    private int frameY;
    private int frameWidth;
    private int frameHeight;

    public UiTextField(Font font, int x, int y, int width, int height, Component narration, UiIcons.Icon icon) {
        super(font, x, y, width, height, narration);
        this.icon = icon;
        setBordered(false);
        setTextColor(UiTheme.TEXT);
        setTextColorUneditable(UiTheme.TEXT_DISABLED);
        setFrame(x, y, width, height);
    }

    public void setFrame(int x, int y, int width, int height) {
        frameX = x;
        frameY = y;
        frameWidth = width;
        frameHeight = height;
        int iconSpace = icon == null ? 0 : UiTheme.ICON_SIZE + UiTheme.SPACE_S;
        setX(x + PADDING_X + iconSpace);
        setY(y + (height - 8) / 2);
        setWidth(Math.max(8, width - PADDING_X * 2 - iconSpace));
        setHeight(10);
    }

    public int frameX() {
        return frameX;
    }

    public int frameY() {
        return frameY;
    }

    public int frameWidth() {
        return frameWidth;
    }

    public int frameHeight() {
        return frameHeight;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && mouseX >= frameX && mouseX < frameX + frameWidth
                && mouseY >= frameY && mouseY < frameY + frameHeight;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (!visible) {
            return;
        }
        boolean focused = isFocused();
        boolean hovered = isMouseOver(mouseX, mouseY);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, focused || hovered ? FIELD_HIGHLIGHTED : FIELD,
                frameX, frameY, frameWidth, frameHeight);
        if (icon != null) {
            UiIcons.draw(g, icon, frameX + PADDING_X, frameY + (frameHeight - UiTheme.ICON_SIZE) / 2,
                    focused ? UiTheme.TEXT_SECONDARY : UiTheme.TEXT_MUTED);
        }
        super.extractWidgetRenderState(g, mouseX, mouseY, delta);
    }
}
