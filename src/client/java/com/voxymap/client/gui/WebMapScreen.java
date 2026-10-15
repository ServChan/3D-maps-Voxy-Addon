package com.voxymap.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.voxymap.client.VoxyMapClient;
import com.voxymap.client.compat.MinecraftCompat;
import com.voxymap.client.integration.VanillaSquadWebMap;
import com.voxymap.client.map.FlatMapRenderer;
import com.voxymap.client.map.MinimapRenderer;
import com.voxymap.client.map.VoxyMapSettings;
import com.voxymap.client.map.WebMapGeometry;
import com.voxymap.client.map.WebMapRenderer;
import com.voxymap.client.map.WebMapTiles;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.sdl.SDLScancode;

import java.util.ArrayList;
import java.util.List;

public final class WebMapScreen extends Screen {
    private static final int MARGIN = 8;
    private static final int TITLE_ROW_HEIGHT = 24;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_GAP = 4;
    private static final int FOOTER_HEIGHT = 16;
    private static final double MIN_PIXELS_PER_BLOCK = 1.0 / 64.0;
    private static final double MAX_PIXELS_PER_BLOCK = 8.0;
    private static final double ZOOM_STEP = 1.25;
    private static final double MAX_KEYBOARD_SPEED = 2048.0;
    private static final int BACKGROUND = 0xFF0B1220;
    private static final int HEADER_BACKGROUND = 0xF0030914;
    private static final int FRAME = 0xFF3BA4FF;
    private static final int SELECTED = 0xFF7DEBFF;
    private static final int TEXT = 0xFFDFF8FF;
    private static final int MUTED_TEXT = 0xFF9FB4C8;
    private static final int WARNING_TEXT = 0xFFFFC266;

    private final String preferredMapId;
    private final String preferredDimension;
    private double centerX;
    private double centerZ;
    private String centerDimension;
    private double pixelsPerBlock;
    private String selectedKey;
    private VanillaSquadWebMap.World selected;
    private List<String> shownKeys = List.of();
    private final List<Button> worldButtons = new ArrayList<>();
    private final List<VanillaSquadWebMap.World> buttonWorlds = new ArrayList<>();
    private int headerHeight = TITLE_ROW_HEIGHT;
    private boolean dragging;
    private long lastFrameNanos;
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;

    public WebMapScreen(String currentMapId, String currentDimension, double centerX, double centerZ,
                        double blocksPerPixel) {
        super(Component.translatable("screen.voxymap.webmap.title"));
        this.preferredMapId = currentMapId;
        this.preferredDimension = currentDimension;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.centerDimension = currentDimension;
        this.pixelsPerBlock = Mth.clamp(1.0 / Math.max(1.0E-3, blocksPerPixel), MIN_PIXELS_PER_BLOCK, MAX_PIXELS_PER_BLOCK);
    }

    @Override
    protected void init() {
        VanillaSquadWebMap.refresh();
        resolveSelection();
        worldButtons.clear();
        buttonWorlds.clear();
        Component backLabel = Component.translatable("screen.voxymap.flat.open_3d");
        int backWidth = font.width(backLabel) + 16;
        addRenderableWidget(Button.builder(backLabel, button -> openFullMap())
                .bounds(width - MARGIN - backWidth, (TITLE_ROW_HEIGHT - BUTTON_HEIGHT) / 2, backWidth, BUTTON_HEIGHT).build());

        List<VanillaSquadWebMap.World> worlds = VanillaSquadWebMap.worlds();
        shownKeys = buttonSignature(worlds);
        int x = MARGIN;
        int y = TITLE_ROW_HEIGHT;
        for (int i = 0; i < worlds.size(); i++) {
            VanillaSquadWebMap.World world = worlds.get(i);
            Component label = worldLabel(world, i);
            int buttonWidth = Math.min(width - MARGIN * 2, font.width(label) + 14);
            if (x > MARGIN && x + buttonWidth > width - MARGIN) {
                x = MARGIN;
                y += BUTTON_HEIGHT + BUTTON_GAP;
            }
            Button button = addRenderableWidget(Button.builder(label, pressed -> select(world))
                    .bounds(x, y, buttonWidth, BUTTON_HEIGHT).build());
            worldButtons.add(button);
            buttonWorlds.add(world);
            x += buttonWidth + BUTTON_GAP;
        }
        headerHeight = worlds.isEmpty() ? TITLE_ROW_HEIGHT : y + BUTTON_HEIGHT + 6;
    }

    private Component worldLabel(VanillaSquadWebMap.World world, int index) {
        String map = mapLabel(world.mapId());
        String dimension = world.dimensionId();
        String label;
        if ("minecraft:overworld".equals(dimension)) {
            label = map;
        } else if (WebMapGeometry.isNether(dimension)) {
            label = map + " · " + Component.translatable("screen.voxymap.webmap.nether").getString();
        } else if (WebMapGeometry.isEnd(dimension)) {
            label = map + " · " + Component.translatable("screen.voxymap.webmap.end").getString();
        } else {
            label = map + " · " + world.displayName();
        }
        if (isCurrentWorld(world)) {
            label += " " + Component.translatable("screen.voxymap.webmap.here").getString();
        }
        return Component.literal(index < 9 ? (index + 1) + "  " + label : label);
    }

