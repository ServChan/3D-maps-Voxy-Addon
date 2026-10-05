package com.voxymap.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.voxymap.client.compat.MinecraftCompat;
import com.voxymap.client.map.FlatMapRenderer;
import com.voxymap.client.map.NetherRoofController;
import com.voxymap.client.map.VoxyMapCameraController;
import com.voxymap.client.map.VoxyMapSettings;
import com.voxymap.client.map.WaypointManager;
import com.voxymap.client.map.WindowMapRenderer;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Locale;

public final class WindowMapScreen extends Screen {
    private static final int HEADER_HEIGHT = 26;
    private static final int MAX_PANEL_WIDTH = 260;
    private static final int MAX_PANEL_HEIGHT = 200;
    private static final int MARGIN = 6;
    private static final int BUTTON_HEIGHT = 18;
    private static final double MIN_PIXELS_PER_BLOCK = 0.125;
    private static final double MAX_PIXELS_PER_BLOCK = 8.0;
    private static final double MIN_BLOCKS_PER_PIXEL = 0.125;
    private static final double MAX_BLOCKS_PER_PIXEL = 256.0;
    private static final double ZOOM_STEP = 1.25;
    private static final double MAX_PITCH_CONTROL = 1.285;
    private static final double MAX_KEYBOARD_SPEED = 640.0;
    private static final int BACKGROUND = 0xF00B1220;
    private static final int FRAME = 0xFF3BA4FF;
    private static final int TEXT = 0xFFDFF8FF;
    private static final int MUTED_TEXT = 0xFF9FB4C8;
    private static final int WARNING_TEXT = 0xFFFFC266;

    private static double pixelsPerBlock = 1.0;
    private static double blocksPerPixel = 4.0;
    private static double yaw = Math.toRadians(45.0);
    private static double pitchControl = 0.68;

    private double centerX;
    private double centerY;
    private double centerZ;
    private boolean following = true;
    private boolean dragging;
    private boolean rotating;
    private long lastFrameNanos;
    private Button modeButton;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public WindowMapScreen() {
        super(Component.translatable("screen.voxymap.flat.title"));
    }

    @Override
    protected void init() {
        NetherRoofController.tick(minecraft);
        Player player = minecraft.player;
        if (player != null && following) {
            centerX = player.getX();
            centerY = player.getY();
            centerZ = player.getZ();
        }
        Component centerLabel = Component.translatable("screen.voxymap.flat.center");
        Component fullLabel = Component.translatable("screen.voxymap.flat.open_3d");
        Component modeLabel = modeLabel();
        int centerWidth = font.width(centerLabel) + 16;
        int fullWidth = font.width(fullLabel) + 16;
        int modeWidth = Math.max(font.width(Component.translatable("screen.voxymap.flat.mode_3d")),
                font.width(Component.translatable("screen.voxymap.flat.mode_flat"))) + 16;
        panelWidth = Math.min(width - MARGIN * 2, MAX_PANEL_WIDTH);
        panelHeight = Math.min(height - MARGIN * 2, MAX_PANEL_HEIGHT);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        int buttonY = panelY + (HEADER_HEIGHT - BUTTON_HEIGHT) / 2;
        int x = panelX + panelWidth - MARGIN - fullWidth;
        addRenderableWidget(Button.builder(fullLabel, button -> openFullMap())
                .bounds(x, buttonY, fullWidth, BUTTON_HEIGHT).build());
        x -= 4 + centerWidth;
        addRenderableWidget(Button.builder(centerLabel, button -> following = true)
                .bounds(x, buttonY, centerWidth, BUTTON_HEIGHT).build());
        x -= 4 + modeWidth;
        modeButton = addRenderableWidget(Button.builder(modeLabel, button -> {
            VoxyMapSettings.toggleWindowMap3d();
            button.setMessage(modeLabel());
        }).bounds(x, buttonY, modeWidth, BUTTON_HEIGHT).build());
    }

    private static Component modeLabel() {
        return Component.translatable(VoxyMapSettings.windowMap3d() ? "screen.voxymap.flat.mode_flat" : "screen.voxymap.flat.mode_3d");
    }

    private boolean threeD() {
        return VoxyMapSettings.windowMap3d() && WindowMapRenderer.status() == WindowMapRenderer.Status.READY;
    }

    private List<FormattedCharSequence> footerLines() {
        String key;
        if (!threeD() && VoxyMapSettings.windowMap3d()) {
            key = "screen.voxymap.flat.unavailable." + WindowMapRenderer.status().name().toLowerCase(Locale.ROOT);
        } else {
            key = threeD() ? "screen.voxymap.flat.hints_3d" : "screen.voxymap.flat.hints";
        }
        return font.split(Component.translatable(key), Math.max(1, panelWidth - MARGIN * 2));
    }

    private int footerHeight() {
        return footerLines().size() * font.lineHeight + 6;
    }

    private FlatMapRenderer.View view() {
        return new FlatMapRenderer.View(panelX + MARGIN, panelY + HEADER_HEIGHT, Math.max(1, panelWidth - MARGIN * 2),
                Math.max(1, panelHeight - HEADER_HEIGHT - footerHeight()), centerX, centerZ, pixelsPerBlock);
    }

