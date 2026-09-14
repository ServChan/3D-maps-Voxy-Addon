package com.voxymap.client.gui;

import com.voxymap.client.VoxyMapClient;
import com.voxymap.client.integration.HappyGhastScannerBridge;
import com.voxymap.client.integration.VanillaSquadFlagMapBridge;
import com.voxymap.client.integration.VanillaSquadPlayerMapBridge;
import com.voxymap.client.integration.XaeroWorldMapBridge;
import com.voxymap.client.map.MapRenderSettingsGuard;
import com.voxymap.client.map.MapShareBounds;
import com.voxymap.client.map.PlayerViewConeGeometry;
import com.voxymap.client.map.RoomCutawayController;
import com.voxymap.client.map.SpiralRouteGeometry;
import com.voxymap.client.map.VoxyBridge;
import com.voxymap.client.map.VoxyMapCameraController;
import com.voxymap.client.map.VoxyMapGuiRenderer;
import com.voxymap.client.map.VoxyMapSettings;
import com.voxymap.client.map.MapShareClient;
import com.voxymap.client.map.WaypointManager;
import com.voxymap.client.map.WaypointSyncSettings;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
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
    private static final int PLAYER_CLUSTER_Y_DISTANCE = 24;
    private static final int PLAYER_CLUSTER_MIN_X_DISTANCE = 42;
    private static final int PLAYER_CLUSTER_LABEL_GAP = 8;
    private static final int PLAYER_CLUSTER_MAX_EXPANDED_ROWS = 18;
    private static final long SKIN_RETRY_INTERVAL_NANOS = 5_000_000_000L;
    private static final double WAYPOINT_HOVER_DISTANCE = 10.0;
    private static final int WAYPOINT_NAME_MAX_LENGTH = 48;
    private static final int WAYPOINT_SWATCH_SIZE = 16;
    private static final int WAYPOINT_SWATCH_GAP = 6;
    private static final double RIGHT_CLICK_DRAG_THRESHOLD = 5.0;
    private static final long DOUBLE_CLICK_NANOS = 350_000_000L;
    private static final double DOUBLE_CLICK_DISTANCE = 6.0;
    private double viewCenterX;
    private double viewCenterY;
    private double viewCenterZ;
    private double blocksPerPixel = 4.0;
    private double viewYaw = Math.toRadians(45.0);
    private double viewPitch = 0.68;

    // The camera-basis vectors below depend only on viewYaw/camera pitch/fov, which are constant for
    // an entire frame, but projectToScreen/mapPointAt are called once per marker/waypoint/hull vertex -
    // sometimes hundreds of times per frame. syncWorldCamera() refreshes these once per camera update
    // instead of every one of those calls redoing the same sin/cos/tan work.
    private double camForwardX, camForwardY, camForwardZ;
    private double camRightX, camRightZ;
    private double camUpX, camUpY, camUpZ;
    private double camTanX, camTanY;

    private boolean dragging = false;
    private boolean rightDragging = false;
    private boolean rightDragMoved = false;
    private double rightPressX = Double.NaN;
    private double rightPressY = Double.NaN;
    private long lastLeftPressNanos = 0L;
    private double lastLeftPressX = Double.NaN;
    private double lastLeftPressY = Double.NaN;
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;
    private double moveVelocityX = 0.0;
    private double moveVelocityZ = 0.0;
    private List<PlayerMarker> playerMarkers = List.of();
    private String hoveredPlayerClusterKey = null;
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
    private int waypointsRowY = -1;
    private int syncAddressRowY = -1;
    private int syncPasswordRowY = -1;
    private int syncStatusRowY = -1;
    private int mapShareRowY = -1;
    private int mapReceiveRowY = -1;
    private int minimapRowY = -1;
    private int timeOfDayRowY = -1;
    private int mobsRowY = -1;
    private int minimapRotateRowY = -1;
    private int chunkGridRowY = -1;
    private int deathPointsRowY = -1;
    private List<ProjectedWaypoint> lastProjectedWaypoints = List.of();
    private TextInputKind textInputKind = null;
    private String renamingWaypointId = null;
    private boolean waypointDraftActive = false;
    private boolean mapShareSelectionActive = false;
    private GroundPoint mapShareFirstCorner = null;
    private MapShareBounds mapShareBounds = null;
    private String pendingMapShareAddress = null;
    private int draftColor;
    private boolean draftShared;
    private double pendingWaypointX;
    private double pendingWaypointY;
    private double pendingWaypointZ;
    private final StringBuilder nameInputBuffer = new StringBuilder();
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
        drawTextInputOverlay(g);
        drawWaypointDraftOverlay(g);
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

        if (textInputKind != null || waypointDraftActive) {
            // Typing into a text field doesn't go through keyPressed's WASD branch below - those keys
            // are read directly from raw GLFW state here, so without this guard every "w"/"a"/"s"/"d"
            // typed would also pan the camera.
            moveVelocityX = 0.0;
            moveVelocityZ = 0.0;
            return;
        }

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
        double planeY = viewCenterY;

        double ndcX = 1.0 - mouseX / width * 2.0;
        double ndcY = 1.0 - mouseY / height * 2.0;
        double rayX = camForwardX + camRightX * ndcX * camTanX + camUpX * ndcY * camTanY;
        double rayY = camForwardY + camUpY * ndcY * camTanY;
        double rayZ = camForwardZ + camRightZ * ndcX * camTanX + camUpZ * ndcY * camTanY;

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
            updateCameraBasis();
        } else {
            VoxyMapCameraController.deactivate();
        }
    }

    private void updateCameraBasis() {
        double pitch = Math.toRadians(VoxyMapCameraController.cameraPitch());
        camForwardX = -Math.sin(viewYaw) * Math.cos(pitch);
        camForwardY = -Math.sin(pitch);
        camForwardZ = Math.cos(viewYaw) * Math.cos(pitch);
        camRightX = Math.cos(viewYaw);
        camRightZ = Math.sin(viewYaw);
        camUpX = -Math.sin(viewYaw) * Math.sin(pitch);
        camUpY = Math.cos(pitch);
        camUpZ = Math.cos(viewYaw) * Math.sin(pitch);
        camTanY = Math.tan(Math.toRadians(VoxyMapCameraController.fov()) * 0.5);
        camTanX = camTanY * width / (double) height;
    }

    private String[][] buildKeyHints() {
        return new String[][] {
                {Component.translatable("overlay.voxymap.key.drag.button").getString(), Component.translatable("overlay.voxymap.key.drag").getString()},
                {Component.translatable("overlay.voxymap.key.waypoint.button").getString(), Component.translatable("overlay.voxymap.key.waypoint").getString()},
                {Component.translatable("overlay.voxymap.key.rotate.button").getString(), Component.translatable("overlay.voxymap.key.rotate").getString()},
                {Component.translatable("overlay.voxymap.key.delete.button").getString(), Component.translatable("overlay.voxymap.key.delete").getString()},
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

    /** "~N MB" for a confirmation prompt - an upper-bound estimate (see
     * {@code VoxySectionBridge.estimateExportBytes}), never an exact count. */
    private static String formatEstimatedSize(long bytes) {
        double megabytes = bytes / (1024.0 * 1024.0);
        return String.format(java.util.Locale.ROOT, megabytes < 10 ? "%.1f" : "%.0f", megabytes);
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
        } else if (isInsideSettingsRow(mouseX, mouseY, waypointsRowY)) {
            VoxyMapSettings.toggleWaypointsVisible();
        } else if (isInsideSettingsRow(mouseX, mouseY, syncAddressRowY)) {
            String worldId = currentVoxyWorldId();
            if (worldId != null) beginEditSyncAddress(worldId);
        } else if (isInsideSettingsRow(mouseX, mouseY, syncPasswordRowY)) {
            String worldId = currentVoxyWorldId();
            if (worldId != null) beginEditSyncPassword(worldId);
        } else if (isInsideSettingsRow(mouseX, mouseY, mapShareRowY)) {
            String worldId = currentVoxyWorldId();
            String address = worldId != null ? WaypointSyncSettings.address(worldId) : null;
            if (address != null) {
                if (MapShareClient.isSharing()) {
                    MapShareClient.stopSharing();
                    mapShareBounds = null;
                } else {
                    beginMapShareSelection(address);
                }
            }
        } else if (isInsideSettingsRow(mouseX, mouseY, mapReceiveRowY)) {
            if (MapShareClient.receiveStatus() == MapShareClient.ReceiveStatus.AWAITING_CONFIRMATION) {
                MapShareClient.confirmReceive(minecraft);
            } else if (MapShareClient.receiveStatus() == MapShareClient.ReceiveStatus.RECEIVING
                    || MapShareClient.receiveStatus() == MapShareClient.ReceiveStatus.CONNECTING) {
                MapShareClient.stopReceiving();
            } else {
                String worldId = currentVoxyWorldId();
                String address = worldId != null ? WaypointSyncSettings.address(worldId) : null;
                if (address != null) {
                    textInputKind = TextInputKind.RECEIVE_CODE;
                    nameInputBuffer.setLength(0);
                }
            }
        } else if (isInsideSettingsRow(mouseX, mouseY, minimapRowY)) {
            VoxyMapSettings.toggleMinimap();
        } else if (isInsideSettingsRow(mouseX, mouseY, timeOfDayRowY)) {
            VoxyMapSettings.cycleTimeOfDayMode();
        } else if (isInsideSettingsRow(mouseX, mouseY, mobsRowY)) {
            VoxyMapSettings.toggleMobs();
        } else if (isInsideSettingsRow(mouseX, mouseY, minimapRotateRowY)) {
            VoxyMapSettings.toggleMinimapRotates();
        } else if (isInsideSettingsRow(mouseX, mouseY, chunkGridRowY)) {
            VoxyMapSettings.toggleChunkGrid();
        } else if (isInsideSettingsRow(mouseX, mouseY, deathPointsRowY)) {
            VoxyMapSettings.toggleDeathPoints();
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
        rightDragging = false;
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
        drawWaypoints(g, mouseX, mouseY);
        drawPlayerMarkers(g, mouseX, mouseY);
        drawMapShareSelection(g);

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

    /**
     * Draws every waypoint saved for the current world as a pin anchored at its stored ground
     * position, with an always-visible name/distance label (matching the territory-label pattern in
     * {@link #drawFlagMarkers}) so a sparse set of user-placed pins stays readable without hovering.
     * Also refreshes {@link #lastProjectedWaypoints}, the click hit-test list used by
     * {@link #hitTestWaypoint}, and renders the in-progress pin while a new one is being named.
     */
    private void drawWaypoints(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        lastProjectedWaypoints = List.of();
        if (!VoxyMapCameraController.isActive() || !VoxyMapSettings.waypointsEnabled()) {
            return;
        }
        String worldId = currentVoxyWorldId();
        if (worldId == null) {
            return;
        }

        List<WaypointManager.Waypoint> waypoints = WaypointManager.forWorld(worldId);
        List<ProjectedWaypoint> projected = new ArrayList<>();
        for (WaypointManager.Waypoint waypoint : waypoints) {
            ScreenPoint point = projectToScreen(waypoint.x(), waypoint.y(), waypoint.z());
            if (point == null || point.x < 8 || point.x >= width - 8 || point.y < 20 || point.y >= height - 8) {
                continue;
            }
            String label = waypoint.name();
            if (minecraft.player != null) {
                long distance = Math.round(Math.hypot(waypoint.x() - minecraft.player.getX(), waypoint.z() - minecraft.player.getZ()));
                label = label + "  " + distance + "m";
            }
            int labelWidth = minecraft.font.width(label) + 8;
            int labelX = Mth.clamp(point.x - labelWidth / 2, 3, Math.max(3, width - labelWidth - 3));
            int labelY = point.y - 26;
            projected.add(new ProjectedWaypoint(waypoint, point, label, labelX, labelY, labelWidth, 11));
        }

        for (ProjectedWaypoint entry : projected) {
            boolean hovered = Math.hypot(entry.point.x - mouseX, entry.point.y - mouseY) <= WAYPOINT_HOVER_DISTANCE;
            drawWaypointPin(g, entry.point, entry.waypoint.color(), hovered);
            g.fill(entry.labelX, entry.labelY, entry.labelX + entry.labelW, entry.labelY + entry.labelH,
                    hovered ? 0xEE030914 : 0xCC030914);
            g.fill(entry.labelX, entry.labelY + entry.labelH - 1, entry.labelX + entry.labelW, entry.labelY + entry.labelH,
                    entry.waypoint.color());
            g.text(minecraft.font, entry.label, entry.labelX + 4, entry.labelY + 2, 0xFFE9F2FF);
        }
        lastProjectedWaypoints = List.copyOf(projected);

        if (waypointDraftActive) {
            ScreenPoint pendingPoint = projectToScreen(pendingWaypointX, pendingWaypointY, pendingWaypointZ);
            if (pendingPoint != null) {
                drawWaypointPin(g, pendingPoint, draftColor, true);
            }
        }
    }

    private void drawWaypointPin(GuiGraphicsExtractor g, ScreenPoint point, int color, boolean hovered) {
        int bodyCenterY = point.y - 7;
        int radius = hovered ? 7 : 5;
        drawSolidLine(g, point.x, bodyCenterY + radius - 1, point.x, point.y, 0xEE0A0A12);
        drawDiamond(g, point.x, bodyCenterY, radius + 2, 0xEE0A0A12);
        drawDiamond(g, point.x, bodyCenterY, radius, color);
        g.fill(point.x - 1, bodyCenterY - 1, point.x + 2, bodyCenterY + 2, 0xFFFFFFFF);
    }

    private WaypointManager.Waypoint hitTestWaypoint(double mouseX, double mouseY) {
        for (ProjectedWaypoint entry : lastProjectedWaypoints) {
            if (Math.hypot(entry.point.x - mouseX, entry.point.y - mouseY) <= WAYPOINT_HOVER_DISTANCE) {
                return entry.waypoint;
            }
            if (mouseX >= entry.labelX && mouseX <= entry.labelX + entry.labelW
                    && mouseY >= entry.labelY && mouseY <= entry.labelY + entry.labelH) {
                return entry.waypoint;
            }
        }
        return null;
    }

    private void beginAddWaypoint(double worldX, double worldY, double worldZ) {
        String worldId = currentVoxyWorldId();
        waypointDraftActive = true;
        pendingWaypointX = worldX;
        pendingWaypointY = worldY;
        pendingWaypointZ = worldZ;
        draftColor = worldId != null ? WaypointManager.suggestedColor(worldId) : WaypointManager.palette()[0];
        draftShared = worldId != null && WaypointManager.canShare(worldId);
        int nextIndex = (worldId != null ? WaypointManager.forWorld(worldId).size() : 0) + 1;
        nameInputBuffer.setLength(0);
        nameInputBuffer.append(Component.translatable("screen.voxymap.waypoint.default_name", nextIndex).getString());
    }

    private void cancelWaypointDraft() {
        waypointDraftActive = false;
        nameInputBuffer.setLength(0);
    }

    private void confirmWaypointDraft() {
        String worldId = currentVoxyWorldId();
        String name = nameInputBuffer.toString().trim();
        if (worldId != null && !name.isEmpty()) {
            WaypointManager.add(worldId, name, pendingWaypointX, pendingWaypointY, pendingWaypointZ, draftColor, draftShared);
        }
        cancelWaypointDraft();
    }

    /** Hit-tests the color swatches and the share/local toggle inside the add-waypoint panel - both
     * need to consume their click without closing the panel, unlike the simple text prompts below where
     * any click commits. Geometry here must match {@link #drawWaypointDraftOverlay} exactly since
     * bounds aren't cached between frames (the panel's layout is a fixed function of screen width). */
    private boolean handleWaypointDraftClick(double mouseX, double mouseY) {
        String worldId = currentVoxyWorldId();
        boolean showShareToggle = WaypointManager.canShare(worldId);
        int boxW = Math.min(320, width - HUD_MARGIN * 2);
        int boxX = width / 2 - boxW / 2;
        int boxY = HUD_MARGIN + 4;
        int swatchY = boxY + 20 + 18 + 6;
        int swatchX = boxX + 10;

        int[] palette = WaypointManager.palette();
        for (int i = 0; i < palette.length; i++) {
            int sx = swatchX + i * (WAYPOINT_SWATCH_SIZE + WAYPOINT_SWATCH_GAP);
            if (mouseX >= sx && mouseX <= sx + WAYPOINT_SWATCH_SIZE && mouseY >= swatchY && mouseY <= swatchY + WAYPOINT_SWATCH_SIZE) {
                draftColor = palette[i];
                return true;
            }
        }

        if (showShareToggle) {
            int toggleY = swatchY + WAYPOINT_SWATCH_SIZE + 6;
            int toggleX = boxX + 10;
            int toggleW = boxW - 20;
            if (mouseX >= toggleX && mouseX <= toggleX + toggleW && mouseY >= toggleY && mouseY <= toggleY + 18) {
                draftShared = !draftShared;
                return true;
            }
        }

        // Any other click (the name field, the title, or outside the panel entirely) commits, same as
        // the simple text prompts - there's no separate confirm button, Enter/Escape stay the precise way.
        confirmWaypointDraft();
        return true;
    }

    private void drawWaypointDraftOverlay(GuiGraphicsExtractor g) {
        if (!waypointDraftActive) {
            return;
        }
        String worldId = currentVoxyWorldId();
        boolean showShareToggle = WaypointManager.canShare(worldId);
        int boxW = Math.min(320, width - HUD_MARGIN * 2);
        int boxH = showShareToggle ? 98 : 76;
        int boxX = width / 2 - boxW / 2;
        int boxY = HUD_MARGIN + 4;
        drawGlassPanel(g, boxX, boxY, boxW, boxH, 0xEE030914, 0xFF7DEBFF);
        g.text(minecraft.font, Component.translatable("screen.voxymap.waypoint.add_title"), boxX + 10, boxY + 6, 0xFFE9F2FF);

        String text = nameInputBuffer.toString();
        int fieldX = boxX + 10;
        int fieldY = boxY + 20;
        int fieldW = boxW - 20;
        int fieldH = 18;
        g.fill(fieldX, fieldY, fieldX + fieldW, fieldY + fieldH, 0xAA0B1622);
        g.fill(fieldX, fieldY, fieldX + 2, fieldY + fieldH, 0xFF7DEBFF);
        g.text(minecraft.font, text, fieldX + 5, fieldY + 5, 0xFFFFFFFF);
        if ((System.nanoTime() / 500_000_000L) % 2 == 0) {
            int caretX = fieldX + 5 + minecraft.font.width(text);
            g.fill(caretX, fieldY + 3, caretX + 1, fieldY + fieldH - 3, 0xFFFFFFFF);
        }

        int swatchY = fieldY + fieldH + 6;
        int swatchX = fieldX;
        int[] palette = WaypointManager.palette();
        for (int color : palette) {
            boolean selected = color == draftColor;
            int outline = selected ? 0xFFFFFFFF : 0xFF0A0A12;
            g.fill(swatchX - 1, swatchY - 1, swatchX + WAYPOINT_SWATCH_SIZE + 1, swatchY + WAYPOINT_SWATCH_SIZE + 1, outline);
            g.fill(swatchX, swatchY, swatchX + WAYPOINT_SWATCH_SIZE, swatchY + WAYPOINT_SWATCH_SIZE, color);
            swatchX += WAYPOINT_SWATCH_SIZE + WAYPOINT_SWATCH_GAP;
        }

        if (showShareToggle) {
            int toggleY = swatchY + WAYPOINT_SWATCH_SIZE + 6;
            String toggleLabel = Component.translatable(draftShared
                    ? "screen.voxymap.waypoint.share.shared" : "screen.voxymap.waypoint.share.local").getString();
            g.fill(fieldX, toggleY, fieldX + fieldW, toggleY + 18, 0x66102034);
            g.fill(fieldX, toggleY, fieldX + 2, toggleY + 18, draftShared ? 0xFF55FF55 : 0xFF3BA4FF);
            g.text(minecraft.font, toggleLabel, fieldX + 6, toggleY + 5, draftShared ? 0xFF9CFFA0 : 0xFFDFF8FF);
        }

        String hint = Component.translatable("screen.voxymap.waypoint.hint").getString();
        g.text(minecraft.font, hint, boxX + 10, boxY + boxH - 12, 0xFF9FD8FF);
    }

    private void beginRenameWaypoint(WaypointManager.Waypoint waypoint) {
        textInputKind = TextInputKind.WAYPOINT_RENAME;
        renamingWaypointId = waypoint.id();
        nameInputBuffer.setLength(0);
        nameInputBuffer.append(waypoint.name());
    }

    private void beginEditSyncAddress(String worldId) {
        textInputKind = TextInputKind.SYNC_ADDRESS;
        nameInputBuffer.setLength(0);
        String current = WaypointSyncSettings.address(worldId);
        if (current != null) {
            nameInputBuffer.append(current);
        }
    }

    private void beginEditSyncPassword(String worldId) {
        textInputKind = TextInputKind.SYNC_PASSWORD;
        nameInputBuffer.setLength(0);
        String current = WaypointSyncSettings.password(worldId);
        if (current != null) {
            nameInputBuffer.append(current);
        }
    }

    private void cancelTextInput() {
        textInputKind = null;
        renamingWaypointId = null;
        nameInputBuffer.setLength(0);
    }

    private void confirmTextInput() {
        String worldId = currentVoxyWorldId();
        String value = nameInputBuffer.toString().trim();
        if (worldId != null) {
            switch (textInputKind) {
                case WAYPOINT_RENAME -> {
                    if (!value.isEmpty()) {
                        WaypointManager.rename(worldId, renamingWaypointId, value);
                    }
                }
                case SYNC_ADDRESS -> WaypointSyncSettings.setAddress(worldId, value);
                case SYNC_PASSWORD -> WaypointSyncSettings.setPassword(worldId, value);
                case RECEIVE_CODE -> {
                    String address = WaypointSyncSettings.address(worldId);
                    if (address != null && value.matches("[0-9]{6}")) {
                        MapShareClient.startReceiving(minecraft, address, value);
                    }
                }
            }
        }
        cancelTextInput();
    }

    private int textInputMaxLength() {
        return switch (textInputKind) {
            case WAYPOINT_RENAME -> WAYPOINT_NAME_MAX_LENGTH;
            case SYNC_ADDRESS -> 128;
            case SYNC_PASSWORD -> 64;
            case RECEIVE_CODE -> 6;
        };
    }

    /** Refuses (with a chat message) editing a waypoint this client can't write to - a pin shared from
     * a read-only sync (address without a password) would otherwise have its rename/delete silently
     * no-op deep inside {@link WaypointManager}. Local-only pins are always editable regardless of the
     * world's sync configuration. */
    private boolean blockedFromEditing(String worldId, WaypointManager.Waypoint waypoint) {
        if (worldId == null || !waypoint.shared()) return false;
        if (WaypointSyncSettings.password(worldId) != null) return false;
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.translatable("message.voxymap.waypoint.read_only"));
        }
        return true;
    }

    private void drawTextInputOverlay(GuiGraphicsExtractor g) {
        if (textInputKind == null) {
            return;
        }
        String titleKey = switch (textInputKind) {
            case WAYPOINT_RENAME -> "screen.voxymap.waypoint.rename_title";
            case SYNC_ADDRESS -> "screen.voxymap.sync.address_title";
            case SYNC_PASSWORD -> "screen.voxymap.sync.password_title";
            case RECEIVE_CODE -> "screen.voxymap.mapshare.receive_code_title";
        };
        String title = Component.translatable(titleKey).getString();
        String text = textInputKind == TextInputKind.SYNC_PASSWORD
                ? "*".repeat(nameInputBuffer.length()) : nameInputBuffer.toString();

        int boxW = Math.min(320, width - HUD_MARGIN * 2);
        int boxH = 54;
        int boxX = width / 2 - boxW / 2;
        int boxY = HUD_MARGIN + 4;
        drawGlassPanel(g, boxX, boxY, boxW, boxH, 0xEE030914, 0xFF7DEBFF);
        g.text(minecraft.font, title, boxX + 10, boxY + 6, 0xFFE9F2FF);

        int fieldX = boxX + 10;
        int fieldY = boxY + 20;
        int fieldW = boxW - 20;
        int fieldH = 18;
        g.fill(fieldX, fieldY, fieldX + fieldW, fieldY + fieldH, 0xAA0B1622);
        g.fill(fieldX, fieldY, fieldX + 2, fieldY + fieldH, 0xFF7DEBFF);
        g.text(minecraft.font, text, fieldX + 5, fieldY + 5, 0xFFFFFFFF);
        if ((System.nanoTime() / 500_000_000L) % 2 == 0) {
            int caretX = fieldX + 5 + minecraft.font.width(text);
            g.fill(caretX, fieldY + 3, caretX + 1, fieldY + fieldH - 3, 0xFFFFFFFF);
        }

        String hint = Component.translatable("screen.voxymap.waypoint.hint").getString();
        g.text(minecraft.font, hint, boxX + 10, boxY + boxH - 12, 0xFF9FD8FF);
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
        projected.sort(Comparator
                .comparing((ProjectedPlayerMarker entry) -> !entry.marker.local)
                .thenComparing(entry -> entry.marker.remote)
                .thenComparing(entry -> entry.marker.name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.marker.uuid == null ? "" : entry.marker.uuid.toString()));

        List<PlayerMarkerCluster> clusters = clusterPlayerMarkers(projected);
        PlayerMarkerCluster hovered = null;
        double hoveredDistance = Double.POSITIVE_INFINITY;
        for (PlayerMarkerCluster cluster : clusters) {
            double distance = clusterHoverDistance(cluster, mouseX, mouseY);
            if (clusterKey(cluster).equals(hoveredPlayerClusterKey)
                    && expandedPlayerClusterBounds(cluster).contains(mouseX, mouseY)) {
                distance = 0.0;
            }
            if (distance <= PLAYER_MARKER_HOVER_DISTANCE && distance < hoveredDistance) {
                hovered = cluster;
                hoveredDistance = distance;
            }
        }
        hoveredPlayerClusterKey = hovered == null ? null : clusterKey(hovered);

        for (PlayerMarkerCluster cluster : clusters) {
            if (cluster != hovered) {
                drawPlayerMarkerCluster(g, cluster, projected, false);
            }
        }
        if (hovered != null) {
            drawPlayerMarkerCluster(g, hovered, projected, true);
        }
    }

    /** Builds connected groups from labels that would overlap at their normal above-marker position.
     * Unlike the former screen-cell allocator, membership and ordering do not depend on hash-map or
     * cell-claim order, so a one-pixel camera movement cannot reshuffle a whole crowd's labels. */
    private List<PlayerMarkerCluster> clusterPlayerMarkers(List<ProjectedPlayerMarker> projected) {
        List<PlayerMarkerCluster> result = new ArrayList<>();
        boolean[] visited = new boolean[projected.size()];
        for (int start = 0; start < projected.size(); start++) {
            if (visited[start]) {
                continue;
            }
            List<ProjectedPlayerMarker> members = new ArrayList<>();
            List<Integer> pending = new ArrayList<>();
            pending.add(start);
            visited[start] = true;
            for (int cursor = 0; cursor < pending.size(); cursor++) {
                int index = pending.get(cursor);
                ProjectedPlayerMarker candidate = projected.get(index);
                members.add(candidate);
                for (int other = 0; other < projected.size(); other++) {
                    if (!visited[other] && playerLabelsOverlap(candidate, projected.get(other))) {
                        visited[other] = true;
                        pending.add(other);
                    }
                }
            }

            int sumX = 0;
            int sumY = 0;
            for (ProjectedPlayerMarker member : members) {
                sumX += member.point.x;
                sumY += member.point.y;
            }
            result.add(new PlayerMarkerCluster(List.copyOf(members),
                    new ScreenPoint(Math.round((float) sumX / members.size()),
                            Math.round((float) sumY / members.size()))));
        }
        return result;
    }

    private boolean playerLabelsOverlap(ProjectedPlayerMarker first, ProjectedPlayerMarker second) {
        int firstWidth = playerLabelWidth(first.marker);
        int secondWidth = playerLabelWidth(second.marker);
        int xDistance = Math.max(PLAYER_CLUSTER_MIN_X_DISTANCE,
                (firstWidth + secondWidth) / 2 + PLAYER_CLUSTER_LABEL_GAP);
        return Math.abs(first.point.x - second.point.x) <= xDistance
                && Math.abs(first.point.y - second.point.y) <= PLAYER_CLUSTER_Y_DISTANCE;
    }

    private int playerLabelWidth(PlayerMarker marker) {
        String label = playerLabel(marker);
        return minecraft.font.width(label) + 8 + (resolveCachedSkin(marker.uuid) == null ? 0 : 13);
    }

    private double clusterHoverDistance(PlayerMarkerCluster cluster, int mouseX, int mouseY) {
        if (cluster.members.size() > 1 && collapsedPlayerClusterBounds(cluster).contains(mouseX, mouseY)) {
            return 0.0;
        }
        double nearest = Math.hypot(cluster.anchor.x - mouseX, cluster.anchor.y - mouseY);
        for (ProjectedPlayerMarker member : cluster.members) {
            nearest = Math.min(nearest, Math.hypot(member.point.x - mouseX, member.point.y - mouseY));
        }
        return nearest;
    }

    private void drawPlayerMarkerCluster(GuiGraphicsExtractor g, PlayerMarkerCluster cluster,
                                         List<ProjectedPlayerMarker> projected, boolean hovered) {
        if (cluster.members.size() == 1) {
            drawPlayerMarker(g, cluster.members.getFirst(), projected, hovered, true);
            return;
        }

        for (ProjectedPlayerMarker member : cluster.members) {
            drawPlayerMarker(g, member, projected, false, false);
        }
        drawCollapsedPlayerClusterLabel(g, cluster, hovered);
        if (hovered) {
            drawExpandedPlayerCluster(g, cluster);
        }
    }

    private void drawCollapsedPlayerClusterLabel(GuiGraphicsExtractor g, PlayerMarkerCluster cluster,
                                                  boolean hovered) {
        PlayerMarker representative = cluster.members.getFirst().marker;
        String label = playerLabel(representative) + " +" + (cluster.members.size() - 1);
        Identifier skin = resolveCachedSkin(representative.uuid);
        int headGap = skin == null ? 0 : 13;
        ScreenRect bounds = collapsedPlayerClusterBounds(cluster);
        int color = representative.local ? 0xFF55E7FF : (representative.remote ? 0xFFF2B33D : 0xFFFF62D4);
        g.fill(bounds.x, bounds.y, bounds.right(), bounds.bottom(), hovered ? 0xEE030914 : 0xCC030914);
        g.fill(bounds.x, bounds.bottom() - 1, bounds.right(), bounds.bottom(), color);
        if (skin != null) {
            PlayerHeadIcon.draw(g, skin, bounds.x + 3, bounds.y, 10);
        }
        g.text(minecraft.font, label, bounds.x + 4 + headGap, bounds.y + 2, 0xFFFFFFFF);
    }

    private void drawExpandedPlayerCluster(GuiGraphicsExtractor g, PlayerMarkerCluster cluster) {
        int visibleRows = Math.min(cluster.members.size(), PLAYER_CLUSTER_MAX_EXPANDED_ROWS);
        boolean truncated = visibleRows < cluster.members.size();
        ScreenRect bounds = expandedPlayerClusterBounds(cluster);
        g.fill(bounds.x, bounds.y, bounds.right(), bounds.bottom(), 0xF0030914);
        g.fill(bounds.x, bounds.y, bounds.x + 2, bounds.bottom(), 0xFF55E7FF);
        for (int i = 0; i < visibleRows; i++) {
            PlayerMarker marker = cluster.members.get(i).marker;
            int rowY = bounds.y + 2 + i * 12;
            Identifier skin = resolveCachedSkin(marker.uuid);
            if (skin != null) {
                PlayerHeadIcon.draw(g, skin, bounds.x + 5, rowY, 10);
            }
            g.text(minecraft.font, playerLabel(marker), bounds.x + (skin == null ? 5 : 18), rowY + 2,
                    marker.local ? 0xFF8CF0FF : 0xFFFFFFFF);
        }
        if (truncated) {
            g.text(minecraft.font, "… +" + (cluster.members.size() - visibleRows), bounds.x + 5,
                    bounds.y + 2 + visibleRows * 12, 0xFF9FB2C4);
        }
    }

    private ScreenRect collapsedPlayerClusterBounds(PlayerMarkerCluster cluster) {
        PlayerMarker representative = cluster.members.getFirst().marker;
        String label = playerLabel(representative) + " +" + (cluster.members.size() - 1);
        int headGap = resolveCachedSkin(representative.uuid) == null ? 0 : 13;
        int labelWidth = minecraft.font.width(label) + 8 + headGap;
        int labelX = Mth.clamp(cluster.anchor.x - labelWidth / 2, 3, Math.max(3, width - labelWidth - 3));
        int labelY = cluster.anchor.y - markerRadius(representative) - 15;
        return new ScreenRect(labelX, labelY, labelWidth, 11);
    }

    private ScreenRect expandedPlayerClusterBounds(PlayerMarkerCluster cluster) {
        int visibleRows = Math.min(cluster.members.size(), PLAYER_CLUSTER_MAX_EXPANDED_ROWS);
        boolean truncated = visibleRows < cluster.members.size();
        int panelWidth = 0;
        for (int i = 0; i < visibleRows; i++) {
            panelWidth = Math.max(panelWidth,
                    minecraft.font.width(playerLabel(cluster.members.get(i).marker)) + 21);
        }
        if (truncated) {
            panelWidth = Math.max(panelWidth,
                    minecraft.font.width("… +" + (cluster.members.size() - visibleRows)) + 8);
        }
        panelWidth = Math.max(panelWidth, 56);
        int rows = visibleRows + (truncated ? 1 : 0);
        int panelHeight = rows * 12 + 4;
        int panelX = Mth.clamp(cluster.anchor.x - panelWidth / 2, 3, Math.max(3, width - panelWidth - 3));
        int panelY = Mth.clamp(cluster.anchor.y + 12, 3, Math.max(3, height - panelHeight - 3));
        return new ScreenRect(panelX, panelY, panelWidth, panelHeight);
    }

    private static String clusterKey(PlayerMarkerCluster cluster) {
        StringBuilder key = new StringBuilder();
        for (ProjectedPlayerMarker member : cluster.members) {
            key.append(member.marker.uuid == null ? member.marker.name : member.marker.uuid).append('\n');
        }
        return key.toString();
    }

    private String playerLabel(PlayerMarker marker) {
        return marker.local
                ? Component.translatable("overlay.voxymap.player").getString() + ": " + marker.name
                : marker.name;
    }

    private static int markerRadius(PlayerMarker marker) {
        return marker.local ? 9 : 6;
    }

    private void drawPlayerMarker(GuiGraphicsExtractor g, ProjectedPlayerMarker entry,
                                  List<ProjectedPlayerMarker> projected, boolean hovered, boolean showLabel) {
        PlayerMarker marker = entry.marker;
        ScreenPoint point = entry.point;
        int radius = markerRadius(marker);
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

        String label = playerLabel(marker);
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

    // drawPlayerMarker's Mth.clamp(nearest * 0.28, 6.0, 11.0) bottoms out at 6.0 for any nearest at or
    // below this - once nearestActiveMarkerDistance has found a neighbor this close, no closer one
    // could change what the caller does with the result, so scanning the rest of a crowd is wasted
    // work. Keeps this an O(n) scan with an early-out instead of an unconditional O(n) pass per marker
    // (O(n^2) total) in the one case - a dense crowd - where that total actually gets large.
    private static final double NEAREST_MARKER_EARLY_EXIT_DISTANCE = 6.0 / 0.28;

    private double nearestActiveMarkerDistance(ProjectedPlayerMarker source,
                                               List<ProjectedPlayerMarker> projected) {
        double nearest = Double.POSITIVE_INFINITY;
        for (ProjectedPlayerMarker other : projected) {
            if (other == source || !other.marker.isActivePlayer()) {
                continue;
            }
            double distance = Math.hypot(other.point.x - source.point.x, other.point.y - source.point.y);
            if (distance < nearest) {
                nearest = distance;
                if (nearest <= NEAREST_MARKER_EARLY_EXIT_DISTANCE) {
                    break;
                }
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

        double relativeX = worldX - cameraX;
        double relativeY = worldY - cameraY;
        double relativeZ = worldZ - cameraZ;

        double depth = relativeX * camForwardX + relativeY * camForwardY + relativeZ * camForwardZ;
        if (depth <= 0.1 || !Double.isFinite(depth)) {
            return null;
        }

        double horizontal = relativeX * camRightX + relativeZ * camRightZ;
        double vertical = relativeX * camUpX + relativeY * camUpY + relativeZ * camUpZ;
        double screenX = width * 0.5 * (1.0 - horizontal / (depth * camTanX));
        double screenY = height * 0.5 * (1.0 - vertical / (depth * camTanY));
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

    /** The real terrain surface at this x/z, for placing a new waypoint - {@code mapPointAt} only ever
     * resolves x/z (it intersects the mouse ray with the fixed horizontal plane at {@link #viewCenterY},
     * the height the view happened to be centered on when the map opened), so without this a waypoint
     * clicked anywhere whose actual elevation differs from that opening height would land floating in
     * the air or buried underground instead of on the ground clicked on. Falls back to
     * {@link #viewCenterY} for a column whose chunk isn't loaded, rather than forcing one in just to
     * place a pin - consistent with the rest of VoxyMap never loading terrain the client hasn't already
     * explored. */
    private double resolveGroundY(double x, double z) {
        if (minecraft.level == null) {
            return viewCenterY;
        }
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        if (!minecraft.level.hasChunk(blockX >> 4, blockZ >> 4)) {
            return viewCenterY;
        }
        int height = minecraft.level.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ);
        return height > minecraft.level.getMinY() ? height : viewCenterY;
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
        String panelWorldId = currentVoxyWorldId();
        WaypointManager.SyncStatus panelSyncStatus = panelWorldId != null
                ? WaypointManager.syncStatus(panelWorldId) : WaypointManager.SyncStatus.DISABLED;
        String syncErrorDetail = panelSyncStatus == WaypointManager.SyncStatus.ERROR
                ? WaypointManager.syncErrorDetail(panelWorldId) : null;
        settingsPanelW = Math.min(360, width - HUD_MARGIN * 2);
        settingsPanelH = (showGhastScannerRow ? 214 : 190) + 24 + 72 + 168 + 24 + (syncErrorDetail != null ? 12 : 0);
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
        waypointsRowY = y + (showGhastScannerRow ? 188 : 164);
        drawSettingValue(g, x, waypointsRowY, "screen.voxymap.settings.waypoints", onOff(VoxyMapSettings.waypointsEnabled()), 7);

        String worldId = panelWorldId;
        String notSet = Component.translatable("screen.voxymap.settings.sync.not_set").getString();

        syncAddressRowY = waypointsRowY + 24;
        String address = worldId != null ? WaypointSyncSettings.address(worldId) : null;
        drawSettingValue(g, x, syncAddressRowY, "screen.voxymap.settings.sync_address", address != null ? address : notSet, 8);

        syncPasswordRowY = syncAddressRowY + 24;
        String password = worldId != null ? WaypointSyncSettings.password(worldId) : null;
        String passwordDisplay = password != null ? "*".repeat(Math.min(password.length(), 12)) : notSet;
        drawSettingValue(g, x, syncPasswordRowY, "screen.voxymap.settings.sync_password", passwordDisplay, 9);

        syncStatusRowY = syncPasswordRowY + 24;
        drawSyncStatusRow(g, x, syncStatusRowY, panelSyncStatus);
        int afterSyncStatus = syncStatusRowY + SETTINGS_ROW_HEIGHT;
        if (syncErrorDetail != null) {
            String detailText = syncErrorDetail.length() > 60 ? syncErrorDetail.substring(0, 57) + "..." : syncErrorDetail;
            g.text(minecraft.font, detailText, x, afterSyncStatus, 0xFFFF8080);
            afterSyncStatus += 12;
        }

        boolean canShareMap = address != null;
        String notConfigured = Component.translatable("screen.voxymap.settings.sync.not_set").getString();

        mapShareRowY = afterSyncStatus;
        String shareValue;
        if (!canShareMap) {
            shareValue = notConfigured;
        } else if (MapShareClient.isSharing()) {
            shareValue = MapShareClient.shareCode() + ": " + switch (MapShareClient.shareStatus()) {
                case WAITING -> Component.translatable("screen.voxymap.mapshare.waiting", MapShareClient.shareCompletedCount()).getString();
                case AWAITING_CONFIRMATION -> Component.translatable("screen.voxymap.mapshare.awaiting_confirmation",
                        formatEstimatedSize(MapShareClient.shareEstimatedBytes())).getString();
                case SENDING -> {
                    int sent = MapShareClient.shareSectionsSentThisAttempt();
                    int total = MapShareClient.shareSectionsTotalThisAttempt();
                    // total briefly reads 0 before the export thread finishes listing sections to send -
                    // same fallback as the receiving side, avoids a flash of a bogus 0%.
                    yield total > 0
                            ? Component.translatable("screen.voxymap.mapshare.sending", sent, total, (int) Math.min(100L, 100L * sent / total)).getString()
                            : Component.translatable("screen.voxymap.mapshare.sending_unknown", sent).getString();
                }
                case ERROR -> Component.translatable("screen.voxymap.mapshare.error").getString();
                case IDLE -> "";
            };
        } else {
            shareValue = Component.translatable("screen.voxymap.mapshare.start").getString();
        }
        drawSettingValue(g, x, mapShareRowY, "screen.voxymap.settings.map_share", shareValue, 10);

        mapReceiveRowY = mapShareRowY + 24;
        String receiveValue;
        if (!canShareMap) {
            receiveValue = notConfigured;
        } else {
            receiveValue = switch (MapShareClient.receiveStatus()) {
                case IDLE -> Component.translatable("screen.voxymap.mapshare.get").getString();
                case CONNECTING -> Component.translatable("screen.voxymap.mapshare.connecting").getString();
                case AWAITING_CONFIRMATION -> Component.translatable("screen.voxymap.mapshare.confirm_receive",
                        formatEstimatedSize(MapShareClient.receiveEstimatedBytes())).getString();
                case RECEIVING -> {
                    int imported = MapShareClient.receiveImportedCount();
                    int skipped = MapShareClient.receiveSkippedCount();
                    int done = imported + skipped;
                    int total = MapShareClient.receiveTotalCount();
                    // total is 0 when the sender is on the older protocol version that didn't send a
                    // count (see MapShareClient's class doc) - fall back to a plain running count rather
                    // than showing a stuck-at-0% that never reflects real progress.
                    yield total > 0
                            ? Component.translatable("screen.voxymap.mapshare.receiving", done, total, (int) Math.min(100L, 100L * done / total)).getString()
                            : Component.translatable("screen.voxymap.mapshare.receiving_unknown", done).getString();
                }
                case DONE -> Component.translatable("screen.voxymap.mapshare.done", MapShareClient.receiveImportedCount(), MapShareClient.receiveSkippedCount()).getString();
                case ERROR -> Component.translatable("screen.voxymap.mapshare.error").getString();
            };
        }
        drawSettingValue(g, x, mapReceiveRowY, "screen.voxymap.settings.map_receive", receiveValue, 11);

        minimapRowY = mapReceiveRowY + 24;
        drawSettingValue(g, x, minimapRowY, "screen.voxymap.settings.minimap", onOff(VoxyMapSettings.minimapEnabled()), 12);

        timeOfDayRowY = minimapRowY + 24;
        String timeOfDayValue = Component.translatable(VoxyMapSettings.timeOfDayMode().translationKey()).getString();
        drawSettingValue(g, x, timeOfDayRowY, "screen.voxymap.settings.timeofday", timeOfDayValue, 13);

        mobsRowY = timeOfDayRowY + 24;
        drawSettingValue(g, x, mobsRowY, "screen.voxymap.settings.mobs", onOff(VoxyMapSettings.mobsEnabled()), 15);

        minimapRotateRowY = mobsRowY + 24;
        drawSettingValue(g, x, minimapRotateRowY, "screen.voxymap.settings.minimaprotate", onOff(VoxyMapSettings.minimapRotates()), 16);

        chunkGridRowY = minimapRotateRowY + 24;
        drawSettingValue(g, x, chunkGridRowY, "screen.voxymap.settings.chunkgrid", onOff(VoxyMapSettings.chunkGridEnabled()), 17);

        deathPointsRowY = chunkGridRowY + 24;
        drawSettingValue(g, x, deathPointsRowY, "screen.voxymap.settings.deathpoints", onOff(VoxyMapSettings.deathPointsEnabled()), 18);
    }

    private void drawSyncStatusRow(GuiGraphicsExtractor g, int x, int y, WaypointManager.SyncStatus status) {
        int rowX = settingsPanelX + 8;
        int rowW = settingsPanelW - 16;
        g.fill(rowX, y, rowX + rowW, y + SETTINGS_ROW_HEIGHT - 2, 0x66102034);
        g.fill(rowX, y, rowX + 2, y + SETTINGS_ROW_HEIGHT - 2, 0xFF3BA4FF);
        g.text(minecraft.font, Component.translatable("screen.voxymap.settings.sync_status"), x, y + 7, 0xFFDFF8FF);

        String labelKey = switch (status) {
            case DISABLED -> "screen.voxymap.settings.sync.status.disabled";
            case READ_ONLY -> "screen.voxymap.settings.sync.status.read_only";
            case SYNCING -> "screen.voxymap.settings.sync.status.syncing";
            case OK -> "screen.voxymap.settings.sync.status.ok";
            case ERROR -> "screen.voxymap.settings.sync.status.error";
        };
        int color = switch (status) {
            case OK -> 0xFF55FF55;
            case READ_ONLY -> 0xFFFFC94D;
            case SYNCING -> 0xFF9FD8FF;
            case ERROR -> 0xFFFF5555;
            case DISABLED -> 0xFF9AA7B8;
        };
        String label = Component.translatable(labelKey).getString();
        int valueW = Math.max(62, minecraft.font.width(label) + 14);
        int valueX = settingsPanelX + settingsPanelW - valueW - 14;
        drawValueBox(g, valueX, y + 3, valueW, 16, label, color);
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
        } else if (row == 7) {
            valueColor = VoxyMapSettings.waypointsEnabled() ? 0xFF55FF55 : 0xFFFF5555;
        } else if (row == 12) {
            valueColor = VoxyMapSettings.minimapEnabled() ? 0xFF55FF55 : 0xFFFF5555;
        } else if (row == 15) {
            valueColor = VoxyMapSettings.mobsEnabled() ? 0xFF55FF55 : 0xFFFF5555;
        } else if (row == 16) {
            valueColor = VoxyMapSettings.minimapRotates() ? 0xFF55FF55 : 0xFFFF5555;
        } else if (row == 17) {
            valueColor = VoxyMapSettings.chunkGridEnabled() ? 0xFF55FF55 : 0xFFFF5555;
        } else if (row == 18) {
            valueColor = VoxyMapSettings.deathPointsEnabled() ? 0xFF55FF55 : 0xFFFF5555;
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

    private void beginMapShareSelection(String address) {
        mapShareSelectionActive = true;
        mapShareFirstCorner = null;
        mapShareBounds = null;
        pendingMapShareAddress = address;
        settingsOpen = false;
        dragging = false;
        rightDragging = false;
    }

    private void cancelMapShareSelection() {
        mapShareSelectionActive = false;
        mapShareFirstCorner = null;
        pendingMapShareAddress = null;
    }

    private boolean handleMapShareSelectionClick(double mouseX, double mouseY) {
        GroundPoint point = mapPointAt(mouseX, mouseY);
        if (point == null) {
            return true;
        }
        if (mapShareFirstCorner == null) {
            mapShareFirstCorner = point;
            return true;
        }

        mapShareBounds = MapShareBounds.fromBlockCoordinates(
                mapShareFirstCorner.x, mapShareFirstCorner.z, point.x, point.z);
        String address = pendingMapShareAddress;
        mapShareSelectionActive = false;
        mapShareFirstCorner = null;
        pendingMapShareAddress = null;
        if (address != null) {
            MapShareClient.startSharing(minecraft, address, mapShareBounds);
            settingsOpen = true;
        }
        return true;
    }

    private void drawMapShareSelection(GuiGraphicsExtractor g) {
        if (!mapShareSelectionActive && mapShareBounds == null) {
            return;
        }

        MapShareBounds bounds = mapShareBounds;
        if (bounds != null) {
            ScreenPoint a = projectToScreen(bounds.minBlockX(), viewCenterY, bounds.minBlockZ());
            ScreenPoint b = projectToScreen(bounds.maxBlockXExclusive(), viewCenterY, bounds.minBlockZ());
            ScreenPoint c = projectToScreen(bounds.maxBlockXExclusive(), viewCenterY, bounds.maxBlockZExclusive());
            ScreenPoint d = projectToScreen(bounds.minBlockX(), viewCenterY, bounds.maxBlockZExclusive());
            drawSelectionEdge(g, a, b);
            drawSelectionEdge(g, b, c);
            drawSelectionEdge(g, c, d);
            drawSelectionEdge(g, d, a);
        } else if (mapShareFirstCorner != null) {
            ScreenPoint point = projectToScreen(mapShareFirstCorner.x, viewCenterY, mapShareFirstCorner.z);
            if (point != null) {
                drawDiamond(g, point.x, point.y, 5, 0xFF55E7FF);
            }
        }

        if (mapShareSelectionActive) {
            String key = mapShareFirstCorner == null
                    ? "screen.voxymap.mapshare.select_first"
                    : "screen.voxymap.mapshare.select_second";
            String instruction = Component.translatable(key).getString();
            int textW = minecraft.font.width(instruction);
            int boxX = (width - textW) / 2 - 10;
            int boxY = HUD_MARGIN + 52;
            g.fill(boxX, boxY, boxX + textW + 20, boxY + 22, 0xD907111F);
            g.fill(boxX, boxY, boxX + 2, boxY + 22, 0xFF55E7FF);
            g.centeredText(minecraft.font, instruction, width / 2, boxY + 7, 0xFFDFF8FF);
        }
    }

    private void drawSelectionEdge(GuiGraphicsExtractor g, ScreenPoint a, ScreenPoint b) {
        if (a != null && b != null) {
            drawSolidLine(g, a.x, a.y, b.x, b.y, 0xFF55E7FF);
        }
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
        if (mapShareSelectionActive) {
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                return handleMapShareSelectionClick(event.x(), event.y());
            }
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                cancelMapShareSelection();
                settingsOpen = true;
                return true;
            }
            return true;
        }
        if (waypointDraftActive) {
            return handleWaypointDraftClick(event.x(), event.y());
        }
        if (textInputKind != null) {
            // Clicking anywhere while a text box is open commits it, mirroring Enter - the click
            // itself isn't otherwise acted on, so a stray click can't start a drag mid-edit or land on
            // the waypoint pin one is currently naming.
            confirmTextInput();
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_1 && handleSettingsClick(event.x(), event.y())) {
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_1 && isInsideXaeroButton(event.x(), event.y())) {
            startXaeroTransition();
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && isInsideSettingsPanel(event.x(), event.y())) {
            if (isInsideSettingsRow(event.x(), event.y(), mapReceiveRowY)) {
                if (MapShareClient.receiveStatus() == MapShareClient.ReceiveStatus.AWAITING_CONFIRMATION) {
                    MapShareClient.declineReceive();
                } else if (MapShareClient.receiveStatus() == MapShareClient.ReceiveStatus.RECEIVING
                        || MapShareClient.receiveStatus() == MapShareClient.ReceiveStatus.CONNECTING) {
                    MapShareClient.stopReceiving();
                }
            }
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && !isInsideSettingsButton(event.x(), event.y()) && !isInsideSettingsPanel(event.x(), event.y())
                && !isInsideXaeroButton(event.x(), event.y())) {
            // The actual waypoint add/remove only fires on release, and only if the button never moved
            // past RIGHT_CLICK_DRAG_THRESHOLD - RMB now does double duty (a plain click edits a
            // waypoint, holding and dragging it rotates the camera), and there is no way to tell which
            // one this is until the button comes back up.
            rightDragging = true;
            rightDragMoved = false;
            rightPressX = event.x();
            rightPressY = event.y();
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_1) {
            WaypointManager.Waypoint hit = hitTestWaypoint(event.x(), event.y());
            if (hit != null) {
                if (!blockedFromEditing(currentVoxyWorldId(), hit)) {
                    beginRenameWaypoint(hit);
                }
                lastLeftPressNanos = 0L;
                return true;
            }

            long now = System.nanoTime();
            boolean isDoubleClick = now - lastLeftPressNanos <= DOUBLE_CLICK_NANOS
                    && Math.hypot(event.x() - lastLeftPressX, event.y() - lastLeftPressY) <= DOUBLE_CLICK_DISTANCE;
            lastLeftPressX = event.x();
            lastLeftPressY = event.y();
            if (isDoubleClick) {
                lastLeftPressNanos = 0L; // consumed - the next click starts a fresh double-click window
                String worldId = currentVoxyWorldId();
                GroundPoint ground = mapPointAt(event.x(), event.y());
                if (ground != null && worldId != null) {
                    beginAddWaypoint(ground.x, resolveGroundY(ground.x, ground.z), ground.z);
                }
                return true;
            }
            lastLeftPressNanos = now;

            dragging = true;
            moveVelocityX = 0.0;
            moveVelocityZ = 0.0;
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
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (rightDragging && !rightDragMoved) {
                String worldId = currentVoxyWorldId();
                WaypointManager.Waypoint hit = hitTestWaypoint(event.x(), event.y());
                if (hit != null) {
                    if (worldId != null && !blockedFromEditing(worldId, hit)) {
                        WaypointManager.remove(worldId, hit.id());
                    }
                } else {
                    GroundPoint ground = mapPointAt(event.x(), event.y());
                    if (ground != null && worldId != null) {
                        beginAddWaypoint(ground.x, resolveGroundY(ground.x, ground.z), ground.z);
                    }
                }
            }
            rightDragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (dragging) {
            // Anchor-based, not a hand-tuned pixel-to-world sign/scale conversion: reuses the same
            // "grab this ground point, keep it under the mouse" technique zoomAt already uses, so it's
            // automatically correct for the current yaw/pitch/FOV instead of needing its own signs
            // kept in sync with projectToScreen's - a mismatch there is exactly what made dragging feel
            // inverted (content moved opposite the cursor instead of following it).
            GroundPoint anchor = mapPointAt(event.x() - deltaX, event.y() - deltaY);
            GroundPoint current = mapPointAt(event.x(), event.y());
            if (anchor != null && current != null) {
                double dx = anchor.x - current.x;
                double dz = anchor.z - current.z;
                double distance = Math.hypot(dx, dz);
                if (distance > MAX_DRAG_STEP_BLOCKS) {
                    double scale = MAX_DRAG_STEP_BLOCKS / distance;
                    dx *= scale;
                    dz *= scale;
                }
                viewCenterX += dx;
                viewCenterZ += dz;
            }
            syncWorldCamera();
            return true;
        }
        if (rightDragging) {
            if (!rightDragMoved && Math.hypot(event.x() - rightPressX, event.y() - rightPressY) > RIGHT_CLICK_DRAG_THRESHOLD) {
                rightDragMoved = true;
            }
            viewYaw += deltaX * 0.01;
            syncWorldCamera();
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (mapShareSelectionActive) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_M) {
                cancelMapShareSelection();
                settingsOpen = true;
            }
            return true;
        }
        if (waypointDraftActive || textInputKind != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (waypointDraftActive) cancelWaypointDraft(); else cancelTextInput();
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (waypointDraftActive) confirmWaypointDraft(); else confirmTextInput();
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && nameInputBuffer.length() > 0) {
                nameInputBuffer.deleteCharAt(nameInputBuffer.length() - 1);
            } else if (event.isPaste()) {
                pasteFromClipboard();
            } else if (event.isCopy()) {
                minecraft.keyboardHandler.setClipboard(nameInputBuffer.toString());
            } else if (event.isCut()) {
                minecraft.keyboardHandler.setClipboard(nameInputBuffer.toString());
                nameInputBuffer.setLength(0);
            }
            // Swallow every other key (WASD, zoom, etc.) so it can't leak into map controls while typing.
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_M) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            WaypointManager.Waypoint hovered = hitTestWaypoint(lastMouseX, lastMouseY);
            String worldId = currentVoxyWorldId();
            if (hovered != null && worldId != null && !blockedFromEditing(worldId, hovered)) {
                WaypointManager.remove(worldId, hovered.id());
            }
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

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (waypointDraftActive || textInputKind != null) {
            boolean allowed = event.isAllowedChatCharacter()
                    && (textInputKind != TextInputKind.RECEIVE_CODE || Character.isDigit(event.codepoint()));
            if (allowed && nameInputBuffer.length() < activeInputMaxLength()) {
                nameInputBuffer.append(event.codepointAsString());
            }
            return true;
        }
        return super.charTyped(event);
    }

    private int activeInputMaxLength() {
        return waypointDraftActive ? WAYPOINT_NAME_MAX_LENGTH : textInputMaxLength();
    }

    private void pasteFromClipboard() {
        if (minecraft == null) return;
        String clipboard = minecraft.keyboardHandler.getClipboard();
        if (clipboard == null || clipboard.isEmpty()) return;
        int maxLength = activeInputMaxLength();
        for (int i = 0; i < clipboard.length() && nameInputBuffer.length() < maxLength; i++) {
            char c = clipboard.charAt(i);
            if (c == '\n' || c == '\r' || (c < 0x20 && c != '\t')) continue;
            nameInputBuffer.append(c);
        }
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
        cancelMapShareSelection();
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

    private record PlayerMarkerCluster(List<ProjectedPlayerMarker> members, ScreenPoint anchor) {
    }

    private record ScreenRect(int x, int y, int width, int height) {
        private int right() {
            return x + width;
        }

        private int bottom() {
            return y + height;
        }

        private boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }
    }

    private record ProjectedWaypoint(WaypointManager.Waypoint waypoint, ScreenPoint point, String label,
                                     int labelX, int labelY, int labelW, int labelH) {
    }

    private enum TextInputKind { WAYPOINT_RENAME, SYNC_ADDRESS, SYNC_PASSWORD, RECEIVE_CODE }

    private record PlayerMarker(String name, double x, double y, double z, boolean local, boolean remote,
                                UUID uuid, float yawDegrees) {
        private boolean isActivePlayer() {
            return !local && !remote && Float.isFinite(yawDegrees);
        }
    }
}