    private static String mapLabel(String mapId) {
        for (VoxyMapSettings.VanillaSquadMap map : VoxyMapSettings.VanillaSquadMap.values()) {
            if (mapId.equals(map.mapId())) {
                return Component.translatable(map.translationKey()).getString();
            }
        }
        return mapId;
    }

    private boolean isCurrentWorld(VanillaSquadWebMap.World world) {
        if (minecraft == null || minecraft.level == null) {
            return false;
        }
        return world.mapId().equals(VanillaSquadWebMap.currentMapId(minecraft))
                && world.dimensionId().equals(minecraft.level.dimension().identifier().toString());
    }

    private void resolveSelection() {
        List<VanillaSquadWebMap.World> worlds = VanillaSquadWebMap.worlds();
        if (worlds.isEmpty()) {
            selected = null;
            return;
        }
        if (selectedKey != null) {
            for (VanillaSquadWebMap.World world : worlds) {
                if (world.key().equals(selectedKey)) {
                    selected = world;
                    return;
                }
            }
        }
        String preferredWorld = WebMapGeometry.worldNameOf(preferredDimension);
        VanillaSquadWebMap.World choice = null;
        for (VanillaSquadWebMap.World world : worlds) {
            boolean sameMap = world.mapId().equals(preferredMapId);
            boolean sameWorld = world.name().equals(preferredWorld);
            if (sameMap && sameWorld) {
                choice = world;
                break;
            }
            if (choice == null && sameWorld) {
                choice = world;
            }
        }
        boolean exact = choice != null && choice.mapId().equals(preferredMapId);
        if (!exact && VanillaSquadWebMap.loading()) {
            selected = null;
            return;
        }
        select(choice != null ? choice : worlds.getFirst());
    }

    private void select(VanillaSquadWebMap.World world) {
        double scale = WebMapGeometry.scaleBetween(centerDimension, world.dimensionId());
        centerX *= scale;
        centerZ *= scale;
        centerDimension = world.dimensionId();
        selected = world;
        selectedKey = world.key();
        dragging = false;
    }