    private VoxyMapCameraController.Pose pose() {
        boolean end = minecraft.level != null && "minecraft:the_end".equals(minecraft.level.dimension().identifier().toString());
        return VoxyMapCameraController.pose(end, centerX, centerY, centerZ, yaw, pitchControl, blocksPerPixel);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, BACKGROUND);
        g.outline(panelX, panelY, panelWidth, panelHeight, FRAME);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            onClose();
            return;
        }
        moveWithKeyboard();
        if (following) {
            centerX = player.getX();
            centerY = player.getY();
            centerZ = player.getZ();
        }
        if (modeButton != null) {
            modeButton.setMessage(modeLabel());
        }
        FlatMapRenderer.View view = view();
        boolean warning = !threeD() && VoxyMapSettings.windowMap3d();
        if (threeD()) {
            extract3d(g, view, player);
        } else {
            FlatMapRenderer.extract(g, minecraft, view);
        }
        g.outline(view.left() - 1, view.top() - 1, view.width() + 2, view.height() + 2, FRAME);

        int titleY = panelY + (HEADER_HEIGHT - font.lineHeight) / 2 + 1;
        g.text(font, title, panelX + MARGIN, titleY, TEXT);
        if (!threeD() && view.contains(mouseX, mouseY)) {
            String cursor = (int) Math.floor(view.worldX(mouseX)) + ", " + (int) Math.floor(view.worldZ(mouseY));
            g.text(font, cursor, panelX + MARGIN + font.width(title) + 8, titleY, MUTED_TEXT);
        }
        int lineY = view.top() + view.height() + 4;
        for (FormattedCharSequence line : footerLines()) {
            g.text(font, line, panelX + MARGIN, lineY, warning ? WARNING_TEXT : MUTED_TEXT);
            lineY += font.lineHeight;
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void extract3d(GuiGraphicsExtractor g, FlatMapRenderer.View view, Player player) {
        int scale = Math.max(1, minecraft.getWindow().getGuiScale());
        NetherRoofController.updateViewContext(minecraft, centerX, centerY, centerZ, blocksPerPixel, Math.min(width, height));
        WindowMapRenderer.render(pose(), view.width() * scale, view.height() * scale);
        GpuTextureView color = WindowMapRenderer.colorView();
        if (color == null) {
            return;
        }
        g.blit(color, WindowMapRenderer.sampler(), view.left(), view.top(), view.left() + view.width(),
                view.top() + view.height(), 0.0f, 1.0f, 1.0f, 0.0f);
        g.enableScissor(view.left(), view.top(), view.left() + view.width(), view.top() + view.height());
        String worldId = minecraft.level == null ? null : worldId();
        if (worldId != null && VoxyMapSettings.waypointsEnabled()) {
            for (WaypointManager.Waypoint waypoint : WaypointManager.visibleForWorld(worldId)) {
                int[] point = project(view, waypoint.x(), waypoint.y(), waypoint.z());
                if (point != null) {
                    drawMarker(g, point[0], point[1], waypoint.color(), waypoint.name());
                }
            }
        }
        for (Player other : minecraft.level.players()) {
            if (other == player) {
                continue;
            }
            int[] point = project(view, other.getX(), other.getY(), other.getZ());
            if (point != null) {
                drawMarker(g, point[0], point[1], 0xFFFF62D4, other.getName().getString());
            }
        }
        int[] self = project(view, player.getX(), player.getY(), player.getZ());
        if (self != null) {
            g.fill(self[0] - 3, self[1] - 3, self[0] + 4, self[1] + 4, 0xFF0A0A12);
            g.fill(self[0] - 2, self[1] - 2, self[0] + 3, self[1] + 3, 0xFF55E7FF);
        }
        g.disableScissor();
    }

    private void drawMarker(GuiGraphicsExtractor g, int x, int y, int color, String name) {
        g.fill(x - 3, y - 3, x + 4, y + 4, 0xEE0A0A12);
        g.fill(x - 2, y - 2, x + 3, y + 3, color);
        int nameWidth = font.width(name);
        int nameX = x - nameWidth / 2;
        int nameY = y - 14;
        g.fill(nameX - 2, nameY - 1, nameX + nameWidth + 2, nameY + 8, 0x99030914);
        g.text(font, name, nameX, nameY, 0xFFFFFFFF);
    }

    private static int[] project(FlatMapRenderer.View view, double x, double y, double z) {
        double[] ndc = WindowMapRenderer.project(x, y, z);
        if (ndc == null) {
            return null;
        }
        double sx = view.left() + (ndc[0] + 1.0) * 0.5 * view.width();
        double sy = view.top() + (1.0 - ndc[1]) * 0.5 * view.height();
        if (!view.contains(sx, sy)) {
            return null;
        }
        return new int[]{(int) Math.round(sx), (int) Math.round(sy)};
    }

    private String worldId() {
        WorldIdentifier identifier = WorldIdentifier.of(minecraft.level);
        return identifier == null ? null : identifier.getWorldId();
    }

    private void moveWithKeyboard() {
        long now = System.nanoTime();
        double dt = lastFrameNanos == 0L ? 1.0 / 60.0 : Math.min(0.08, (now - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = now;
        double forward = (keyDown(InputConstants.KEY_W) || keyDown(InputConstants.KEY_UP) ? 1.0 : 0.0)
                - (keyDown(InputConstants.KEY_S) || keyDown(InputConstants.KEY_DOWN) ? 1.0 : 0.0);
        double strafe = (keyDown(InputConstants.KEY_A) || keyDown(InputConstants.KEY_LEFT) ? 1.0 : 0.0)
                - (keyDown(InputConstants.KEY_D) || keyDown(InputConstants.KEY_RIGHT) ? 1.0 : 0.0);
        double length = Math.hypot(forward, strafe);
        if (length == 0.0 || dragging) {
            return;
        }
        forward /= Math.max(1.0, length);
        strafe /= Math.max(1.0, length);
        following = false;
        double boost = keyDown(InputConstants.KEY_LSHIFT) || keyDown(InputConstants.KEY_RSHIFT) ? 2.5 : 1.0;
        double blocks = threeD() ? blocksPerPixel : 1.0 / pixelsPerBlock;
        double speed = Math.min(Math.max(12.0, blocks * 76.0), MAX_KEYBOARD_SPEED) * boost
                * VoxyMapSettings.cameraSpeedMultiplier() * dt;
        if (threeD()) {
            centerX += (-Math.sin(yaw) * forward + Math.cos(yaw) * strafe) * speed;
            centerZ += (Math.cos(yaw) * forward + Math.sin(yaw) * strafe) * speed;
        } else {
            centerX -= strafe * speed;
            centerZ -= forward * speed;
        }
    }

    private static boolean keyDown(int key) {
        return InputConstants.isKeyDown(key);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isInside) {
        if (super.mouseClicked(event, isInside)) {
            return true;
        }
        if (!view().contains(event.x(), event.y())) {
            return false;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            dragging = true;
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && threeD()) {
            rotating = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && dragging) {
            dragging = false;
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && rotating) {
            rotating = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (rotating) {
            yaw += deltaX * 0.01;
            pitchControl = Mth.clamp(pitchControl + deltaY * 0.006, 0.0, MAX_PITCH_CONTROL);
            return true;
        }
        if (dragging) {
            following = false;
            if (threeD()) {
                VoxyMapCameraController.Pose pose = pose();
                double range = Math.sqrt(square(pose.x() - centerX) + square(pose.y() - centerY) + square(pose.z() - centerZ));
                double perPixel = 2.0 * range * Math.tan(Math.toRadians(pose.fov()) / 2.0) / view().height();
                double alongView = perPixel / Math.max(0.2, Math.sin(Math.toRadians(pose.pitch())));
                centerX += Math.cos(yaw) * deltaX * perPixel - Math.sin(yaw) * deltaY * alongView;
                centerZ += Math.sin(yaw) * deltaX * perPixel + Math.cos(yaw) * deltaY * alongView;
            } else {
                centerX -= deltaX / pixelsPerBlock;
                centerZ -= deltaY / pixelsPerBlock;
            }
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    private static double square(double value) {
        return value * value;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0) {
            return true;
        }
        if (threeD()) {
            blocksPerPixel = Mth.clamp(blocksPerPixel * Math.pow(0.82, scrollY), MIN_BLOCKS_PER_PIXEL, MAX_BLOCKS_PER_PIXEL);
            return true;
        }
        FlatMapRenderer.View before = view();
        double anchorX = before.worldX(mouseX);
        double anchorZ = before.worldZ(mouseY);
        double zoomed = Mth.clamp(pixelsPerBlock * Math.pow(ZOOM_STEP, scrollY), MIN_PIXELS_PER_BLOCK, MAX_PIXELS_PER_BLOCK);
        if (zoomed == pixelsPerBlock) {
            return true;
        }
        pixelsPerBlock = zoomed;
        if (before.contains(mouseX, mouseY) && !following) {
            FlatMapRenderer.View after = view();
            centerX += anchorX - after.worldX(mouseX);
            centerZ += anchorZ - after.worldZ(mouseY);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == InputConstants.KEY_ESCAPE || keyCode == InputConstants.KEY_M) {
            onClose();
            return true;
        }
        if (keyCode == InputConstants.KEY_C) {
            following = true;
            return true;
        }
        if (keyCode == InputConstants.KEY_W || keyCode == InputConstants.KEY_S || keyCode == InputConstants.KEY_A
                || keyCode == InputConstants.KEY_D || keyCode == InputConstants.KEY_UP || keyCode == InputConstants.KEY_DOWN
                || keyCode == InputConstants.KEY_LEFT || keyCode == InputConstants.KEY_RIGHT) {
            return true;
        }
        return super.keyPressed(event);
    }

    private void openFullMap() {
        MinecraftCompat.showScreen(minecraft, new MapScreen());
    }

    @Override
    public void tick() {
        NetherRoofController.tick(minecraft);
    }

    @Override
    public void removed() {
        NetherRoofController.deactivate();
        WindowMapRenderer.release();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }
}
