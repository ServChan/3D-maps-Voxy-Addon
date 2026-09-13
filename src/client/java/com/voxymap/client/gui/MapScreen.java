package com.voxymap.client.gui;

import com.voxymap.client.VoxyMapClient;
import com.voxymap.client.integration.HappyGhastScannerBridge;
import com.voxymap.client.integration.VanillaSquadFlagMapBridge;
import com.voxymap.client.integration.VanillaSquadPlayerMapBridge;
import com.voxymap.client.integration.XaeroWorldMapBridge;
import com.voxymap.client.map.MapRenderSettingsGuard;
import com.voxymap.client.map.PlayerViewConeGeometry;
import com.voxymap.client.map.RoomCutawayController;
import com.voxymap.client.map.SpiralRouteGeometry;
import com.voxymap.client.map.VoxyBridge;
import com.voxymap.client.map.VoxyMapCameraController;
import com.voxymap.client.map.VoxyMapGuiRenderer;
import com.voxymap.client.map.VoxyMapSettings;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MapScreen extends Screen {

    private static final Component TITLE = Component.translatable("screen.voxymap.title");

    private static final long OPEN_ANIMATION_NANOS = 850_000_000L;
    private static final long XAERO_TRANSITION_NANOS = 380_000_000L;
    private static final double MAX_KEYBOARD_CAMERA_SPEED = 640.0;
    private static final double MAX_DRAG_STEP_BLOCKS = 1536.0;
    private static final double PLAYER_VIEW_CONE_HALF_ANGLE = 32.0;
    private static final double PLAYER_VIEW_CONE_SCREEN_LENGTH = 34.0;
    // 4 segments per lap: 24 back / 16 ahead covers ~6 already-flown and ~4 upcoming laps around the
    // scanner's current position, which is the stretch relevant to picking a lap to resume from.
    private static final int GHAST_ROUTE_LOOKBACK_SEGMENTS = 24;
    private static final int GHAST_ROUTE_LOOKAHEAD_SEGMENTS = 16;
    private static final double PLAYER_VIEW_CONE_CROWD_DISTANCE = 52.0;
    private static final double PLAYER_MARKER_HOVER_DISTANCE = 12.0;
    private static final int PLAYER_LABEL_CELL_WIDTH = 56;
    private static final int PLAYER_LABEL_CELL_HEIGHT = 14;
    private static final long SKIN_RETRY_INTERVAL_NANOS = 5_000_000_000L;
    private double viewCenterX;
    private double viewCenterY;
    private double viewCenterZ;
    private double blocksPerPixel = 4.0;
    private double viewYaw = Math.toRadians(45.0);
    private double viewPitch = 0.68;

    private boolean dragging = false;
    private boolean middleDragging = false;
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;
    private double moveVelocityX = 0.0;
    private double moveVelocityZ = 0.0;
    private List<PlayerMarker> playerMarkers = List.of();
    private final Map<UUID, Identifier> skinTextureCache = new HashMap<>();
    private final Map<UUID, Long> missingSkinRetryNanos = new HashMap<>();
    private HappyGhastScannerBridge.Route cachedGhastRoute;
    private List<SpiralRouteGeometry.Waypoint> cachedGhastRoutePoints = List.of();
    private int cachedGhastFirstSegment;
    private String[][] cachedKeyHints = new String[0][0];
    private long lastFrameNanos = 0L;
    private long openedAtNanos = 0L;
    private boolean openLogged = false;
    private boolean firstInitDone = false;
    private boolean xaeroTransitionActive = false;
    private boolean xaeroOpenScheduled = false;
    private long xaeroTransitionStartedAt = 0L;
    private int xaeroButtonX = -1;
    private int xaeroButtonY = -1;
    private int xaeroButtonW = 0;
    private int xaeroButtonH = 0;
    private boolean settingsOpen = false;
    private int settingsButtonX = -1;
    private int settingsButtonY = -1;
    private int settingsButtonW = 0;
    private int settingsButtonH = 0;
    private int settingsPanelX = -1;
    private int settingsPanelY = -1;
    private int settingsPanelW = 0;
    private int settingsPanelH = 0;
    private int speedMinusX = -1;
    private int speedMinusY = -1;
    private int speedPlusX = -1;
    private int speedPlusY = -1;
    private int pauseRowY = -1;
    private int speedRowY = -1;
    private int cutawayRowY = -1;
    private int vanillaSquadRowY = -1;
    private int vanillaSquadFlagsRowY = -1;
    private int vanillaSquadMapRowY = -1;
    private int ghastScannerRowY = -1;
    private static final int SETTINGS_ROW_HEIGHT = 24;
    private static final int SPEED_BUTTON_SIZE = 18;
    private static final int HUD_MARGIN = 12;
    private static final int HUD_GAP = 6;
    private static final int HUD_BUTTON_HEIGHT = 24;
    private static final int KEY_HINT_HEIGHT = 22;

    public MapScreen() {
        super(TITLE);
    }

    @Override
    protected void init() {
        super.init();
        // Vanilla replays init() on this same screen instance for a window resize, GUI-scale change,
        // or alt-tab, not just on first open. Only the very first call should reset the opening
        // animation, recenter the view, and (re)open the map renderer - otherwise every resize
        // restarts the fade-in and snaps the view back to the player, discarding the user's pan/zoom.
        if (!firstInitDone) {
            firstInitDone = true;
            openedAtNanos = System.nanoTime();
            lastFrameNanos = 0L;
            if (minecraft.player != null) {
                centerOnPlayer();
            }
            VoxyMapGuiRenderer.open(minecraft);
        }
        capturePlayerMarkers();
        cachedKeyHints = buildKeyHints();
        VoxyBridge.suppressEnvironmentalFogForMap();
        MapRenderSettingsGuard.applyForMap(minecraft);
        syncWorldCamera();
        RoomCutawayController.tick(minecraft);
        if (!openLogged) {
            VoxyMapClient.LOGGER.info("[VoxyMap] Map opened.");
            openLogged = true;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        updateSmoothControls();
        VoxyBridge.suppressEnvironmentalFogForMap();
        MapRenderSettingsGuard.applyForMap(minecraft);
        syncWorldCamera();
        drawWorldViewerOverlay(g, mouseX, mouseY);
        drawOpeningAnimation(g);
        if (xaeroTransitionActive) {
            drawXaeroTransition(g);
            if (!xaeroOpenScheduled && System.nanoTime() - xaeroTransitionStartedAt >= XAERO_TRANSITION_NANOS) {
                // Screen replacement can invoke removed() and restore renderer state. Defer it until
                // after render-state extraction rather than mutating the screen stack mid-frame.
                xaeroOpenScheduled = true;
                minecraft.execute(() -> {
                    if (xaeroTransitionActive) {
                        openXaeroWorldMap();
                    } else {
                        xaeroOpenScheduled = false;
                    }
                });
            }
        }
    }

    private double openingProgress() {
        if (openedAtNanos == 0L) return 1.0;
        double raw = (System.nanoTime() - openedAtNanos) / (double) OPEN_ANIMATION_NANOS;
        raw = Mth.clamp(raw, 0.0, 1.0);
        return 1.0 - Math.pow(1.0 - raw, 3.0);
    }

    private void updateSmoothControls() {
        if (minecraft == null || minecraft.getWindow() == null) return;

        long now = System.nanoTime();
        double dt = lastFrameNanos == 0L ? 1.0 / 60.0 : Math.min(0.08, (now - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = now;

        long window = minecraft.getWindow().handle();
        double forwardInput = keyDown(window, GLFW.GLFW_KEY_W) || keyDown(window, GLFW.GLFW_KEY_UP) ? 1.0 : 0.0;
        forwardInput -= keyDown(window, GLFW.GLFW_KEY_S) || keyDown(window, GLFW.GLFW_KEY_DOWN) ? 1.0 : 0.0;
        double strafeInput = keyDown(window, GLFW.GLFW_KEY_A) || keyDown(window, GLFW.GLFW_KEY_LEFT) ? 1.0 : 0.0;
        strafeInput -= keyDown(window, GLFW.GLFW_KEY_D) || keyDown(window, GLFW.GLFW_KEY_RIGHT) ? 1.0 : 0.0;

        double horizontalInput = Math.hypot(forwardInput, strafeInput);
        if (horizontalInput > 1.0) {
            forwardInput /= horizontalInput;
            strafeInput /= horizontalInput;
        }

        double speedBoost = keyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) || keyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT) ? 2.5 : 1.0;
        double moveSpeed = Math.max(12.0, blocksPerPixel * 76.0) * speedBoost * VoxyMapSettings.cameraSpeedMultiplier();
        moveSpeed = Math.min(moveSpeed, MAX_KEYBOARD_CAMERA_SPEED * speedBoost);
        double forwardX = -Math.sin(viewYaw);
        double forwardZ = Math.cos(viewYaw);
        double rightX = Math.cos(viewYaw);
        double rightZ = Math.sin(viewYaw);
        double targetVX = dragging ? 0.0 : (forwardX * forwardInput + rightX * strafeInput) * moveSpeed;
        double targetVZ = dragging ? 0.0 : (forwardZ * forwardInput + rightZ * strafeInput) * moveSpeed;

        double response = 1.0 - Math.exp(-dt * 10.0);
        moveVelocityX = Mth.lerp(response, moveVelocityX, targetVX);
        moveVelocityZ = Mth.lerp(response, moveVelocityZ, targetVZ);

        if (!dragging) {
            viewCenterX += moveVelocityX * dt;
            viewCenterZ += moveVelocityZ * dt;
        }
    }

    private static boolean keyDown(long window, int key) {
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }

    private GroundPoint mapPointAt(double mouseX, double mouseY) {
        if (width <= 0 || height <= 0 || !VoxyMapCameraController.isActive()) {
            return null;
        }

        double cameraX = VoxyMapCameraController.cameraX();
        double cameraY = VoxyMapCameraController.cameraY();
        double cameraZ = VoxyMapCameraController.cameraZ();
        double pitch = Math.toRadians(VoxyMapCameraController.cameraPitch());
        double planeY = viewCenterY;

        double forwardX = -Math.sin(viewYaw) * Math.cos(pitch);
        double forwardY = -Math.sin(pitch);
        double forwardZ = Math.cos(viewYaw) * Math.cos(pitch);
        double rightX = Math.cos(viewYaw);
        double rightZ = Math.sin(viewYaw);
        double upX = -Math.sin(viewYaw) * Math.sin(pitch);
        double upY = Math.cos(pitch);
        double upZ = Math.cos(viewYaw) * Math.sin(pitch);

        double tanY = Math.tan(Math.toRadians(VoxyMapCameraController.fov()) * 0.5);
        double tanX = tanY * width / (double) height;
        double ndcX = 1.0 - mouseX / width * 2.0;
        double ndcY = 1.0 - mouseY / height * 2.0;
        double rayX = forwardX + rightX * ndcX * tanX + upX * ndcY * tanY;
        double rayY = forwardY + upY * ndcY * tanY;
        double rayZ = forwardZ + rightZ * ndcX * tanX + upZ * ndcY * tanY;

        if (Math.abs(rayY) < 1.0E-6) {
            return null;
        }

        double t = (planeY - cameraY) / rayY;
        if (t <= 0.0 || !Double.isFinite(t)) {
            return null;
        }
        return new GroundPoint(cameraX + rayX * t, cameraZ + rayZ * t);
    }

    private void keepMapPointUnderMouse(GroundPoint anchor, double mouseX, double mouseY) {
        GroundPoint current = mapPointAt(mouseX, mouseY);
        if (anchor == null || current == null) {
            return;
        }
        viewCenterX += anchor.x - current.x;
        viewCenterZ += anchor.z - current.z;
    }

    private void panByPixels(double deltaX, double deltaY) {
        double rightX = Math.cos(viewYaw);
        double rightZ = Math.sin(viewYaw);
        double forwardX = -Math.sin(viewYaw);
        double forwardZ = Math.cos(viewYaw);
        double pitch = Math.toRadians(VoxyMapCameraController.cameraPitch());
        double forwardScale = 1.0 / Math.max(0.35, Math.sin(pitch));
        double sideDistance = -deltaX * blocksPerPixel;
        double forwardDistance = -deltaY * blocksPerPixel * forwardScale;
        double distance = Math.hypot(sideDistance, forwardDistance);
        if (distance > MAX_DRAG_STEP_BLOCKS) {
            double scale = MAX_DRAG_STEP_BLOCKS / distance;
            sideDistance *= scale;
            forwardDistance *= scale;
        }

        viewCenterX += rightX * sideDistance + forwardX * forwardDistance;
        viewCenterZ += rightZ * sideDistance + forwardZ * forwardDistance;
    }

    @Override
    public void tick() {
        // Feed the newest pan/zoom context before the cutaway tick updates shader state.
        syncWorldCamera();
        RoomCutawayController.tick(minecraft);
        if (minecraft != null && minecraft.level != null) {
            String dimensionId = minecraft.level.dimension().identifier().toString();
            if (minecraft.player != null) {
                List<VanillaSquadPlayerMapBridge.Observer> observers = minecraft.level.players().stream()
                        .map(player -> new VanillaSquadPlayerMapBridge.Observer(
                                VanillaSquadPlayerMapBridge.normalizeUuid(player.getUUID().toString()),
                                player.getName().getString(), player.getX(), player.getZ()))
                        .toList();
                VanillaSquadPlayerMapBridge.tick(minecraft.level, dimensionId, minecraft.player.getUUID(),
                        minecraft.player.getName().getString(), minecraft.player.getX(), minecraft.player.getZ(), observers,
                        currentVoxyWorldId());
            }
            VanillaSquadFlagMapBridge.tick(VanillaSquadPlayerMapBridge.activeMapId(), dimensionId);
        }
        capturePlayerMarkers();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
    }

    private void syncWorldCamera() {
        RoomCutawayController.updateViewContext(
                minecraft, viewCenterX, viewCenterY, viewCenterZ, blocksPerPixel, Math.min(width, height));
        if (minecraft != null && minecraft.level != null && minecraft.player != null) {
            VoxyMapCameraController.update(minecraft, viewCenterX, viewCenterY, viewCenterZ, viewYaw, viewPitch, blocksPerPixel);
        } else {
            VoxyMapCameraController.deactivate();
        }
    }

    private String[][] buildKeyHints() {
        return new String[][] {
                {Component.translatable("overlay.voxymap.key.drag.button").getString(), Component.translatable("overlay.voxymap.key.drag").getString()},
                {Component.translatable("overlay.voxymap.key.rotate.button").getString(), Component.translatable("overlay.voxymap.key.rotate").getString()},
                {Component.translatable("overlay.voxymap.key.move.button").getString(), Component.translatable("overlay.voxymap.key.move").getString()},
                {Component.translatable("overlay.voxymap.key.fast.button").getString(), Component.translatable("overlay.voxymap.key.fast").getString()},
                {Component.translatable("overlay.voxymap.key.zoom.button").getString(), Component.translatable("overlay.voxymap.key.zoom").getString()},
                {Component.translatable("overlay.voxymap.key.center.button").getString(), Component.translatable("overlay.voxymap.key.center").getString()},
                {Component.translatable("overlay.voxymap.key.close.button").getString(), Component.translatable("overlay.voxymap.key.close").getString()}
        };
    }

    private int keyHintsWidth(String[][] hints) {
        int width = 0;
        for (String[] hint : hints) {
            width += hintWidth(hint) + HUD_GAP;
        }
        return width - HUD_GAP;
    }

    private int hintWidth(String[] hint) {
        return minecraft.font.width(hint[0]) + minecraft.font.width(hint[1]) + 22;
    }

    private String onOff(boolean enabled) {
        return Component.translatable(enabled ? "screen.voxymap.settings.on" : "screen.voxymap.settings.off").getString();
    }

    private boolean isInsideXaeroButton(double mouseX, double mouseY) {
        return xaeroButtonW > 0
                && mouseX >= xaeroButtonX && mouseX <= xaeroButtonX + xaeroButtonW
                && mouseY >= xaeroButtonY && mouseY <= xaeroButtonY + xaeroButtonH;
    }

    private boolean isInsideSettingsButton(double mouseX, double mouseY) {
        return settingsButtonW > 0
                && mouseX >= settingsButtonX && mouseX <= settingsButtonX + settingsButtonW
                && mouseY >= settingsButtonY && mouseY <= settingsButtonY + settingsButtonH;
    }

    private boolean isInsideSettingsPanel(double mouseX, double mouseY) {
        return settingsOpen && settingsPanelW > 0
                && mouseX >= settingsPanelX && mouseX <= settingsPanelX + settingsPanelW
                && mouseY >= settingsPanelY && mouseY <= settingsPanelY + settingsPanelH;
    }

    private boolean handleSettingsClick(double mouseX, double mouseY) {
        if (isInsideSettingsButton(mouseX, mouseY)) {
            settingsOpen = !settingsOpen;
            return true;
        }
        if (!isInsideSettingsPanel(mouseX, mouseY)) {
            if (settingsOpen) {
                settingsOpen = false;
                return true;
            }
            return false;
        }

        if (mouseX >= speedMinusX && mouseX <= speedMinusX + SPEED_BUTTON_SIZE
                && mouseY >= speedMinusY && mouseY <= speedMinusY + SPEED_BUTTON_SIZE) {
            VoxyMapSettings.changeCameraSpeed(-0.25);
            return true;
        }
        if (mouseX >= speedPlusX && mouseX <= speedPlusX + SPEED_BUTTON_SIZE
                && mouseY >= speedPlusY && mouseY <= speedPlusY + SPEED_BUTTON_SIZE) {
            VoxyMapSettings.changeCameraSpeed(0.25);
            return true;
        }

        if (isInsideSettingsRow(mouseX, mouseY, pauseRowY)) {
            VoxyMapSettings.togglePauseSingleplayer();
        } else if (isInsideSettingsRow(mouseX, mouseY, speedRowY)) {
            double rowMid = settingsPanelX + settingsPanelW / 2.0;
            VoxyMapSettings.changeCameraSpeed(mouseX < rowMid ? -0.25 : 0.25);
        } else if (isInsideSettingsRow(mouseX, mouseY, cutawayRowY)) {
            VoxyMapSettings.toggleRoomCutaway();
        } else if (isInsideSettingsRow(mouseX, mouseY, vanillaSquadRowY)) {
            VoxyMapSettings.toggleVanillaSquadPlayers();
        } else if (isInsideSettingsRow(mouseX, mouseY, vanillaSquadFlagsRowY)) {
            VoxyMapSettings.toggleVanillaSquadFlags();
        } else if (isInsideSettingsRow(mouseX, mouseY, vanillaSquadMapRowY)) {
            VoxyMapSettings.cycleVanillaSquadMap(currentVoxyWorldId());
        } else if (isInsideSettingsRow(mouseX, mouseY, ghastScannerRowY)) {
            VoxyMapSettings.toggleGhastScannerRoute();
        }
        return true;
    }

    private boolean isInsideSettingsRow(double mouseX, double mouseY, int rowY) {
        return rowY >= 0
                && mouseX >= settingsPanelX + 8 && mouseX <= settingsPanelX + settingsPanelW - 8
                && mouseY >= rowY && mouseY <= rowY + SETTINGS_ROW_HEIGHT - 2;
    }

    private void startXaeroTransition() {
        if (xaeroTransitionActive) return;
        dragging = false;
        middleDragging = false;
        xaeroTransitionActive = true;
        xaeroOpenScheduled = false;
        xaeroTransitionStartedAt = System.nanoTime();
    }

    private void openXaeroWorldMap() {
        VoxyMapGuiRenderer.close(minecraft);
        VoxyMapCameraController.deactivate();
        VoxyBridge.restoreEnvironmentalFogAfterMap();
        MapRenderSettingsGuard.restoreAfterMap(minecraft);

        if (XaeroWorldMapBridge.openWorldMap(minecraft)) {
            return;
        }

        VoxyBridge.suppressEnvironmentalFogForMap();
        MapRenderSettingsGuard.applyForMap(minecraft);
        syncWorldCamera();
        VoxyMapGuiRenderer.open(minecraft);
        xaeroTransitionActive = false;
        xaeroOpenScheduled = false;
        xaeroTransitionStartedAt = 0L;
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.translatable("message.voxymap.xaero_open_failed"));
        }
    }

    private void drawOpeningAnimation(GuiGraphicsExtractor g) {
        double progress = openingProgress();
        if (progress >= 0.995) return;

        int alpha = (int) (185 * (1.0 - progress));
        int dim = (alpha << 24) | 0x030914;
        g.fill(0, 0, width, height, dim);

        int bandHeight = (int) ((height * 0.28) * (1.0 - progress));
        if (bandHeight > 0) {
            g.fill(0, 0, width, bandHeight, 0xDD030914);
            g.fill(0, height - bandHeight, width, height, 0xDD030914);
            g.fill(0, bandHeight, width, bandHeight + 2, 0xFF3BA4FF);
            g.fill(0, height - bandHeight - 2, width, height - bandHeight, 0xFF3BA4FF);
        }

        int scanY = (int) (height * progress);
        g.fill(0, scanY - 1, width, scanY + 1, 0xAA72D7FF);
        String title = "VOXYMAP";
        int titleAlpha = (int) (255 * (1.0 - Math.abs(progress - 0.35) / 0.35));
        if (titleAlpha > 0) {
            int color = (Mth.clamp(titleAlpha, 0, 255) << 24) | 0xDFF8FF;
            g.centeredText(minecraft.font, title, width / 2, height / 2 - 5, color);
        }
    }

    private void drawWorldViewerOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        drawFlagMarkers(g);
        drawGhastScannerRoute(g);
        drawPlayerMarkers(g, mouseX, mouseY);

        int slide = (int) ((1.0 - openingProgress()) * 28.0);
        int topX = HUD_MARGIN;
        int topY = HUD_MARGIN - slide;
        String title = "VoxyMap 3D";
        String playerText = "";
        if (minecraft.player != null) {
            int px = (int) minecraft.player.getX();
            int py = (int) minecraft.player.getY();
            int pz = (int) minecraft.player.getZ();
            playerText = Component.translatable("overlay.voxymap.player").getString() + "  " + px + " / " + py + " / " + pz;
        }
        String cameraText = Component.translatable("overlay.voxymap.camera").getString() + "  " + (int) viewCenterX + " / " + (int) viewCenterY + " / " + (int) viewCenterZ
                + "   " + Component.translatable("overlay.voxymap.zoom").getString() + " " + String.format("%.2f", blocksPerPixel);
        if (RoomCutawayController.isActive()) {
            cameraText += "   " + Component.translatable("overlay.voxymap.slice").getString();
        }
        int topW = Math.max(1, Math.min(width - HUD_MARGIN * 2, Math.max(minecraft.font.width(title), Math.max(minecraft.font.width(playerText), minecraft.font.width(cameraText))) + 20));
        int topH = minecraft.player != null ? 42 : 30;
        drawGlassPanel(g, topX, topY, topW, topH, 0xAA030914, 0xFF3BA4FF);
        g.text(minecraft.font, Component.literal(title), topX + 10, topY + 5, 0xFFFFFFFF);
        if (minecraft.player != null) {
            g.text(minecraft.font, playerText, topX + 10, topY + 18, 0xFFDFF8FF);
        }
        g.text(minecraft.font, cameraText, topX + 10, topY + (minecraft.player != null ? 31 : 18), 0xFF9FD8FF);

        int footerTop = drawFooter(g, slide);
        drawXaeroButton(g, mouseX, mouseY, slide, topX, topY, topW, topH);
        drawSettingsButton(g, mouseX, mouseY, slide);
        if (settingsOpen) {
            drawSettingsPanel(g, footerTop);
        }
    }

    private void drawGlassPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, int bg, int accent) {
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y, x + 2, y + h, accent);
        g.fill(x, y, x + w, y + 1, 0x33FFFFFF);
        g.fill(x, y + h - 1, x + w, y + h, 0x55000000);
    }

    private void capturePlayerMarkers() {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            playerMarkers = List.of();
            return;
        }

        List<PlayerMarker> snapshot = new ArrayList<>();
        Set<String> knownUuids = new HashSet<>();
        Set<String> knownNames = new HashSet<>();
        for (Player player : minecraft.level.players()) {
            knownUuids.add(VanillaSquadPlayerMapBridge.normalizeUuid(player.getUUID().toString()));
            knownNames.add(player.getName().getString().toLowerCase(java.util.Locale.ROOT));
            snapshot.add(new PlayerMarker(
                    player.getName().getString(),
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player == minecraft.player,
                    false,
                    player.getUUID(),
                    player.getYHeadRot()));
        }

        if (VoxyMapSettings.vanillaSquadPlayersEnabled()) {
            String dimensionId = minecraft.level.dimension().identifier().toString();
            for (VanillaSquadPlayerMapBridge.RemotePlayer remote : VanillaSquadPlayerMapBridge.playersInDimension(dimensionId)) {
                if ((remote.uuid() != null && knownUuids.contains(remote.uuid()))
                        || knownNames.contains(remote.name().toLowerCase(java.util.Locale.ROOT))) {
                    continue; // Already shown live from the client's own nearby entity list.
                }
                UUID uuid = VanillaSquadPlayerMapBridge.toJavaUuid(remote.uuid());
                snapshot.add(new PlayerMarker(remote.name(), remote.x(), remote.y(), remote.z(), false, true,
                        uuid, Float.NaN));
            }
        }

        playerMarkers = List.copyOf(snapshot);
    }

    /**
     * Draws each territory as a dashed hull outline plus a single centroid label, instead of one
     * marker and one text-width measurement per raw banner point — a named cluster can have 1000+
     * members, and drawing those individually every frame is what caused the severe frame drop.
     */
    private void drawFlagMarkers(GuiGraphicsExtractor g) {
        if (!VoxyMapCameraController.isActive() || !VoxyMapSettings.vanillaSquadFlagsEnabled()
                || minecraft == null || minecraft.level == null) {
            return;
        }

        String dimensionId = minecraft.level.dimension().identifier().toString();
        for (VanillaSquadFlagMapBridge.Territory territory : VanillaSquadFlagMapBridge.territoriesInDimension(
                VanillaSquadPlayerMapBridge.activeMapId(), dimensionId)) {
            List<double[]> vertices = territory.outline();
            if (vertices.isEmpty()) {
                continue;
            }

            boolean anyVisible;
            if (vertices.size() == 1) {
                // A named territory with a single banner has no outline to draw, but it must still
                // show up as a marker - otherwise a lone named flag silently vanishes from the map.
                double[] vertex = vertices.get(0);
                ScreenPoint point = projectToScreen(vertex[0], viewCenterY, vertex[1]);
                anyVisible = point != null;
                if (anyVisible) {
                    drawDiamond(g, point.x, point.y, 5, 0xEE0A0A12);
                    drawDiamond(g, point.x, point.y, 3, territory.color());
                }
            } else {
                ScreenPoint[] outline = new ScreenPoint[vertices.size()];
                for (int i = 0; i < vertices.size(); i++) {
                    double[] vertex = vertices.get(i);
                    outline[i] = projectToScreen(vertex[0], viewCenterY, vertex[1]);
                }
                anyVisible = false;
                for (int i = 0; i < outline.length; i++) {
                    ScreenPoint a = outline[i];
                    ScreenPoint b = outline[(i + 1) % outline.length];
                    if (a != null && b != null) {
                        anyVisible |= drawDashedLine(g, a.x, a.y, b.x, b.y, territory.color());
                    }
                }
            }
            if (!anyVisible) {
                continue;
            }

            ScreenPoint labelPoint = projectToScreen(territory.centroidX(), viewCenterY, territory.centroidZ());
            if (labelPoint == null || labelPoint.x < 6 || labelPoint.x >= width - 6
                    || labelPoint.y < 12 || labelPoint.y >= height - 6) {
                continue;
            }
            int labelWidth = minecraft.font.width(territory.name()) + 8;
            int labelX = Mth.clamp(labelPoint.x - labelWidth / 2, 3, Math.max(3, width - labelWidth - 3));
            int labelY = labelPoint.y - 5;
            g.fill(labelX, labelY, labelX + labelWidth, labelY + 11, 0x99030914);
            g.fill(labelX, labelY + 10, labelX + labelWidth, labelY + 11, territory.color());
            g.text(minecraft.font, territory.name(), labelX + 4, labelY + 2, 0xFFE9F2FF);
        }
    }

    /**
     * Draws HappyGhastScanner's expanding square-spiral scan route as a two-tone polyline - already-
     * flown segments dim, upcoming segments bright - plus a lap number at the start of each lap visible
     * in the window and a highlighted marker at the scanner's current waypoint, so the exact point to
     * resume from is readable against terrain already rendered on the map.
     */
    private void drawGhastScannerRoute(GuiGraphicsExtractor g) {
        if (!VoxyMapCameraController.isActive() || !VoxyMapSettings.ghastScannerRouteEnabled()) {
            return;
        }
        HappyGhastScannerBridge.Route route = HappyGhastScannerBridge.currentRoute();
        if (route == null) {
            cachedGhastRoute = null;
            cachedGhastRoutePoints = List.of();
            return;
        }

        if (!route.equals(cachedGhastRoute)) {
            cachedGhastFirstSegment = Math.max(0, route.currentSegment() - GHAST_ROUTE_LOOKBACK_SEGMENTS);
            int lastSegment = route.currentSegment() + GHAST_ROUTE_LOOKAHEAD_SEGMENTS;
            cachedGhastRoutePoints = SpiralRouteGeometry.pathSegment(
                    route.centerX(), route.centerZ(), route.step(), cachedGhastFirstSegment, lastSegment);
            cachedGhastRoute = route;
        }
        int firstSegment = cachedGhastFirstSegment;
        List<SpiralRouteGeometry.Waypoint> points = cachedGhastRoutePoints;

        ScreenPoint previous = projectToScreen(points.get(0).x(), viewCenterY, points.get(0).z());
        for (int i = 1; i < points.size(); i++) {
            int segment = firstSegment + i - 1;
            SpiralRouteGeometry.Waypoint waypoint = points.get(i);
            ScreenPoint current = projectToScreen(waypoint.x(), viewCenterY, waypoint.z());
            if (previous != null && current != null) {
                int color = segment < route.currentSegment() ? 0x996E88A8 : 0xFFFFC94D;
                drawSolidLine(g, previous.x, previous.y, current.x, current.y, color);
            }
            if (current != null && segment % 4 == 0 && segment != route.currentSegment()) {
                drawGhastLapLabel(g, current, waypoint.lap(), false);
            }
            previous = current;
        }

        SpiralRouteGeometry.Waypoint currentWaypoint =
                SpiralRouteGeometry.waypointAt(route.centerX(), route.centerZ(), route.step(), route.currentSegment());
        ScreenPoint here = projectToScreen(currentWaypoint.x(), viewCenterY, currentWaypoint.z());
        if (here != null) {
            drawDiamond(g, here.x, here.y, 6, 0xEE0A0A12);
            drawDiamond(g, here.x, here.y, 4, 0xFFFFC94D);
            drawGhastLapLabel(g, here, route.currentLap(), true);
        }
    }

    private void drawGhastLapLabel(GuiGraphicsExtractor g, ScreenPoint at, int lap, boolean current) {
        if (at.x < 6 || at.x >= width - 6 || at.y < 12 || at.y >= height - 6) {
            return;
        }
        String text = current
                ? Component.translatable("overlay.voxymap.ghastscanner.lap", lap).getString()
                : Integer.toString(lap);
        int labelWidth = minecraft.font.width(text) + (current ? 8 : 6);
        int labelX = at.x - labelWidth / 2;
        int labelY = at.y - (current ? 20 : 16);
        int accent = current ? 0xFFFFC94D : 0xFF6E88A8;
        g.fill(labelX, labelY, labelX + labelWidth, labelY + 11, 0xCC030914);
        g.fill(labelX, labelY, labelX + 2, labelY + 11, accent);
        g.text(minecraft.font, text, labelX + 4, labelY + 2, current ? 0xFFFFE9B8 : 0xFFC7D8EA);
    }

    private boolean drawDashedLine(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        double[] clipped = clipLineToViewport(x1, y1, x2, y2);
        if (clipped == null) {
            return false;
        }
        double dx = clipped[2] - clipped[0];
        double dy = clipped[3] - clipped[1];
        double length = Math.hypot(dx, dy);
        if (length < 1.0) {
            return false;
        }
        int steps = Math.max(1, (int) (length / 5.0));
        for (int i = 0; i <= steps; i += 2) {
            double t = i / (double) steps;
            int px = (int) Math.round(clipped[0] + dx * t);
            int py = (int) Math.round(clipped[1] + dy * t);
            g.fill(px, py, px + 2, py + 2, color);
        }
        return true;
    }

    /** Liang-Barsky clipping bounds work per edge to at most the screen diagonal - for two endpoints
     * that both faithfully represent where their 3D point sits relative to the screen plane. */
    private double[] clipLineToViewport(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double t0 = 0.0;
        double t1 = 1.0;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x1 + 2.0, width + 2.0 - x1, y1 + 2.0, height + 2.0 - y1};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0.0) {
                if (q[i] < 0.0) return null;
                continue;
            }
            double ratio = q[i] / p[i];
            if (p[i] < 0.0) {
                if (ratio > t1) return null;
                t0 = Math.max(t0, ratio);
            } else {
                if (ratio < t0) return null;
                t1 = Math.min(t1, ratio);
            }
        }
        double clippedX1 = x1 + t0 * dx;
        double clippedY1 = y1 + t0 * dy;
        double clippedX2 = x1 + t1 * dx;
        double clippedY2 = y1 + t1 * dy;
        // A world point almost edge-on to the camera (depth just past the near-plane cutoff) still
        // projects to a technically finite screen coordinate, just a wildly wrong one - projection is
        // only meaningful for points in front of the camera, and 2D-clipping an already-bad coordinate
        // cannot recover that. Such a point can still fall inside the padded rectangle above and pass
        // every check, producing a "clipped" segment that streaks across (and past) the whole screen.
        // No legitimate on-screen line needs to be longer than a couple of screen diagonals; a fill
        // loop stepping across one this long - times every such segment drawn that frame - is a severe,
        // silent frame-time hit rather than the outright freeze an unbounded coordinate would cause.
        double maxSpan = 2.0 * Math.hypot(width, height) + 64.0;
        if (Math.abs(clippedX2 - clippedX1) > maxSpan || Math.abs(clippedY2 - clippedY1) > maxSpan) {
            return null;
        }
        return new double[]{clippedX1, clippedY1, clippedX2, clippedY2};
    }

    private void drawPlayerMarkers(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!VoxyMapCameraController.isActive() || playerMarkers.isEmpty()) {
            return;
        }

        List<ProjectedPlayerMarker> projected = new ArrayList<>();
        for (PlayerMarker marker : playerMarkers) {
            ScreenPoint point = marker.local && isViewCenteredOn(marker)
                    ? new ScreenPoint(width / 2, height / 2)
                    : projectToScreen(marker.x, marker.y, marker.z);
            if (point == null || point.x < 8 || point.x >= width - 8 || point.y < 16 || point.y >= height - 8) {
                continue;
            }
            projected.add(new ProjectedPlayerMarker(marker, point));
        }

        ProjectedPlayerMarker hovered = null;
        double hoveredDistance = PLAYER_MARKER_HOVER_DISTANCE;
        for (ProjectedPlayerMarker entry : projected) {
            double distance = Math.hypot(entry.point.x - mouseX, entry.point.y - mouseY);
            if (distance <= hoveredDistance) {
                hovered = entry;
                hoveredDistance = distance;
            }
        }

        // Dense player groups used to turn into an unreadable wall of overlapping names and heads.
        // Keep every direction marker visible, reserve labels for the local/hovered player, and then
        // greedily place only labels with enough screen space. Hidden labels reappear on hover.
        Set<ProjectedPlayerMarker> labeled = new HashSet<>();
        Set<Long> occupiedLabelCells = new HashSet<>();
        for (ProjectedPlayerMarker entry : projected) {
            if (entry.marker.local || entry == hovered) {
                labeled.add(entry);
                occupyLabelCell(entry.point, occupiedLabelCells);
            }
        }
        for (ProjectedPlayerMarker entry : projected) {
            if (!labeled.contains(entry) && claimLabelCell(entry.point, occupiedLabelCells)) {
                labeled.add(entry);
            }
        }

        for (ProjectedPlayerMarker entry : projected) {
            if (entry != hovered) {
                drawPlayerMarker(g, entry, projected, false, labeled.contains(entry));
            }
        }
        if (hovered != null) {
            drawPlayerMarker(g, hovered, projected, true, true);
        }
    }

    private boolean claimLabelCell(ScreenPoint point, Set<Long> occupied) {
        int cellX = Math.floorDiv(point.x, PLAYER_LABEL_CELL_WIDTH);
        int cellY = Math.floorDiv(point.y, PLAYER_LABEL_CELL_HEIGHT);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (occupied.contains(labelCellKey(cellX + dx, cellY + dy))) {
                    return false;
                }
            }
        }
        occupied.add(labelCellKey(cellX, cellY));
        return true;
    }

    private void occupyLabelCell(ScreenPoint point, Set<Long> occupied) {
        occupied.add(labelCellKey(
                Math.floorDiv(point.x, PLAYER_LABEL_CELL_WIDTH),
                Math.floorDiv(point.y, PLAYER_LABEL_CELL_HEIGHT)));
    }

    private static long labelCellKey(int x, int y) {
        return ((long) x << 32) ^ (y & 0xFFFFFFFFL);
    }

    private void drawPlayerMarker(GuiGraphicsExtractor g, ProjectedPlayerMarker entry,
                                  List<ProjectedPlayerMarker> projected, boolean hovered, boolean showLabel) {
        PlayerMarker marker = entry.marker;
        ScreenPoint point = entry.point;
        int radius = marker.local ? 9 : 6;
        int outline = marker.local ? 0xEE071923 : (marker.remote ? 0xEE241A05 : 0xEE210A25);
        int color = marker.local ? 0xFF55E7FF : (marker.remote ? 0xFFF2B33D : 0xFFFF62D4);
        if (marker.isActivePlayer()) {
            double nearest = nearestActiveMarkerDistance(entry, projected);
            if (hovered || nearest >= PLAYER_VIEW_CONE_CROWD_DISTANCE) {
                drawPlayerViewCone(g, marker, point);
            } else {
                drawPlayerDirectionTick(g, marker, point, Mth.clamp(nearest * 0.28, 6.0, 11.0));
            }
        }
        drawDiamond(g, point.x, point.y, radius + 2, outline);
        drawDiamond(g, point.x, point.y, radius, color);
        g.fill(point.x - 1, point.y - 1, point.x + 2, point.y + 2, 0xFFFFFFFF);
        if (!showLabel) {
            return;
        }

        String label = marker.local
                ? Component.translatable("overlay.voxymap.player").getString() + ": " + marker.name
                : marker.name;
        Identifier skin = resolveCachedSkin(marker.uuid);
        int headSize = 10;
        int headGap = skin != null ? headSize + 3 : 0;
        int labelWidth = minecraft.font.width(label) + 8 + headGap;
        int labelX = Mth.clamp(point.x - labelWidth / 2, 3, Math.max(3, width - labelWidth - 3));
        int labelY = point.y - radius - 15;
        g.fill(labelX, labelY, labelX + labelWidth, labelY + 11, hovered ? 0xEE030914 : 0xCC030914);
        g.fill(labelX, labelY + 10, labelX + labelWidth, labelY + 11, color);
        if (skin != null) {
            PlayerHeadIcon.draw(g, skin, labelX + 3, labelY, headSize);
        }
        g.text(minecraft.font, label, labelX + 4 + headGap, labelY + 2, 0xFFFFFFFF);
    }

    private Identifier resolveCachedSkin(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        Identifier cached = skinTextureCache.get(uuid);
        if (cached != null) {
            return cached;
        }
        long now = System.nanoTime();
        Long retryAt = missingSkinRetryNanos.get(uuid);
        if (retryAt != null && now < retryAt) {
            return null;
        }
        Identifier resolved = PlayerHeadIcon.resolveSkinTexture(minecraft, uuid);
        if (resolved != null) {
            skinTextureCache.put(uuid, resolved);
            missingSkinRetryNanos.remove(uuid);
        } else {
            missingSkinRetryNanos.put(uuid, now + SKIN_RETRY_INTERVAL_NANOS);
        }
        return resolved;
    }

    private double nearestActiveMarkerDistance(ProjectedPlayerMarker source,
                                               List<ProjectedPlayerMarker> projected) {
        double nearest = Double.POSITIVE_INFINITY;
        for (ProjectedPlayerMarker other : projected) {
            if (other != source && other.marker.isActivePlayer()) {
                nearest = Math.min(nearest,
                        Math.hypot(other.point.x - source.point.x, other.point.y - source.point.y));
            }
        }
        return nearest;
    }

    private void drawPlayerViewCone(GuiGraphicsExtractor g, PlayerMarker marker, ScreenPoint origin) {
        double worldLength = Mth.clamp(blocksPerPixel * PLAYER_VIEW_CONE_SCREEN_LENGTH, 12.0, 96.0);
        ScreenPoint left = projectViewConePoint(marker, marker.yawDegrees - PLAYER_VIEW_CONE_HALF_ANGLE, worldLength);
        ScreenPoint right = projectViewConePoint(marker, marker.yawDegrees + PLAYER_VIEW_CONE_HALF_ANGLE, worldLength);
        ScreenPoint lookPoint = projectViewConePoint(marker, marker.yawDegrees, worldLength);
        if (left != null && right != null) {
            drawSolidLine(g, origin.x, origin.y, left.x, left.y, 0x66FF62D4);
            drawSolidLine(g, left.x, left.y, right.x, right.y, 0x44FF62D4);
            drawSolidLine(g, right.x, right.y, origin.x, origin.y, 0x66FF62D4);
        }
        if (lookPoint != null) {
            drawSolidLine(g, origin.x, origin.y, lookPoint.x, lookPoint.y, 0xAAFFB3EC);
        }
    }

    private void drawPlayerDirectionTick(GuiGraphicsExtractor g, PlayerMarker marker,
                                         ScreenPoint origin, double screenLength) {
        ScreenPoint farPoint = projectViewConePoint(marker, marker.yawDegrees,
                Mth.clamp(blocksPerPixel * PLAYER_VIEW_CONE_SCREEN_LENGTH, 12.0, 96.0));
        if (farPoint == null) {
            return;
        }
        double dx = farPoint.x - origin.x;
        double dy = farPoint.y - origin.y;
        double length = Math.hypot(dx, dy);
        if (length > 0.5) {
            drawSolidLine(g, origin.x, origin.y,
                    (int) Math.round(origin.x + dx / length * screenLength),
                    (int) Math.round(origin.y + dy / length * screenLength), 0xBBFFB3EC);
        }
    }

    private void drawSolidLine(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        double[] clipped = clipLineToViewport(x1, y1, x2, y2);
        if (clipped == null) {
            return;
        }
        double dx = clipped[2] - clipped[0];
        double dy = clipped[3] - clipped[1];
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int px = (int) Math.round(clipped[0] + dx * t);
            int py = (int) Math.round(clipped[1] + dy * t);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    private ScreenPoint projectViewConePoint(PlayerMarker marker, double yawDegrees, double length) {
        return projectToScreen(
                marker.x + PlayerViewConeGeometry.directionX(yawDegrees) * length,
                marker.y,
                marker.z + PlayerViewConeGeometry.directionZ(yawDegrees) * length);
    }

    private boolean isViewCenteredOn(PlayerMarker marker) {
        return Math.abs(marker.x - viewCenterX) < 0.5
                && Math.abs(marker.y - viewCenterY) < 2.0
                && Math.abs(marker.z - viewCenterZ) < 0.5;
    }

    private ScreenPoint projectToScreen(double worldX, double worldY, double worldZ) {
        double cameraX = VoxyMapCameraController.cameraX();
        double cameraY = VoxyMapCameraController.cameraY();
        double cameraZ = VoxyMapCameraController.cameraZ();
        double pitch = Math.toRadians(VoxyMapCameraController.cameraPitch());

        double relativeX = worldX - cameraX;
        double relativeY = worldY - cameraY;
        double relativeZ = worldZ - cameraZ;
        double forwardX = -Math.sin(viewYaw) * Math.cos(pitch);
        double forwardY = -Math.sin(pitch);
        double forwardZ = Math.cos(viewYaw) * Math.cos(pitch);
        double rightX = Math.cos(viewYaw);
        double rightZ = Math.sin(viewYaw);
        double upX = -Math.sin(viewYaw) * Math.sin(pitch);
        double upY = Math.cos(pitch);
        double upZ = Math.cos(viewYaw) * Math.sin(pitch);

        double depth = relativeX * forwardX + relativeY * forwardY + relativeZ * forwardZ;
        if (depth <= 0.1 || !Double.isFinite(depth)) {
            return null;
        }

        double horizontal = relativeX * rightX + relativeZ * rightZ;
        double vertical = relativeX * upX + relativeY * upY + relativeZ * upZ;
        double tanY = Math.tan(Math.toRadians(VoxyMapCameraController.fov()) * 0.5);
        double tanX = tanY * width / (double) height;
        double screenX = width * 0.5 * (1.0 - horizontal / (depth * tanX));
        double screenY = height * 0.5 * (1.0 - vertical / (depth * tanY));
        if (!Double.isFinite(screenX) || !Double.isFinite(screenY)) {
            return null;
        }
        // A point almost edge-on to the camera (depth just above the 0.1 cutoff) can still project to
        // an astronomically large but finite pixel coordinate. Math.round(double) returns a long, and
        // narrowing that to int silently wraps around instead of clamping, turning a far off-screen
        // point into a garbage coordinate that can still look like it is inside the viewport to the
        // line clipper - producing clipped segments hundreds of millions of pixels "long" and freezing
        // the per-pixel fill loop that draws them. Clamp far outside any real screen size first so the
        // narrowing conversion is always exact.
        double clampedX = Mth.clamp(screenX, -1.0E6, 1.0E6);
        double clampedY = Mth.clamp(screenY, -1.0E6, 1.0E6);
        return new ScreenPoint((int) Math.round(clampedX), (int) Math.round(clampedY));
    }

    private static void drawDiamond(GuiGraphicsExtractor g, int centerX, int centerY, int radius, int color) {
        for (int row = -radius; row <= radius; row++) {
            int halfWidth = radius - Math.abs(row);
            g.fill(centerX - halfWidth, centerY + row, centerX + halfWidth + 1, centerY + row + 1, color);
        }
    }

    private void centerOnPlayer() {
        viewCenterX = minecraft.player.getX();
        viewCenterY = minecraft.player.getY();
        viewCenterZ = minecraft.player.getZ();
        moveVelocityX = 0.0;
        moveVelocityZ = 0.0;
    }

    private void drawSettingsButton(GuiGraphicsExtractor g, int mouseX, int mouseY, int slide) {
        String label = Component.translatable("overlay.voxymap.settings").getString();
        settingsButtonW = Math.min(width - HUD_MARGIN * 2, Math.min(156, Math.max(104, minecraft.font.width(label) + 28)));
        settingsButtonH = HUD_BUTTON_HEIGHT;
        settingsButtonX = width - settingsButtonW - HUD_MARGIN;
        settingsButtonY = xaeroButtonW > 0 ? xaeroButtonY + xaeroButtonH + HUD_GAP : HUD_MARGIN - slide;
        boolean hovered = isInsideSettingsButton(mouseX, mouseY);
        int bg = settingsOpen ? 0xD014283D : (hovered ? 0xCC112338 : 0x99030914);
        int accent = settingsOpen || hovered ? 0xFF7DEBFF : 0xFF3BA4FF;

        drawGlassPanel(g, settingsButtonX, settingsButtonY, settingsButtonW, settingsButtonH, bg, accent);
        int gearX = settingsButtonX + 13;
        int gearY = settingsButtonY + 12;
        g.fill(gearX - 4, gearY - 1, gearX + 4, gearY + 1, accent);
        g.fill(gearX - 1, gearY - 4, gearX + 1, gearY + 4, accent);
        g.text(minecraft.font, label, settingsButtonX + 26, settingsButtonY + 8, 0xFFE9F2FF);
    }

    private void drawSettingsPanel(GuiGraphicsExtractor g, int footerTop) {
        boolean showGhastScannerRow = HappyGhastScannerBridge.isAvailable();
        settingsPanelW = Math.min(360, width - HUD_MARGIN * 2);
        settingsPanelH = showGhastScannerRow ? 214 : 190;
        settingsPanelX = width - settingsPanelW - HUD_MARGIN;
        settingsPanelY = Math.max(HUD_MARGIN, footerTop - settingsPanelH - HUD_GAP);
        drawGlassPanel(g, settingsPanelX, settingsPanelY, settingsPanelW, settingsPanelH, 0xDD030914, 0xFF3BA4FF);

        int x = settingsPanelX + 12;
        int y = settingsPanelY + 10;
        pauseRowY = y + 20;
        speedRowY = y + 44;
        cutawayRowY = y + 68;
        vanillaSquadRowY = y + 92;
        vanillaSquadFlagsRowY = y + 116;
        vanillaSquadMapRowY = y + 140;
        g.text(minecraft.font, Component.translatable("screen.voxymap.settings.title"), x, y, 0xFFFFFFFF);
        drawSettingValue(g, x, pauseRowY, "screen.voxymap.settings.pause", onOff(VoxyMapSettings.pauseSingleplayer()), 0);
        drawSettingValue(g, x, speedRowY, "screen.voxymap.settings.speed", String.format("%.2fx", VoxyMapSettings.cameraSpeedMultiplier()), 1);
        drawSettingValue(g, x, cutawayRowY, "screen.voxymap.settings.cutaway", onOff(VoxyMapSettings.roomCutawayEnabled()), 2);
        drawSettingValue(g, x, vanillaSquadRowY, "screen.voxymap.settings.vanillasquad", onOff(VoxyMapSettings.vanillaSquadPlayersEnabled()), 3);
        drawSettingValue(g, x, vanillaSquadFlagsRowY, "screen.voxymap.settings.vanillasquadflags", onOff(VoxyMapSettings.vanillaSquadFlagsEnabled()), 4);
        VoxyMapSettings.VanillaSquadMap map = VoxyMapSettings.vanillaSquadMap(currentVoxyWorldId());
        String mapValue = Component.translatable(map.translationKey()).getString();
        if (map == VoxyMapSettings.VanillaSquadMap.AUTO) {
            mapValue += " → " + VanillaSquadPlayerMapBridge.activeMapId();
        }
        drawSettingValue(g, x, vanillaSquadMapRowY, "screen.voxymap.settings.vanillasquadmap", mapValue, 6);
        if (showGhastScannerRow) {
            ghastScannerRowY = y + 164;
            drawSettingValue(g, x, ghastScannerRowY, "screen.voxymap.settings.ghastscanner", onOff(VoxyMapSettings.ghastScannerRouteEnabled()), 5);
        } else {
            ghastScannerRowY = -1;
        }
    }

    private String currentVoxyWorldId() {
        if (minecraft == null || minecraft.level == null) return null;
        WorldIdentifier identifier = WorldIdentifier.of(minecraft.level);
        return identifier == null ? null : identifier.getWorldId();
    }

    private void drawSettingValue(GuiGraphicsExtractor g, int x, int y, String labelKey, String value, int row) {
        int rowX = settingsPanelX + 8;
        int rowW = settingsPanelW - 16;
        g.fill(rowX, y, rowX + rowW, y + SETTINGS_ROW_HEIGHT - 2, 0x66102034);
        g.fill(rowX, y, rowX + 2, y + SETTINGS_ROW_HEIGHT - 2, 0xFF3BA4FF);
        g.text(minecraft.font, Component.translatable(labelKey), x, y + 7, 0xFFDFF8FF);
        int valueW = Math.max(62, minecraft.font.width(value) + 14);
        int valueX = settingsPanelX + settingsPanelW - valueW - 14;
        if (row == 1) {
            valueX -= (SPEED_BUTTON_SIZE + 6);
        }
        int valueColor;
        if (row == 0) {
            valueColor = VoxyMapSettings.pauseSingleplayer() ? 0xFF55FF55 : 0xFFFF5555;
        } else if (row == 2) {
            valueColor = VoxyMapSettings.roomCutawayEnabled() ? 0xFF55FF55 : 0xFFFF5555;
        } else if (row == 3) {
            valueColor = VoxyMapSettings.vanillaSquadPlayersEnabled() ? 0xFF55FF55 : 0xFFFF5555;
        } else if (row == 4) {
            valueColor = VoxyMapSettings.vanillaSquadFlagsEnabled() ? 0xFF55FF55 : 0xFFFF5555;
        } else if (row == 5) {
            valueColor = VoxyMapSettings.ghastScannerRouteEnabled() ? 0xFF55FF55 : 0xFFFF5555;
        } else {
            valueColor = 0xFFFFFFFF;
        }
        drawValueBox(g, valueX, y + 3, valueW, 16, value, valueColor);
        if (row == 1) {
            speedMinusX = valueX - SPEED_BUTTON_SIZE - 6;
            speedMinusY = y + 2;
            speedPlusX = valueX + valueW + 6;
            speedPlusY = y + 2;
            drawSmallButton(g, speedMinusX, speedMinusY, "-", 0xCC102034);
            drawSmallButton(g, speedPlusX, speedPlusY, "+", 0xCC102034);
        }
    }

    private void drawValueBox(GuiGraphicsExtractor g, int x, int y, int w, int h, String value, int valueColor) {
        g.fill(x, y, x + w, y + h, 0xAA102034);
        g.fill(x, y, x + 2, y + h, 0xFF3BA4FF);
        g.text(minecraft.font, value, x + 7, y + 4, valueColor);
    }

    private void drawSmallButton(GuiGraphicsExtractor g, int x, int y, String label, int bg) {
        g.fill(x, y, x + SPEED_BUTTON_SIZE, y + SPEED_BUTTON_SIZE, bg);
        g.fill(x, y, x + 2, y + SPEED_BUTTON_SIZE, 0xFF7DEBFF);
        g.centeredText(minecraft.font, label, x + SPEED_BUTTON_SIZE / 2, y + 5, 0xFFFFFFFF);
    }

    private int drawFooter(GuiGraphicsExtractor g, int slide) {
        String[][] hints = cachedKeyHints;
        int contentWidth = keyHintsWidth(hints);
        float availableWidth = Math.max(1, width - HUD_MARGIN * 2);
        float scale = Math.min(1.0f, availableWidth / contentWidth);
        int visualHeight = Math.max(1, Math.round(KEY_HINT_HEIGHT * scale));
        int footerX = HUD_MARGIN;
        int footerY = height - HUD_MARGIN - visualHeight + slide;

        g.pose().pushMatrix();
        g.pose().translate(footerX, footerY);
        g.pose().scale(scale, scale);
        drawKeyHints(g, hints, 0, 0);
        g.pose().popMatrix();
        return footerY;
    }

    private void drawKeyHints(GuiGraphicsExtractor g, String[][] hints, int x, int y) {
        int currentX = x;
        for (String[] hint : hints) {
            int w = hintWidth(hint);
            drawGlassPanel(g, currentX, y, w, KEY_HINT_HEIGHT, 0x99030914, 0xFF3BA4FF);
            g.text(minecraft.font, hint[0], currentX + 9, y + 7, 0xFFFFFFFF);
            g.text(minecraft.font, hint[1], currentX + 14 + minecraft.font.width(hint[0]), y + 7, 0xFFBFD8FF);
            currentX += w + HUD_GAP;
        }
    }

    private void drawXaeroButton(GuiGraphicsExtractor g, int mouseX, int mouseY, int slide, int topX, int topY, int topW, int topH) {
        if (!XaeroWorldMapBridge.isAvailable()) {
            xaeroButtonX = -1;
            xaeroButtonY = -1;
            xaeroButtonW = 0;
            xaeroButtonH = 0;
            return;
        }

        String label = Component.translatable("overlay.voxymap.xaero").getString();
        xaeroButtonW = Math.min(width - HUD_MARGIN * 2, Math.min(180, Math.max(118, minecraft.font.width(label) + 36)));
        xaeroButtonH = HUD_BUTTON_HEIGHT;
        xaeroButtonX = width - xaeroButtonW - HUD_MARGIN;
        xaeroButtonY = HUD_MARGIN - slide;
        if (xaeroButtonX < topX + topW + HUD_GAP) {
            xaeroButtonY = topY + topH + HUD_GAP;
        }
        boolean hovered = isInsideXaeroButton(mouseX, mouseY);
        int bg = hovered ? 0xCC112338 : 0x99030914;
        int accent = hovered ? 0xFF7DEBFF : 0xFF3BA4FF;

        g.fill(xaeroButtonX, xaeroButtonY, xaeroButtonX + xaeroButtonW, xaeroButtonY + xaeroButtonH, bg);
        g.fill(xaeroButtonX, xaeroButtonY, xaeroButtonX + 2, xaeroButtonY + xaeroButtonH, accent);
        int iconX = xaeroButtonX + 12;
        int iconY = xaeroButtonY + 12;
        g.fill(iconX - 5, iconY - 5, iconX + 5, iconY + 5, 0xFF102A38);
        g.fill(iconX - 4, iconY - 1, iconX + 4, iconY + 1, accent);
        g.fill(iconX - 1, iconY - 4, iconX + 1, iconY + 4, accent);
        g.text(minecraft.font, label, xaeroButtonX + 28, xaeroButtonY + 8, 0xFFE9F2FF);
    }

    private void drawXaeroTransition(GuiGraphicsExtractor g) {
        double raw = (System.nanoTime() - xaeroTransitionStartedAt) / (double) XAERO_TRANSITION_NANOS;
        raw = Mth.clamp(raw, 0.0, 1.0);
        double eased = 1.0 - Math.pow(1.0 - raw, 3.0);
        int maxRadius = Math.max(width, height) + 120;
        int radius = (int) (maxRadius * eased);
        int originX = xaeroButtonX > 0 ? xaeroButtonX + xaeroButtonW / 2 : width - 80;
        int originY = xaeroButtonY > 0 ? xaeroButtonY + xaeroButtonH / 2 : 24;

        g.fill(0, 0, width, height, (int) (0x55000000 | ((long) (80 * eased) << 24)));
        g.fill(originX - radius, originY - radius / 3, originX + radius, originY + radius / 3, 0xDD07111F);
        g.fill(originX - radius / 3, originY - radius, originX + radius / 3, originY + radius, 0xAA0B3148);
        g.centeredText(minecraft.font, Component.translatable("overlay.voxymap.xaero.transition"),
                width / 2, height / 2 + 22, 0xFFE9F2FF);
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0) {
            return true;
        }
        double factor = Math.pow(0.82, scrollY);
        zoomAt(mouseX, mouseY, factor);
        return true;
    }

    private void zoomAt(double mouseX, double mouseY, double factor) {
        double oldBlocksPerPixel = blocksPerPixel;
        double newBlocksPerPixel = Mth.clamp(oldBlocksPerPixel * factor, 0.125, 256.0);
        if (newBlocksPerPixel == oldBlocksPerPixel) {
            return;
        }

        syncWorldCamera();
        GroundPoint anchor = mapPointAt(mouseX, mouseY);
        blocksPerPixel = newBlocksPerPixel;
        syncWorldCamera();
        keepMapPointUnderMouse(anchor, mouseX, mouseY);
        syncWorldCamera();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isInside) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_1 && handleSettingsClick(event.x(), event.y())) {
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_1 && isInsideXaeroButton(event.x(), event.y())) {
            startXaeroTransition();
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_1) {
            dragging = true;
            middleDragging = false;
            moveVelocityX = 0.0;
            moveVelocityZ = 0.0;
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            middleDragging = true;
            dragging = false;
            return true;
        }
        return super.mouseClicked(event, isInside);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_1) {
            dragging = false;
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            middleDragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (dragging) {
            panByPixels(deltaX, deltaY);
            syncWorldCamera();
            return true;
        }
        if (middleDragging) {
            viewYaw += deltaX * 0.01;
            syncWorldCamera();
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_M) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_W || keyCode == GLFW.GLFW_KEY_S ||
                keyCode == GLFW.GLFW_KEY_A || keyCode == GLFW.GLFW_KEY_D ||
                keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN ||
                keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_C && minecraft.player != null) {
            centerOnPlayer();
            syncWorldCamera();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
            zoomAt(lastZoomMouseX(), lastZoomMouseY(), 0.8);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
            zoomAt(lastZoomMouseX(), lastZoomMouseY(), 1.25);
            return true;
        }
        return super.keyPressed(event);
    }

    private double lastZoomMouseX() {
        return Double.isNaN(lastMouseX) ? width / 2.0 : lastMouseX;
    }

    private double lastZoomMouseY() {
        return Double.isNaN(lastMouseY) ? height / 2.0 : lastMouseY;
    }

    @Override
    public void onClose() {
        closeMapView();
        super.onClose();
    }

    @Override
    public void removed() {
        closeMapView();
        if (openLogged) {
            VoxyMapClient.LOGGER.info("[VoxyMap] Map closed.");
            openLogged = false;
        }
        super.removed();
    }

    private void closeMapView() {
        xaeroTransitionActive = false;
        VoxyMapGuiRenderer.close(minecraft);
        VoxyMapCameraController.deactivate();
        VoxyBridge.restoreEnvironmentalFogAfterMap();
        MapRenderSettingsGuard.restoreAfterMap(minecraft);
    }

    @Override
    public boolean isPauseScreen() {
        return VoxyMapSettings.pauseSingleplayer()
                && minecraft != null
                && minecraft.hasSingleplayerServer()
                && minecraft.isLocalServer()
                && minecraft.getSingleplayerServer() != null
                && !minecraft.getSingleplayerServer().isPublished();
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    private record GroundPoint(double x, double z) {
    }

    private record ScreenPoint(int x, int y) {
    }

    private record ProjectedPlayerMarker(PlayerMarker marker, ScreenPoint point) {
    }

    private record PlayerMarker(String name, double x, double y, double z, boolean local, boolean remote,
                                UUID uuid, float yawDegrees) {
        private boolean isActivePlayer() {
            return !local && !remote && Float.isFinite(yawDegrees);
        }
    }
}