    private FlatMapRenderer.View view() {
        int top = headerHeight;
        return new FlatMapRenderer.View(0, top, Math.max(1, width), Math.max(1, height - top - FOOTER_HEIGHT),
                centerX, centerZ, pixelsPerBlock);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, BACKGROUND);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null || minecraft.player == null) {
            onClose();
            return;
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        moveWithKeyboard();
        FlatMapRenderer.View view = view();
        if (selected != null) {
            boolean current = isCurrentWorld(selected);
            String linked = current ? MinimapRenderer.currentWorldId(minecraft)
                    : VoxyMapSettings.linkedWebMapWorld(selected.mapId(), selected.name());
            WebMapRenderer.extract(g, minecraft, view, selected, new WebMapRenderer.Overlays(current, linked));
        } else {
            String key = VanillaSquadWebMap.loading() ? "screen.voxymap.webmap.loading_worlds"
                    : "screen.voxymap.webmap.unavailable";
            g.centeredText(font, Component.translatable(key), width / 2, view.top() + view.height() / 2,
                    VanillaSquadWebMap.loading() ? MUTED_TEXT : WARNING_TEXT);
        }

        g.fill(0, 0, width, headerHeight, HEADER_BACKGROUND);
        g.fill(0, headerHeight - 1, width, headerHeight, FRAME);
        int titleY = (TITLE_ROW_HEIGHT - font.lineHeight) / 2 + 1;
        g.text(font, title, MARGIN, titleY, TEXT);
        if (view.contains(mouseX, mouseY)) {
            String cursor = (int) Math.floor(view.worldX(mouseX)) + ", " + (int) Math.floor(view.worldZ(mouseY));
            g.text(font, cursor, MARGIN + font.width(title) + 10, titleY, MUTED_TEXT);
        }
        for (int i = 0; i < worldButtons.size(); i++) {
            Button button = worldButtons.get(i);
            if (buttonWorlds.get(i).key().equals(selectedKey)) {
                g.fill(button.getX() - 1, button.getY() - 1, button.getX() + button.getWidth() + 1,
                        button.getY() + button.getHeight() + 1, SELECTED);
            }
        }

        int footerY = height - FOOTER_HEIGHT;
        g.fill(0, footerY, width, height, HEADER_BACKGROUND);
        String source = WebMapTiles.pending() > 0
                ? Component.translatable("screen.voxymap.webmap.loading_tiles").getString()
                : Component.translatable("screen.voxymap.webmap.source").getString();
        String hints = Component.translatable("screen.voxymap.webmap.hints").getString();
        int available = Math.max(0, width - MARGIN * 2);
        boolean sourceFits = font.width(hints) + 12 + font.width(source) <= available;
        if (sourceFits) {
            g.text(font, source, width - MARGIN - font.width(source), footerY + 4, MUTED_TEXT);
        }
        g.text(font, font.plainSubstrByWidth(hints, available), MARGIN, footerY + 4, MUTED_TEXT);
        super.extractRenderState(g, mouseX, mouseY, delta);
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
        double boost = keyDown(InputConstants.KEY_LSHIFT) || keyDown(InputConstants.KEY_RSHIFT) ? 2.5 : 1.0;
        double speed = Math.min(Math.max(12.0, 76.0 / pixelsPerBlock), MAX_KEYBOARD_SPEED) * boost
                * VoxyMapSettings.cameraSpeedMultiplier() * dt / Math.max(1.0, length);
        centerX -= strafe * speed;
        centerZ -= forward * speed;
    }

    private static boolean keyDown(int key) {
        return InputConstants.isKeyDown(key);
    }

    @Override
    public void tick() {
        if (!VanillaSquadWebMap.available()) {
            onClose();
            return;
        }
        VanillaSquadWebMap.refresh();
        List<VanillaSquadWebMap.World> worlds = VanillaSquadWebMap.worlds();
        if (!buttonSignature(worlds).equals(shownKeys)) {
            rebuildWidgets();
        } else if (selected == null) {
            resolveSelection();
        } else {
            for (VanillaSquadWebMap.World world : worlds) {
                if (world.key().equals(selectedKey)) {
                    selected = world;
                    break;
                }
            }
        }
    }

    private List<String> buttonSignature(List<VanillaSquadWebMap.World> worlds) {
        return worlds.stream().map(world -> world.key() + (isCurrentWorld(world) ? "*" : "")).toList();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isInside) {
        if (super.mouseClicked(event, isInside)) {
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && view().contains(event.x(), event.y())) {
            dragging = true;
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
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (dragging) {
            centerX -= deltaX / pixelsPerBlock;
            centerZ -= deltaY / pixelsPerBlock;
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0) {
            zoomAt(mouseX, mouseY, Math.pow(ZOOM_STEP, scrollY));
        }
        return true;
    }

    private void zoomAt(double mouseX, double mouseY, double factor) {
        FlatMapRenderer.View before = view();
        double zoomed = Mth.clamp(pixelsPerBlock * factor, MIN_PIXELS_PER_BLOCK, MAX_PIXELS_PER_BLOCK);
        if (zoomed == pixelsPerBlock) {
            return;
        }
        double anchorX = before.worldX(mouseX);
        double anchorZ = before.worldZ(mouseY);
        pixelsPerBlock = zoomed;
        if (before.contains(mouseX, mouseY)) {
            FlatMapRenderer.View after = view();
            centerX += anchorX - after.worldX(mouseX);
            centerZ += anchorZ - after.worldZ(mouseY);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == InputConstants.KEY_ESCAPE || VoxyMapClient.isOpenMapKey(event)) {
            onClose();
            return true;
        }
        if (keyCode == InputConstants.KEY_C && selected != null) {
            centerOnFocus();
            return true;
        }
        if (keyCode == InputConstants.KEY_EQUALS || keyCode == InputConstants.KEY_ADD) {
            zoomAt(zoomAnchorX(), zoomAnchorY(), ZOOM_STEP);
            return true;
        }
        if (keyCode == InputConstants.KEY_MINUS || keyCode == SDLScancode.SDL_SCANCODE_KP_MINUS) {
            zoomAt(zoomAnchorX(), zoomAnchorY(), 1.0 / ZOOM_STEP);
            return true;
        }
        int index = worldIndexForKey(keyCode);
        if (index >= 0 && index < buttonWorlds.size()) {
            select(buttonWorlds.get(index));
            return true;
        }
        if (keyCode == InputConstants.KEY_W || keyCode == InputConstants.KEY_S || keyCode == InputConstants.KEY_A
                || keyCode == InputConstants.KEY_D || keyCode == InputConstants.KEY_UP || keyCode == InputConstants.KEY_DOWN
                || keyCode == InputConstants.KEY_LEFT || keyCode == InputConstants.KEY_RIGHT) {
            return true;
        }
        return super.keyPressed(event);
    }

    private static int worldIndexForKey(int keyCode) {
        int[] keys = {InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3, InputConstants.KEY_4,
                InputConstants.KEY_5, InputConstants.KEY_6, InputConstants.KEY_7, InputConstants.KEY_8,
                InputConstants.KEY_9};
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == keyCode) {
                return i;
            }
        }
        return -1;
    }

    private void centerOnFocus() {
        Player player = minecraft.player;
        if (player != null && isCurrentWorld(selected)) {
            centerX = player.getX();
            centerZ = player.getZ();
        } else {
            centerX = selected.spawnX() + 0.5;
            centerZ = selected.spawnZ() + 0.5;
        }
    }

    private double zoomAnchorX() {
        return Double.isNaN(lastMouseX) ? width / 2.0 : lastMouseX;
    }

    private double zoomAnchorY() {
        return Double.isNaN(lastMouseY) ? height / 2.0 : lastMouseY;
    }

    private void openFullMap() {
        MinecraftCompat.showScreen(minecraft, new MapScreen());
    }

    @Override
    public void removed() {
        WebMapTiles.clear(minecraft);
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
