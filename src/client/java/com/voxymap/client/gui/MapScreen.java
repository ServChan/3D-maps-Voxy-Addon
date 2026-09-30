package com.voxymap.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.voxymap.client.VoxyMapClient;
import com.voxymap.client.integration.LitematicaPlusBridge;
import com.voxymap.client.integration.VanillaSquadFlagMapBridge;
import com.voxymap.client.integration.RemotePlayerMarkers;
import com.voxymap.client.integration.VanillaSquadServer;
import com.voxymap.client.integration.XaeroWorldMapBridge;
import com.voxymap.client.map.MapRenderSettingsGuard;
import com.voxymap.client.map.MapScreenshot;
import com.voxymap.client.map.MapShareBounds;
import com.voxymap.client.map.PlayerViewConeGeometry;
import com.voxymap.client.map.NetherRoofController;
import com.voxymap.client.map.VoxyBridge;
import com.voxymap.client.map.VoxyMapCameraController;
import com.voxymap.client.map.VoxyMapGuiRenderer;
import com.voxymap.client.map.VoxyMapPicker;
import com.voxymap.client.map.VoxyMapSettings;
import com.voxymap.client.map.MapShareClient;
import com.voxymap.client.map.WaypointManager;
import com.voxymap.client.map.WaypointSyncSettings;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.sdl.SDLScancode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class MapScreen extends Screen {

    private static final Component TITLE = Component.translatable("screen.voxymap.title");

    private static final long OPEN_ANIMATION_NANOS = 850_000_000L;
    private static final long XAERO_TRANSITION_NANOS = 380_000_000L;
    private static final double MAX_KEYBOARD_CAMERA_SPEED = 640.0;
    private static final double MAX_DRAG_STEP_BLOCKS = 1536.0;
    private static final double PLAYER_VIEW_CONE_HALF_ANGLE = 32.0;
    private static final double LOCAL_VIEW_SCREEN_LENGTH = 90.0;
    private static final double PLAYER_VIEW_CONE_SCREEN_LENGTH = 34.0;

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
    private static final long DOUBLE_CLICK_NANOS = 350_000_000L;
    private static final double DOUBLE_CLICK_DISTANCE = 6.0;
    private double viewCenterX;
    private double viewCenterY;
    private double viewCenterZ;
    private double blocksPerPixel = 4.0;
    private double viewYaw = Math.toRadians(45.0);
    private double viewPitch = 0.68;

    private double camForwardX, camForwardY, camForwardZ;
    private double camRightX, camRightZ;
    private double camUpX, camUpY, camUpZ;
    private double camTanX, camTanY;

    private boolean dragging = false;
    private boolean rightDragging = false;
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
    private int settingsScroll = 0;
    private int settingsMaxScroll = 0;
    private int speedMinusX = -1;
    private int speedMinusY = -1;
    private int speedPlusX = -1;
    private int speedPlusY = -1;
    private final Map<SettingsCatalog.Entry, Integer> settingRowY = new EnumMap<>(SettingsCatalog.Entry.class);
    private final MapObjectPanel objectPanel = new MapObjectPanel();
    private long lastObjectPanelRefreshNanos;
    private String selectedObjectId;
    private String followingPlayerId;
    private List<ProjectedWaypoint> lastProjectedWaypoints = List.of();
    private String selectedWaypointId;
    private WaypointManager.Waypoint deletedWaypoint;
    private String deletedWaypointWorldId;
    private long undoDeleteUntilNanos;
    private long pickFailureUntilNanos;
    private boolean surfacePickPending;
    private long surfacePickRequest;

    private VoxyMapPicker.Hit hoverCursorHit;
    private boolean hoverCursorPickPending;
    private long hoverCursorPickRequest;
    private long lastHoverCursorPickNanos;
    private double lastHoverCursorPickMouseX = Double.NaN;
    private double lastHoverCursorPickMouseY = Double.NaN;
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
    private static final int SETTINGS_HEADER_HEIGHT = 18;
    private static final int SETTINGS_TITLE_HEIGHT = 30;
    private static final int SETTINGS_BOTTOM_PADDING = 8;
    private static final int SETTINGS_ERROR_DETAIL_HEIGHT = 12;
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
        if (MapScreenshot.markersOnlyFrame()) {

            drawFlagMarkers(g);
            drawWaypoints(g, -1, -1);
            drawPlayerMarkers(g, -1, -1);
            return;
        }
        drawWorldViewerOverlay(g, mouseX, mouseY);
        drawOpeningAnimation(g);
        drawTextInputOverlay(g);
        drawWaypointDraftOverlay(g);
        drawTransientMapMessage(g);
        if (xaeroTransitionActive) {
            drawXaeroTransition(g);
            if (!xaeroOpenScheduled && System.nanoTime() - xaeroTransitionStartedAt >= XAERO_TRANSITION_NANOS) {

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

            moveVelocityX = 0.0;
            moveVelocityZ = 0.0;
            return;
        }

        double forwardInput = keyDown(InputConstants.KEY_W) || keyDown(InputConstants.KEY_UP) ? 1.0 : 0.0;
        forwardInput -= keyDown(InputConstants.KEY_S) || keyDown(InputConstants.KEY_DOWN) ? 1.0 : 0.0;
        double strafeInput = keyDown(InputConstants.KEY_A) || keyDown(InputConstants.KEY_LEFT) ? 1.0 : 0.0;
        strafeInput -= keyDown(InputConstants.KEY_D) || keyDown(InputConstants.KEY_RIGHT) ? 1.0 : 0.0;

        double horizontalInput = Math.hypot(forwardInput, strafeInput);
        if (horizontalInput > 1.0) {
            forwardInput /= horizontalInput;
            strafeInput /= horizontalInput;
        }

        if (horizontalInput > 0.0 || dragging) {
            stopFollowing();
        } else if (followingPlayerId != null) {
            PlayerMarker target = playerByObjectId(followingPlayerId);
            if (target != null) {
                double followResponse = 1.0 - Math.exp(-dt * 8.0);
                viewCenterX = Mth.lerp(followResponse, viewCenterX, target.x);
                viewCenterY = Mth.lerp(followResponse, viewCenterY, target.y);
                viewCenterZ = Mth.lerp(followResponse, viewCenterZ, target.z);
            }
        }

        double speedBoost = keyDown(InputConstants.KEY_LSHIFT) || keyDown(InputConstants.KEY_RSHIFT) ? 2.5 : 1.0;
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

    private static boolean keyDown(int key) {
        return InputConstants.isKeyDown(key);
    }

    private GroundPoint mapPointAt(double mouseX, double mouseY) {
        if (width <= 0 || height <= 0 || !VoxyMapCameraController.isActive()) {
            return null;
        }

        VoxyMapPicker.Ray ray = mapRayAt(mouseX, mouseY);
        if (ray == null) return null;
        double cameraX = ray.originX();
        double cameraY = ray.originY();
        double cameraZ = ray.originZ();
        double planeY = viewCenterY;

        double rayX = ray.directionX();
        double rayY = ray.directionY();
        double rayZ = ray.directionZ();

        if (Math.abs(rayY) < 1.0E-6) {
            return null;
        }

        double t = (planeY - cameraY) / rayY;
        if (t <= 0.0 || !Double.isFinite(t)) {
            return null;
        }
        return new GroundPoint(cameraX + rayX * t, cameraZ + rayZ * t);
    }

    private VoxyMapPicker.Ray mapRayAt(double mouseX, double mouseY) {
        if (width <= 0 || height <= 0 || !VoxyMapCameraController.isActive()) return null;
        double ndcX = 1.0 - mouseX / width * 2.0;
        double ndcY = 1.0 - mouseY / height * 2.0;
        return new VoxyMapPicker.Ray(
                VoxyMapCameraController.cameraX(), VoxyMapCameraController.cameraY(), VoxyMapCameraController.cameraZ(),
                camForwardX + camRightX * ndcX * camTanX + camUpX * ndcY * camTanY,
                camForwardY + camUpY * ndcY * camTanY,
                camForwardZ + camRightZ * ndcX * camTanX + camUpZ * ndcY * camTanY);
    }

    private void pickSurfaceAt(double mouseX, double mouseY, Consumer<VoxyMapPicker.Hit> resultConsumer) {
        if (surfacePickPending || minecraft == null || minecraft.level == null) return;
        VoxyMapPicker.Ray ray = mapRayAt(mouseX, mouseY);
        WorldIdentifier world = WorldIdentifier.of(minecraft.level);
        if (ray == null || world == null) {
            resultConsumer.accept(null);
            return;
        }
        String expectedWorldId = world.getWorldId();
        long request = ++surfacePickRequest;
        surfacePickPending = true;
        VoxyMapPicker.pickAsync(world, ray, 32_768.0,
                minecraft.level.getMinY(), minecraft.level.getMinY() + minecraft.level.getHeight()).whenComplete((hit, throwable) -> minecraft.execute(() -> {
            if (request != surfacePickRequest) return;
            surfacePickPending = false;
            if (!expectedWorldId.equals(currentVoxyWorldId())) return;
            if (throwable != null) {
                VoxyMapClient.LOGGER.warn("[VoxyMap] Asynchronous surface pick failed.", throwable);
                resultConsumer.accept(null);
            } else {
                resultConsumer.accept(hit);
            }
        }));
    }

    private void updateHoverCursorHit(double mouseX, double mouseY) {
        if (minecraft == null || minecraft.level == null || width <= 0 || height <= 0
                || !VoxyMapCameraController.isActive()
                || mouseX < 0 || mouseY < 0 || mouseX >= width || mouseY >= height) {
            hoverCursorHit = null;
            return;
        }

        boolean moved = Math.abs(mouseX - lastHoverCursorPickMouseX) > 0.5
                || Math.abs(mouseY - lastHoverCursorPickMouseY) > 0.5;
        long now = System.nanoTime();
        if (hoverCursorPickPending || (!moved && now - lastHoverCursorPickNanos < 100_000_000L)) {
            return;
        }

        VoxyMapPicker.Ray ray = mapRayAt(mouseX, mouseY);
        WorldIdentifier world = WorldIdentifier.of(minecraft.level);
        if (ray == null || world == null) {
            hoverCursorHit = null;
            return;
        }

        lastHoverCursorPickMouseX = mouseX;
        lastHoverCursorPickMouseY = mouseY;
        lastHoverCursorPickNanos = now;
        String expectedWorldId = world.getWorldId();
        long request = ++hoverCursorPickRequest;
        hoverCursorPickPending = true;
        VoxyMapPicker.pickAsync(world, ray, 32_768.0,
                minecraft.level.getMinY(), minecraft.level.getMinY() + minecraft.level.getHeight()).whenComplete((hit, throwable) -> minecraft.execute(() -> {
            if (request != hoverCursorPickRequest) return;
            hoverCursorPickPending = false;
            if (throwable != null || !expectedWorldId.equals(currentVoxyWorldId())) return;
            hoverCursorHit = hit;
        }));
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

        syncWorldCamera();
        NetherRoofController.tick(minecraft);
        if (minecraft != null && minecraft.level != null) {
            String dimensionId = minecraft.level.dimension().identifier().toString();
            if (minecraft.player != null) {
                List<RemotePlayerMarkers.Observer> observers = minecraft.level.players().stream()
                        .map(player -> new RemotePlayerMarkers.Observer(
                                RemotePlayerMarkers.normalizeUuid(player.getUUID().toString()),
                                player.getName().getString(), player.getX(), player.getZ()))
                        .toList();
                RemotePlayerMarkers.tick(minecraft.level, dimensionId, minecraft.player.getUUID(),
                        minecraft.player.getName().getString(), minecraft.player.getX(), minecraft.player.getZ(), observers,
                        currentVoxyWorldId());
            }
            VanillaSquadFlagMapBridge.tick(RemotePlayerMarkers.activeMapId(currentVoxyWorldId()), dimensionId);
        }
        capturePlayerMarkers();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
    }

    private void syncWorldCamera() {
        NetherRoofController.updateViewContext(
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
                {Component.translatable("overlay.voxymap.key.objects.button").getString(), Component.translatable("overlay.voxymap.key.objects").getString()},
                {Component.translatable("overlay.voxymap.key.follow.button").getString(), Component.translatable("overlay.voxymap.key.follow").getString()},
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
            if (settingsOpen) settingsScroll = 0;
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

        SettingsCatalog.Entry entry = settingsEntryAt(mouseX, mouseY);
        if (entry != null) {
            activateSetting(entry, mouseX);
        }
        return true;
    }

    private SettingsCatalog.Entry settingsEntryAt(double mouseX, double mouseY) {
        for (Map.Entry<SettingsCatalog.Entry, Integer> row : settingRowY.entrySet()) {
            if (isInsideSettingsRow(mouseX, mouseY, row.getValue())) {
                return row.getKey();
            }
        }
        return null;
    }

    private void activateSetting(SettingsCatalog.Entry entry, double mouseX) {
        String worldId = currentVoxyWorldId();
        switch (entry) {
            case PAUSE -> VoxyMapSettings.togglePauseSingleplayer();
            case SPEED -> VoxyMapSettings.changeCameraSpeed(mouseX < settingsPanelX + settingsPanelW / 2.0 ? -0.25 : 0.25);
            case TIME_OF_DAY -> VoxyMapSettings.cycleTimeOfDayMode();
            case FOG_SUPPRESSION -> VoxyMapSettings.cycleFogSuppression();
            case SCREENSHOT -> VoxyMapSettings.cycleScreenshotContent();
            case VIEW_DIRECTION -> VoxyMapSettings.toggleViewDirection();
            case WAYPOINTS -> VoxyMapSettings.toggleWaypointsVisible();
            case DEATH_POINTS -> VoxyMapSettings.toggleDeathPoints();
            case VANILLASQUAD_PLAYERS -> VoxyMapSettings.toggleVanillaSquadPlayers();
            case VANILLASQUAD_FLAGS -> VoxyMapSettings.toggleVanillaSquadFlags();
            case VANILLASQUAD_MAP -> VoxyMapSettings.cycleVanillaSquadMap(worldId);
            case MINIMAP -> VoxyMapSettings.toggleMinimap();
            case MINIMAP_ROTATE -> VoxyMapSettings.toggleMinimapRotates();
            case MOBS -> VoxyMapSettings.toggleMobs();
            case CHUNK_GRID -> VoxyMapSettings.toggleChunkGrid();
            case SYNC_ADDRESS -> {
                if (worldId != null) beginEditSyncAddress(worldId);
            }
            case SYNC_PASSWORD -> {
                if (worldId != null) beginEditSyncPassword(worldId);
            }
            case MAP_SHARE -> {
                String address = worldId != null ? WaypointSyncSettings.address(worldId) : null;
                if (address != null) {
                    if (MapShareClient.isSharing()) {
                        MapShareClient.stopSharing();
                        mapShareBounds = null;
                    } else {
                        beginMapShareSelection(address);
                    }
                }
            }
            case MAP_RECEIVE -> {
                if (MapShareClient.receiveStatus() == MapShareClient.ReceiveStatus.AWAITING_CONFIRMATION) {
                    MapShareClient.confirmReceive(minecraft);
                } else if (MapShareClient.receiveStatus() == MapShareClient.ReceiveStatus.RECEIVING
                        || MapShareClient.receiveStatus() == MapShareClient.ReceiveStatus.CONNECTING) {
                    MapShareClient.stopReceiving();
                } else {
                    String address = worldId != null ? WaypointSyncSettings.address(worldId) : null;
                    if (address != null) {
                        textInputKind = TextInputKind.RECEIVE_CODE;
                        nameInputBuffer.setLength(0);
                    }
                }
            }
            case SYNC_STATUS, DIAGNOSTICS -> {

            }
        }
    }

    private boolean isInsideSettingsRow(double mouseX, double mouseY, int rowY) {
        return isSettingsRowVisible(rowY)
                && mouseX >= settingsPanelX + 8 && mouseX <= settingsPanelX + settingsPanelW - 8
                && mouseY >= rowY && mouseY <= rowY + SETTINGS_ROW_HEIGHT - 2;
    }

    private boolean isSettingsRowVisible(int rowY) {
        return SettingsPanelLayout.rowVisible(rowY, settingsPanelY, settingsPanelH, SETTINGS_ROW_HEIGHT);
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
        drawSchematicPlacements(g, mouseX, mouseY);
        drawWaypoints(g, mouseX, mouseY);
        drawPlayerMarkers(g, mouseX, mouseY);
        drawMapShareSelection(g);

        int slide = (int) ((1.0 - openingProgress()) * 28.0);
        int topX = HUD_MARGIN;
        int topY = HUD_MARGIN - slide;
        String title = "VoxyMap 3D";
        String playerText = "";
        if (minecraft.player != null) {
            int px = Mth.floor(minecraft.player.getX());
            int py = Mth.floor(minecraft.player.getY());
            int pz = Mth.floor(minecraft.player.getZ());
            playerText = Component.translatable("overlay.voxymap.player").getString() + "  " + px + " / " + py + " / " + pz;
        }
        String cameraText = Component.translatable("overlay.voxymap.camera").getString() + "  " + Mth.floor(viewCenterX) + " / " + Mth.floor(viewCenterY) + " / " + Mth.floor(viewCenterZ)
                + "   " + Component.translatable("overlay.voxymap.zoom").getString() + " " + String.format("%.2f", blocksPerPixel);
        if (NetherRoofController.isActive()) {
            cameraText += "   " + Component.translatable("overlay.voxymap.slice").getString();
        }
        updateHoverCursorHit(mouseX, mouseY);
        VoxyMapPicker.Hit cursorHit = hoverCursorHit;
        String cursorText = Component.translatable("overlay.voxymap.cursor").getString() + "  "
                + (cursorHit != null ? cursorHit.blockX() + " / " + cursorHit.blockY() + " / " + cursorHit.blockZ() : "—");
        int topW = Math.max(1, Math.min(width - HUD_MARGIN * 2, Math.max(minecraft.font.width(title),
                Math.max(minecraft.font.width(playerText), Math.max(minecraft.font.width(cameraText), minecraft.font.width(cursorText)))) + 20));
        int topH = (minecraft.player != null ? 42 : 30) + 13;
        drawGlassPanel(g, topX, topY, topW, topH, 0xAA030914, 0xFF3BA4FF);
        g.text(minecraft.font, Component.literal(title), topX + 10, topY + 5, 0xFFFFFFFF);
        if (minecraft.player != null) {
            g.text(minecraft.font, playerText, topX + 10, topY + 18, 0xFFDFF8FF);
        }
        g.text(minecraft.font, cameraText, topX + 10, topY + (minecraft.player != null ? 31 : 18), 0xFF9FD8FF);
        g.text(minecraft.font, cursorText, topX + 10, topY + (minecraft.player != null ? 44 : 31), 0xFF7FE8B8);

        int footerTop = drawFooter(g, slide);
        drawXaeroButton(g, mouseX, mouseY, slide, topX, topY, topW, topH);
        drawSettingsButton(g, mouseX, mouseY, slide);
        if (settingsOpen) {
            drawSettingsPanel(g, footerTop);
        }
        if (objectPanel.isOpen() && System.nanoTime() - lastObjectPanelRefreshNanos > 500_000_000L) {
            refreshObjectPanel();
        }
        objectPanel.render(g, minecraft.font, width, height, mouseX, mouseY);
    }

    private void refreshObjectPanel() {
        lastObjectPanelRefreshNanos = System.nanoTime();
        List<MapObjectPanel.Entry> entries = new ArrayList<>();
        if (minecraft != null && minecraft.player != null) {
            for (PlayerMarker marker : playerMarkers) {
                long distance = Math.round(Math.hypot(marker.x - minecraft.player.getX(), marker.z - minecraft.player.getZ()));
                String detail = Math.round(marker.x) + " / " + Math.round(marker.y) + " / " + Math.round(marker.z)
                        + "  ·  " + distance + "m";
                entries.add(new MapObjectPanel.Entry(playerObjectId(marker), MapObjectPanel.Kind.PLAYER,
                        marker.name, detail, marker.x, marker.y, marker.z,
                        marker.local ? 0xFF55D8FF : marker.remote ? 0xFFFFC94D : 0xFFFF55CC, true, false));
            }
        }
        String worldId = currentVoxyWorldId();
        if (worldId != null) {
            for (WaypointManager.Waypoint waypoint : WaypointManager.forWorld(worldId)) {
                long distance = minecraft != null && minecraft.player != null
                        ? Math.round(Math.hypot(waypoint.x() - minecraft.player.getX(), waypoint.z() - minecraft.player.getZ())) : 0L;
                String detail = Math.round(waypoint.x()) + " / " + Math.round(waypoint.y()) + " / " + Math.round(waypoint.z())
                        + (minecraft != null && minecraft.player != null ? "  ·  " + distance + "m" : "");
                entries.add(new MapObjectPanel.Entry(waypointObjectId(waypoint), MapObjectPanel.Kind.WAYPOINT,
                        waypoint.name(), detail, waypoint.x(), waypoint.y(), waypoint.z(), waypoint.color(), false,
                        !blockedFromEditingSilently(worldId, waypoint)));
            }
        }
        if (minecraft != null && minecraft.level != null && VoxyMapSettings.vanillaSquadFlagsActive()) {
            String dimensionId = minecraft.level.dimension().identifier().toString();
            for (VanillaSquadFlagMapBridge.Territory territory : VanillaSquadFlagMapBridge.territoriesInDimension(
                    RemotePlayerMarkers.activeMapId(currentVoxyWorldId()), dimensionId)) {
                entries.add(new MapObjectPanel.Entry(territoryObjectId(territory), MapObjectPanel.Kind.TERRITORY,
                        territory.name(), Math.round(territory.centroidX()) + " / " + Math.round(territory.centroidZ()),
                        territory.centroidX(), viewCenterY, territory.centroidZ(), territory.color(), false, false));
            }
        }
        for (LitematicaPlusBridge.Placement placement : LitematicaPlusBridge.placements()) {
            entries.add(new MapObjectPanel.Entry(schematicObjectId(placement), MapObjectPanel.Kind.SCHEMATIC,
                    placement.name(), schematicDetail(placement), placement.centerX(), placement.maxY(),
                    placement.centerZ(), schematicColor(placement), false, true));
        }
        objectPanel.setObjects(entries, selectedObjectId, followingPlayerId);
    }

    private void handleObjectPanelAction(MapObjectPanel.Action action) {
        if (action == null || action.type() == MapObjectPanel.ActionType.CONSUMED) return;
        if (action.type() == MapObjectPanel.ActionType.REFILTER) {
            refreshObjectPanel();
            return;
        }
        MapObjectPanel.Entry entry = action.entry();
        if (entry == null) return;
        if (entry.kind() == MapObjectPanel.Kind.SCHEMATIC) {
            handleSchematicAction(action.type(), entry);
            return;
        }
        if (action.type() == MapObjectPanel.ActionType.SELECT) {
            selectObject(entry);
        } else if (action.type() == MapObjectPanel.ActionType.CENTER) {
            selectObject(entry);
            stopFollowing();
            viewCenterX = entry.x();
            viewCenterY = entry.y();
            viewCenterZ = entry.z();
            moveVelocityX = moveVelocityZ = 0.0;
            syncWorldCamera();
        } else if (action.type() == MapObjectPanel.ActionType.FOLLOW) {
            selectObject(entry);
            followingPlayerId = followingPlayerId != null && followingPlayerId.equals(entry.id()) ? null : entry.id();
        } else if (action.type() == MapObjectPanel.ActionType.EDIT) {
            selectObject(entry);
            WaypointManager.Waypoint waypoint = selectedWaypoint();
            if (waypoint != null && !blockedFromEditing(currentVoxyWorldId(), waypoint)) beginRenameWaypoint(waypoint);
        } else if (action.type() == MapObjectPanel.ActionType.DELETE) {
            selectObject(entry);
            deleteSelectedWaypoint();
        }
    }

    private void selectObject(MapObjectPanel.Entry entry) {
        selectedObjectId = entry.id();
        selectedWaypointId = entry.kind() == MapObjectPanel.Kind.WAYPOINT ? entry.id().substring("waypoint:".length()) : null;
    }

    private void stopFollowing() {
        followingPlayerId = null;
    }

    private static final String SCHEMATIC_PREFIX = "schematic:";
    private static final int SCHEMATIC_COLOR = 0xFF2FC7FF;
    private static final int SCHEMATIC_SELECTED_COLOR = 0xFF00E5FF;
    private static final int SCHEMATIC_HIDDEN_COLOR = 0xFF8FA7BF;
    private List<ScreenRect> schematicLabelRects = List.of();
    private List<String> schematicLabelIds = List.of();

    private static String schematicObjectId(LitematicaPlusBridge.Placement placement) {
        return SCHEMATIC_PREFIX + placement.id();
    }

    private static int schematicColor(LitematicaPlusBridge.Placement placement) {
        return !placement.shown() ? SCHEMATIC_HIDDEN_COLOR : placement.selected() ? SCHEMATIC_SELECTED_COLOR : SCHEMATIC_COLOR;
    }

    private static String schematicDetail(LitematicaPlusBridge.Placement placement) {
        String size = (placement.maxX() - placement.minX()) + " x " + (placement.maxY() - placement.minY()) + " x "
                + (placement.maxZ() - placement.minZ());
        return placement.progress() < 0.0 ? size
                : String.format(java.util.Locale.ROOT, "%.1f%%  ·  %s", placement.progress() * 100.0, size);
    }

    private void handleSchematicAction(MapObjectPanel.ActionType type, MapObjectPanel.Entry entry) {
        String id = entry.id().substring(SCHEMATIC_PREFIX.length());
        selectedObjectId = entry.id();
        selectedWaypointId = null;
        if (type == MapObjectPanel.ActionType.DELETE || type == MapObjectPanel.ActionType.FOLLOW) {
            return;
        }
        LitematicaPlusBridge.select(id);
        if (type == MapObjectPanel.ActionType.CENTER) {
            stopFollowing();
            viewCenterX = entry.x();
            viewCenterY = entry.y();
            viewCenterZ = entry.z();
            moveVelocityX = moveVelocityZ = 0.0;
            syncWorldCamera();
        } else if (type == MapObjectPanel.ActionType.EDIT) {
            closeMapView();
            LitematicaPlusBridge.openEditor(id);
        }
    }

    private String hitTestSchematic(double mouseX, double mouseY) {
        for (int i = 0; i < schematicLabelRects.size(); i++) {
            if (schematicLabelRects.get(i).contains(mouseX, mouseY)) {
                return schematicLabelIds.get(i);
            }
        }
        return null;
    }

    private void drawSchematicPlacements(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        schematicLabelRects = List.of();
        schematicLabelIds = List.of();
        if (!VoxyMapCameraController.isActive() || !LitematicaPlusBridge.isAvailable()) {
            return;
        }
        List<ScreenRect> rects = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (LitematicaPlusBridge.Placement placement : LitematicaPlusBridge.placements()) {
            int color = schematicColor(placement);
            double x0 = placement.minX();
            double y0 = placement.minY();
            double z0 = placement.minZ();
            double x1 = placement.maxX();
            double y1 = placement.maxY();
            double z1 = placement.maxZ();
            for (int i = 0; i < 2; i++) {
                double y = i == 0 ? y0 : y1;
                draw3DLine(g, x0, y, z0, x1, y, z0, color);
                draw3DLine(g, x1, y, z0, x1, y, z1, color);
                draw3DLine(g, x1, y, z1, x0, y, z1, color);
                draw3DLine(g, x0, y, z1, x0, y, z0, color);
            }
            draw3DLine(g, x0, y0, z0, x0, y1, z0, color);
            draw3DLine(g, x1, y0, z0, x1, y1, z0, color);
            draw3DLine(g, x1, y0, z1, x1, y1, z1, color);
            draw3DLine(g, x0, y0, z1, x0, y1, z1, color);

            ScreenPoint labelPoint = projectToScreen(placement.centerX(), y1, placement.centerZ());
            if (labelPoint == null || labelPoint.x < 6 || labelPoint.x >= width - 6
                    || labelPoint.y < 12 || labelPoint.y >= height - 6) {
                continue;
            }
            String label = placement.progress() < 0.0 ? placement.name()
                    : placement.name() + "  " + String.format(java.util.Locale.ROOT, "%.1f%%", placement.progress() * 100.0);
            int labelWidth = minecraft.font.width(label) + 8;
            int labelX = Mth.clamp(labelPoint.x - labelWidth / 2, 3, Math.max(3, width - labelWidth - 3));
            int labelY = labelPoint.y - 14;
            ScreenRect rect = new ScreenRect(labelX, labelY, labelWidth, 11);
            boolean hovered = rect.contains(mouseX, mouseY) || schematicObjectId(placement).equals(selectedObjectId);
            g.fill(labelX, labelY, labelX + labelWidth, labelY + 11, hovered ? 0xEE030914 : 0xCC030914);
            g.fill(labelX, labelY + 10, labelX + labelWidth, labelY + 11, color);
            if (placement.progress() >= 0.0) {
                g.fill(labelX, labelY + 10, labelX + (int) Math.round(labelWidth * Math.min(1.0, placement.progress())),
                        labelY + 11, 0xFF55FF55);
            }
            g.text(minecraft.font, label, labelX + 4, labelY + 2, 0xFFE9F2FF);
            rects.add(rect);
            ids.add(placement.id());
        }
        schematicLabelRects = List.copyOf(rects);
        schematicLabelIds = List.copyOf(ids);
    }

    private PlayerMarker playerByObjectId(String id) {
        if (id == null) return null;
        for (PlayerMarker marker : playerMarkers) if (playerObjectId(marker).equals(id)) return marker;
        return null;
    }

    private static String playerObjectId(PlayerMarker marker) {
        return "player:" + (marker.uuid != null ? marker.uuid : marker.name.toLowerCase(java.util.Locale.ROOT));
    }

    private static String waypointObjectId(WaypointManager.Waypoint waypoint) {
        return "waypoint:" + waypoint.id();
    }

    private static String territoryObjectId(VanillaSquadFlagMapBridge.Territory territory) {
        return "territory:" + territory.name() + ":" + Math.round(territory.centroidX()) + ":" + Math.round(territory.centroidZ());
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
            knownUuids.add(RemotePlayerMarkers.normalizeUuid(player.getUUID().toString()));
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

        if (VoxyMapSettings.vanillaSquadPlayersActive()) {
            String dimensionId = minecraft.level.dimension().identifier().toString();
            for (RemotePlayerMarkers.RemotePlayer remote : RemotePlayerMarkers.playersInDimension(dimensionId)) {
                if ((remote.uuid() != null && knownUuids.contains(remote.uuid()))
                        || knownNames.contains(remote.name().toLowerCase(java.util.Locale.ROOT))) {
                    continue;
                }
                UUID uuid = RemotePlayerMarkers.toJavaUuid(remote.uuid());
                snapshot.add(new PlayerMarker(remote.name(), remote.x(), remote.y(), remote.z(), false, true,
                        uuid, Float.NaN));
            }
        }

        playerMarkers = List.copyOf(snapshot);
    }

    private void drawFlagMarkers(GuiGraphicsExtractor g) {
        if (!VoxyMapCameraController.isActive() || !VoxyMapSettings.vanillaSquadFlagsActive()
                || minecraft == null || minecraft.level == null) {
            return;
        }

        String dimensionId = minecraft.level.dimension().identifier().toString();
        for (VanillaSquadFlagMapBridge.Territory territory : VanillaSquadFlagMapBridge.territoriesInDimension(
                RemotePlayerMarkers.activeMapId(currentVoxyWorldId()), dimensionId)) {
            List<double[]> vertices = territory.outline();
            if (vertices.isEmpty()) {
                continue;
            }

            boolean anyVisible;
            if (vertices.size() == 1) {

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
            boolean selected = entry.waypoint.id().equals(selectedWaypointId);
            boolean hovered = selected || Math.hypot(entry.point.x - mouseX, entry.point.y - mouseY) <= WAYPOINT_HOVER_DISTANCE;
            drawWaypointPin(g, entry.point, entry.waypoint.color(), hovered);
        }
        List<ProjectedWaypoint> labelOrder = new ArrayList<>(projected);
        labelOrder.sort(Comparator.comparing((ProjectedWaypoint entry) -> !entry.waypoint.id().equals(selectedWaypointId)));
        List<ScreenRect> claimedLabels = new ArrayList<>();
        for (ProjectedWaypoint entry : labelOrder) {
            boolean selected = entry.waypoint.id().equals(selectedWaypointId);
            boolean hovered = selected || Math.hypot(entry.point.x - mouseX, entry.point.y - mouseY) <= WAYPOINT_HOVER_DISTANCE;
            ScreenRect labelBounds = new ScreenRect(entry.labelX, entry.labelY, entry.labelW, entry.labelH);
            boolean overlaps = claimedLabels.stream().anyMatch(existing -> rectanglesOverlap(existing, labelBounds));
            if (overlaps && !hovered) continue;
            g.fill(entry.labelX, entry.labelY, entry.labelX + entry.labelW, entry.labelY + entry.labelH,
                    hovered ? 0xEE030914 : 0xCC030914);
            g.fill(entry.labelX, entry.labelY + entry.labelH - 1, entry.labelX + entry.labelW, entry.labelY + entry.labelH,
                    entry.waypoint.color());
            g.text(minecraft.font, entry.label, entry.labelX + 4, entry.labelY + 2, 0xFFE9F2FF);
            claimedLabels.add(labelBounds);
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

    private static boolean rectanglesOverlap(ScreenRect first, ScreenRect second) {
        return first.x < second.right() && first.right() > second.x
                && first.y < second.bottom() && first.bottom() > second.y;
    }

    private WaypointManager.Waypoint selectedWaypoint() {
        String worldId = currentVoxyWorldId();
        if (worldId == null || selectedWaypointId == null) return null;
        return WaypointManager.forWorld(worldId).stream()
                .filter(waypoint -> waypoint.id().equals(selectedWaypointId)).findFirst().orElse(null);
    }

    private void deleteSelectedWaypoint() {
        String worldId = currentVoxyWorldId();
        WaypointManager.Waypoint selected = selectedWaypoint();
        if (worldId == null || selected == null || blockedFromEditing(worldId, selected)) return;
        deletedWaypoint = selected;
        deletedWaypointWorldId = worldId;
        undoDeleteUntilNanos = System.nanoTime() + 8_000_000_000L;
        WaypointManager.remove(worldId, selected.id());
        selectedWaypointId = null;
        selectedObjectId = null;
    }

    private void undoWaypointDelete() {
        if (deletedWaypoint == null || System.nanoTime() > undoDeleteUntilNanos) return;
        if (WaypointManager.restore(deletedWaypointWorldId, deletedWaypoint)) {
            selectedWaypointId = deletedWaypoint.id();
            selectedObjectId = waypointObjectId(deletedWaypoint);
            deletedWaypoint = null;
            deletedWaypointWorldId = null;
            undoDeleteUntilNanos = 0L;
        }
    }

    private void drawTransientMapMessage(GuiGraphicsExtractor g) {
        String message = null;
        if (surfacePickPending) {
            message = Component.translatable("screen.voxymap.waypoint.picking").getString();
        } else if (System.nanoTime() < pickFailureUntilNanos) {
            message = Component.translatable("screen.voxymap.waypoint.no_surface").getString();
        } else if (deletedWaypoint != null && System.nanoTime() < undoDeleteUntilNanos) {
            message = Component.translatable("screen.voxymap.waypoint.deleted_undo").getString();
        }
        if (message == null) return;
        int boxW = minecraft.font.width(message) + 20;
        int boxX = (width - boxW) / 2;
        int boxY = height - 54;
        drawGlassPanel(g, boxX, boxY, boxW, 20, 0xDD030914, 0xFFFFC94D);
        g.centeredText(minecraft.font, message, width / 2, boxY + 6, 0xFFFFFFFF);
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
            WaypointManager.Waypoint added = WaypointManager.add(
                    worldId, name, pendingWaypointX, pendingWaypointY, pendingWaypointZ, draftColor, draftShared);
            if (added != null) {
                selectedWaypointId = added.id();
                selectedObjectId = waypointObjectId(added);
            }
        }
        cancelWaypointDraft();
    }

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

    private boolean blockedFromEditing(String worldId, WaypointManager.Waypoint waypoint) {
        if (!blockedFromEditingSilently(worldId, waypoint)) return false;
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.translatable("message.voxymap.waypoint.read_only"));
        }
        return true;
    }

    private boolean blockedFromEditingSilently(String worldId, WaypointManager.Waypoint waypoint) {
        return worldId != null && waypoint.shared() && WaypointSyncSettings.password(worldId) == null;
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

    private double[] clipLineToViewport(double x1, double y1, double x2, double y2) {
        return MapProjection.clipLine(x1, y1, x2, y2, width, height);
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

    private List<PlayerMarkerCluster> clusterPlayerMarkers(List<ProjectedPlayerMarker> projected) {
        List<PlayerMarkerClusterer.Marker> inputs = projected.stream()
                .map(entry -> new PlayerMarkerClusterer.Marker(
                        entry.point.x, entry.point.y, playerLabelWidth(entry.marker)))
                .toList();
        return PlayerMarkerClusterer.cluster(inputs, PLAYER_CLUSTER_MIN_X_DISTANCE,
                        PLAYER_CLUSTER_Y_DISTANCE, PLAYER_CLUSTER_LABEL_GAP).stream()
                .map(cluster -> new PlayerMarkerCluster(
                        cluster.memberIndexes().stream().map(projected::get).toList(),
                        new ScreenPoint(cluster.anchorX(), cluster.anchorY())))
                .toList();
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
        int labelX = ClusterLayout.clampOrigin(cluster.anchor.x - labelWidth / 2, labelWidth, width, 3);
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
        int panelX = ClusterLayout.clampOrigin(cluster.anchor.x - panelWidth / 2, panelWidth, width, 3);
        int panelY = ClusterLayout.clampOrigin(cluster.anchor.y + 12, panelHeight, height, 3);
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
        if (marker.local && VoxyMapSettings.viewDirectionEnabled()) {
            drawLocalViewDirection(g, marker, point);
        }
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

    private void drawLocalViewDirection(GuiGraphicsExtractor g, PlayerMarker marker, ScreenPoint origin) {
        if (!Float.isFinite(marker.yawDegrees)) {
            return;
        }
        double halfAngle = localViewHalfAngleDegrees();
        double worldLength = Mth.clamp(blocksPerPixel * LOCAL_VIEW_SCREEN_LENGTH, 16.0, 192.0);
        ScreenPoint left = projectViewConePoint(marker, marker.yawDegrees - halfAngle, worldLength);
        ScreenPoint right = projectViewConePoint(marker, marker.yawDegrees + halfAngle, worldLength);
        ScreenPoint ahead = projectViewConePoint(marker, marker.yawDegrees, worldLength);
        if (left != null && right != null) {
            fillTriangle(g, origin, left, right, 0x2E55E7FF);
            drawSolidLine(g, origin.x, origin.y, left.x, left.y, 0x9955E7FF);
            drawSolidLine(g, origin.x, origin.y, right.x, right.y, 0x9955E7FF);
            drawSolidLine(g, left.x, left.y, right.x, right.y, 0x6655E7FF);
        }
        if (ahead != null) {
            drawSolidLine(g, origin.x, origin.y, ahead.x, ahead.y, 0xFF8CF0FF);
            drawArrowHead(g, origin, ahead, 8, 0xFF8CF0FF);
        }
    }

    private double localViewHalfAngleDegrees() {
        double verticalFov = minecraft != null ? minecraft.options.fov().get() : 70.0;
        double aspect = height > 0 ? width / (double) height : 16.0 / 9.0;
        double half = Math.toDegrees(Math.atan(Math.tan(Math.toRadians(verticalFov) * 0.5) * aspect));
        return Mth.clamp(half, 20.0, 75.0);
    }

    private void drawArrowHead(GuiGraphicsExtractor g, ScreenPoint from, ScreenPoint tip, int size, int color) {
        double dx = tip.x - from.x;
        double dy = tip.y - from.y;
        double length = Math.hypot(dx, dy);
        if (length < 4.0) {
            return;
        }
        dx /= length;
        dy /= length;
        for (int side = -1; side <= 1; side += 2) {
            double cos = Math.cos(Math.toRadians(150.0));
            double sin = Math.sin(Math.toRadians(150.0)) * side;
            double wx = dx * cos - dy * sin;
            double wy = dx * sin + dy * cos;
            drawSolidLine(g, tip.x, tip.y,
                    (int) Math.round(tip.x + wx * size), (int) Math.round(tip.y + wy * size), color);
        }
    }

    private void fillTriangle(GuiGraphicsExtractor g, ScreenPoint a, ScreenPoint b, ScreenPoint c, int color) {
        int[] xs = {a.x, b.x, c.x};
        int[] ys = {a.y, b.y, c.y};
        int minY = Math.max(0, Math.min(a.y, Math.min(b.y, c.y)));
        int maxY = Math.min(height - 1, Math.max(a.y, Math.max(b.y, c.y)));
        for (int y = minY; y <= maxY; y++) {
            double[] span = ScreenTriangle.span(y + 0.5, xs, ys);
            if (span == null) {
                continue;
            }
            int from = Mth.clamp((int) Math.floor(span[0]), 0, width);
            int to = Mth.clamp((int) Math.ceil(span[1]), 0, width);
            if (to > from) {
                g.fill(from, y, to, y + 1, color);
            }
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
        MapProjection.Point point = MapProjection.project(
                worldX, worldY, worldZ,
                VoxyMapCameraController.cameraX(), VoxyMapCameraController.cameraY(), VoxyMapCameraController.cameraZ(),
                camForwardX, camForwardY, camForwardZ,
                camRightX, camRightZ,
                camUpX, camUpY, camUpZ,
                camTanX, camTanY, width, height);
        return point == null ? null : new ScreenPoint(point.x(), point.y());
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
        String worldId = currentVoxyWorldId();
        WaypointManager.SyncStatus syncStatus = worldId != null
                ? WaypointManager.syncStatus(worldId) : WaypointManager.SyncStatus.DISABLED;
        String syncErrorDetail = syncStatus == WaypointManager.SyncStatus.ERROR
                ? WaypointManager.syncErrorDetail(worldId) : null;
        List<SettingsCatalog.Section> sections = SettingsCatalog.sections(true, true, VanillaSquadServer.isConnected(),
                RemotePlayerMarkers.available());

        int naturalPanelH = SETTINGS_TITLE_HEIGHT + SETTINGS_BOTTOM_PADDING
                + (syncErrorDetail != null ? SETTINGS_ERROR_DETAIL_HEIGHT : 0);
        for (SettingsCatalog.Section section : sections) {
            naturalPanelH += SETTINGS_HEADER_HEIGHT + section.entries().size() * SETTINGS_ROW_HEIGHT;
        }
        settingsPanelW = Math.min(360, width - HUD_MARGIN * 2);
        SettingsPanelLayout.Result panelLayout = SettingsPanelLayout.calculate(
                naturalPanelH, footerTop, HUD_MARGIN, HUD_GAP, settingsScroll);
        settingsPanelH = panelLayout.panelHeight();
        settingsMaxScroll = panelLayout.maxScroll();
        settingsScroll = panelLayout.scroll();
        settingsPanelX = width - settingsPanelW - HUD_MARGIN;
        settingsPanelY = Math.max(HUD_MARGIN, footerTop - settingsPanelH - HUD_GAP);
        drawGlassPanel(g, settingsPanelX, settingsPanelY, settingsPanelW, settingsPanelH, 0xDD030914, 0xFF3BA4FF);

        int x = settingsPanelX + 12;
        g.text(minecraft.font, Component.translatable("screen.voxymap.settings.title"), x, settingsPanelY + 10, 0xFFFFFFFF);

        speedMinusX = speedMinusY = speedPlusX = speedPlusY = -1;
        settingRowY.clear();
        int y = settingsPanelY + SETTINGS_TITLE_HEIGHT - settingsScroll;
        for (SettingsCatalog.Section section : sections) {
            drawSettingsGroupHeader(g, x, y, section.group());
            y += SETTINGS_HEADER_HEIGHT;
            for (SettingsCatalog.Entry entry : section.entries()) {
                settingRowY.put(entry, y);
                drawSettingEntry(g, x, y, entry, worldId, syncStatus);
                y += SETTINGS_ROW_HEIGHT;
                if (entry == SettingsCatalog.Entry.SYNC_STATUS && syncErrorDetail != null) {
                    String detailText = syncErrorDetail.length() > 60 ? syncErrorDetail.substring(0, 57) + "..." : syncErrorDetail;
                    if (y >= settingsPanelY + 28 && y + 10 <= settingsPanelY + settingsPanelH - 6) {
                        g.text(minecraft.font, detailText, x, y, 0xFFFF8080);
                    }
                    y += SETTINGS_ERROR_DETAIL_HEIGHT;
                }
            }
        }

        if (settingsMaxScroll > 0) {
            int trackTop = settingsPanelY + 30;
            int trackH = settingsPanelH - 40;
            int thumbH = Math.max(18, trackH * settingsPanelH / naturalPanelH);
            int thumbY = trackTop + (trackH - thumbH) * settingsScroll / settingsMaxScroll;
            g.fill(settingsPanelX + settingsPanelW - 4, trackTop,
                    settingsPanelX + settingsPanelW - 2, trackTop + trackH, 0x553BA4FF);
            g.fill(settingsPanelX + settingsPanelW - 4, thumbY,
                    settingsPanelX + settingsPanelW - 2, thumbY + thumbH, 0xFF7DEBFF);
        }
    }

    private void drawSettingsGroupHeader(GuiGraphicsExtractor g, int x, int y, SettingsCatalog.Group group) {
        if (!SettingsPanelLayout.rowVisible(y, settingsPanelY, settingsPanelH, SETTINGS_HEADER_HEIGHT)) return;
        String title = Component.translatable(group.titleKey()).getString();
        g.text(minecraft.font, title, x, y + 5, 0xFF7DEBFF);
        int lineX = x + minecraft.font.width(title) + 6;
        int lineRight = settingsPanelX + settingsPanelW - 14;
        if (lineRight > lineX) {
            g.fill(lineX, y + 9, lineRight, y + 10, 0x553BA4FF);
        }
    }

    private void drawSettingEntry(GuiGraphicsExtractor g, int x, int y, SettingsCatalog.Entry entry,
                                  String worldId, WaypointManager.SyncStatus syncStatus) {
        String notSet = Component.translatable("screen.voxymap.settings.sync.not_set").getString();
        String address = worldId != null ? WaypointSyncSettings.address(worldId) : null;
        switch (entry) {
            case PAUSE -> drawToggleSetting(g, x, y, entry, VoxyMapSettings.pauseSingleplayer());
            case SPEED -> drawSettingValue(g, x, y, entry,
                    String.format("%.2fx", VoxyMapSettings.cameraSpeedMultiplier()), 0xFFFFFFFF);
            case TIME_OF_DAY -> drawSettingValue(g, x, y, entry,
                    Component.translatable(VoxyMapSettings.timeOfDayMode().translationKey()).getString(), 0xFFFFFFFF);
            case FOG_SUPPRESSION -> {
                VoxyMapSettings.FogSuppression mode = VoxyMapSettings.fogSuppressionMode();
                drawSettingValue(g, x, y, entry, Component.translatable(mode.translationKey()).getString(),
                        switch (mode) {
                            case ALWAYS -> 0xFF55FF55;
                            case NETHER_ONLY -> 0xFFFFC94D;
                            case OFF -> 0xFFFF5555;
                        });
            }
            case SCREENSHOT -> drawSettingValue(g, x, y, entry,
                    Component.translatable(VoxyMapSettings.screenshotContent().translationKey()).getString(), 0xFFFFFFFF);
            case VIEW_DIRECTION -> drawToggleSetting(g, x, y, entry, VoxyMapSettings.viewDirectionEnabled());
            case WAYPOINTS -> drawToggleSetting(g, x, y, entry, VoxyMapSettings.waypointsEnabled());
            case DEATH_POINTS -> drawToggleSetting(g, x, y, entry, VoxyMapSettings.deathPointsEnabled());
            case VANILLASQUAD_PLAYERS -> drawToggleSetting(g, x, y, entry, VoxyMapSettings.vanillaSquadPlayersEnabled());
            case VANILLASQUAD_FLAGS -> drawToggleSetting(g, x, y, entry, VoxyMapSettings.vanillaSquadFlagsEnabled());
            case VANILLASQUAD_MAP -> {
                VoxyMapSettings.VanillaSquadMap map = VoxyMapSettings.vanillaSquadMap(worldId);
                String value = Component.translatable(map.translationKey()).getString();
                if (map == VoxyMapSettings.VanillaSquadMap.AUTO) {
                    value += " → " + RemotePlayerMarkers.activeMapId(worldId);
                }
                drawSettingValue(g, x, y, entry, value, 0xFFFFFFFF);
            }
            case MINIMAP -> drawToggleSetting(g, x, y, entry, VoxyMapSettings.minimapEnabled());
            case MINIMAP_ROTATE -> drawToggleSetting(g, x, y, entry, VoxyMapSettings.minimapRotates());
            case MOBS -> drawToggleSetting(g, x, y, entry, VoxyMapSettings.mobsEnabled());
            case CHUNK_GRID -> drawToggleSetting(g, x, y, entry, VoxyMapSettings.chunkGridEnabled());
            case SYNC_ADDRESS -> drawSettingValue(g, x, y, entry, address != null ? address : notSet, 0xFFFFFFFF);
            case SYNC_PASSWORD -> {
                String password = worldId != null ? WaypointSyncSettings.password(worldId) : null;
                String display = password != null ? "*".repeat(Math.min(password.length(), 12)) : notSet;
                drawSettingValue(g, x, y, entry, display, 0xFFFFFFFF);
            }
            case SYNC_STATUS -> drawSyncStatusRow(g, x, y, syncStatus);
            case MAP_SHARE -> drawSettingValue(g, x, y, entry, mapShareValue(address, notSet), 0xFFFFFFFF);
            case MAP_RECEIVE -> drawSettingValue(g, x, y, entry, mapReceiveValue(address, notSet), 0xFFFFFFFF);
            case DIAGNOSTICS -> drawSettingValue(g, x, y, entry,
                    Component.translatable(SettingsValues.diagnosticsKey()).getString(), 0xFFFFFFFF);
        }
    }

    private void drawToggleSetting(GuiGraphicsExtractor g, int x, int y, SettingsCatalog.Entry entry, boolean enabled) {
        drawSettingValue(g, x, y, entry, onOff(enabled), enabled ? 0xFF55FF55 : 0xFFFF5555);
    }

    private String mapShareValue(String address, String notSet) {
        if (address == null) {
            return notSet;
        }
        if (!MapShareClient.isSharing()) {
            return Component.translatable("screen.voxymap.mapshare.start").getString();
        }
        return MapShareClient.shareCode() + ": " + switch (MapShareClient.shareStatus()) {
            case WAITING -> Component.translatable("screen.voxymap.mapshare.waiting", MapShareClient.shareCompletedCount()).getString();
            case AWAITING_CONFIRMATION -> Component.translatable("screen.voxymap.mapshare.awaiting_confirmation",
                    formatEstimatedSize(MapShareClient.shareEstimatedBytes())).getString();
            case SENDING -> {
                int sent = MapShareClient.shareSectionsSentThisAttempt();
                int total = MapShareClient.shareSectionsTotalThisAttempt();

                yield total > 0
                        ? Component.translatable("screen.voxymap.mapshare.sending", sent, total, (int) Math.min(100L, 100L * sent / total)).getString()
                        : Component.translatable("screen.voxymap.mapshare.sending_unknown", sent).getString();
            }
            case ERROR -> Component.translatable("screen.voxymap.mapshare.error").getString();
            case IDLE -> "";
        };
    }

    private String mapReceiveValue(String address, String notSet) {
        if (address == null) {
            return notSet;
        }
        return switch (MapShareClient.receiveStatus()) {
            case IDLE -> Component.translatable("screen.voxymap.mapshare.get").getString();
            case CONNECTING -> Component.translatable("screen.voxymap.mapshare.connecting").getString();
            case AWAITING_CONFIRMATION -> Component.translatable("screen.voxymap.mapshare.confirm_receive",
                    formatEstimatedSize(MapShareClient.receiveEstimatedBytes())).getString();
            case RECEIVING -> {
                int imported = MapShareClient.receiveImportedCount();
                int skipped = MapShareClient.receiveSkippedCount();
                int done = imported + skipped;
                int total = MapShareClient.receiveTotalCount();

                yield total > 0
                        ? Component.translatable("screen.voxymap.mapshare.receiving", done, total, (int) Math.min(100L, 100L * done / total)).getString()
                        : Component.translatable("screen.voxymap.mapshare.receiving_unknown", done).getString();
            }
            case DONE -> Component.translatable("screen.voxymap.mapshare.done", MapShareClient.receiveImportedCount(), MapShareClient.receiveSkippedCount()).getString();
            case ERROR -> Component.translatable("screen.voxymap.mapshare.error").getString();
        };
    }

    private void drawSyncStatusRow(GuiGraphicsExtractor g, int x, int y, WaypointManager.SyncStatus status) {
        if (!isSettingsRowVisible(y)) return;
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

    private void drawSettingValue(GuiGraphicsExtractor g, int x, int y, SettingsCatalog.Entry entry,
                                  String value, int valueColor) {
        if (!isSettingsRowVisible(y)) return;
        int rowX = settingsPanelX + 8;
        int rowW = settingsPanelW - 16;
        g.fill(rowX, y, rowX + rowW, y + SETTINGS_ROW_HEIGHT - 2, 0x66102034);
        g.fill(rowX, y, rowX + 2, y + SETTINGS_ROW_HEIGHT - 2, 0xFF3BA4FF);
        g.text(minecraft.font, Component.translatable(entry.labelKey()), x, y + 7, 0xFFDFF8FF);
        int valueW = Math.max(62, minecraft.font.width(value) + 14);
        int valueX = settingsPanelX + settingsPanelW - valueW - 14;
        if (entry == SettingsCatalog.Entry.SPEED) {
            valueX -= (SPEED_BUTTON_SIZE + 6);
        }
        drawValueBox(g, valueX, y + 3, valueW, 16, value, valueColor);
        if (entry == SettingsCatalog.Entry.SPEED) {
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
        pickSurfaceAt(mouseX, mouseY, this::acceptMapShareSurface);
        return true;
    }

    private void acceptMapShareSurface(VoxyMapPicker.Hit hit) {
        if (!mapShareSelectionActive) return;
        if (hit == null) {
            pickFailureUntilNanos = System.nanoTime() + 3_000_000_000L;
            return;
        }
        GroundPoint point = new GroundPoint(hit.x() + 0.5, hit.z() + 0.5);
        if (mapShareFirstCorner == null) {
            mapShareFirstCorner = point;
            return;
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
    }

    private void drawMapShareSelection(GuiGraphicsExtractor g) {
        if (!mapShareSelectionActive && mapShareBounds == null) {
            return;
        }

        MapShareBounds bounds = mapShareBounds;
        if (bounds != null) {
            double minX = bounds.minBlockX();
            double maxX = bounds.maxBlockXExclusive();
            double minZ = bounds.minBlockZ();
            double maxZ = bounds.maxBlockZExclusive();
            int color = 0xFF55E7FF;
            draw3DLine(g, minX, viewCenterY, minZ, maxX, viewCenterY, minZ, color);
            draw3DLine(g, maxX, viewCenterY, minZ, maxX, viewCenterY, maxZ, color);
            draw3DLine(g, maxX, viewCenterY, maxZ, minX, viewCenterY, maxZ, color);
            draw3DLine(g, minX, viewCenterY, maxZ, minX, viewCenterY, minZ, color);
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

    private void draw3DLine(GuiGraphicsExtractor g, double x1, double y1, double z1,
                            double x2, double y2, double z2, int color) {
        double cameraX = VoxyMapCameraController.cameraX();
        double cameraY = VoxyMapCameraController.cameraY();
        double cameraZ = VoxyMapCameraController.cameraZ();

        double d1 = (x1 - cameraX) * camForwardX + (y1 - cameraY) * camForwardY + (z1 - cameraZ) * camForwardZ;
        double d2 = (x2 - cameraX) * camForwardX + (y2 - cameraY) * camForwardY + (z2 - cameraZ) * camForwardZ;

        double near = 0.15;
        if (d1 < near && d2 < near) {
            return;
        }

        if (d1 < near) {
            double t = (near - d1) / (d2 - d1);
            x1 = x1 + t * (x2 - x1);
            y1 = y1 + t * (y2 - y1);
            z1 = z1 + t * (z2 - z1);
        } else if (d2 < near) {
            double t = (near - d1) / (d2 - d1);
            x2 = x1 + t * (x2 - x1);
            y2 = y1 + t * (y2 - y1);
            z2 = z1 + t * (z2 - z1);
        }

        ScreenPoint p1 = projectToScreen(x1, y1, z1);
        ScreenPoint p2 = projectToScreen(x2, y2, z2);
        if (p1 != null && p2 != null) {
            drawSegment(g, p1.x, p1.y, p2.x, p2.y, color);
        }
    }

    private void drawSegment(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        double[] clipped = clipLineToViewport(x1, y1, x2, y2);
        if (clipped == null) {
            return;
        }
        double dx = clipped[2] - clipped[0];
        double dy = clipped[3] - clipped[1];
        double length = Math.hypot(dx, dy);
        if (length < 1.0) {
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate((float) clipped[0], (float) clipped[1]);
        g.pose().rotate((float) Math.atan2(dy, dx));
        g.fill(0, 0, (int) Math.ceil(length) + 1, 1, color);
        g.pose().popMatrix();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (objectPanel.scroll(mouseX, mouseY, scrollY)) {
            refreshObjectPanel();
            return true;
        }
        if (isInsideSettingsPanel(mouseX, mouseY) && settingsMaxScroll > 0 && scrollY != 0.0) {
            settingsScroll = Mth.clamp(settingsScroll - (int) Math.signum(scrollY) * 24, 0, settingsMaxScroll);
            return true;
        }
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
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                return handleMapShareSelectionClick(event.x(), event.y());
            }
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
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

            confirmTextInput();
            return true;
        }
        MapObjectPanel.Action panelAction = objectPanel.click(event.x(), event.y(), event.button());
        if (panelAction != null) {
            handleObjectPanelAction(panelAction);
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && handleSettingsClick(event.x(), event.y())) {
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && isInsideXaeroButton(event.x(), event.y())) {
            startXaeroTransition();
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && isInsideSettingsPanel(event.x(), event.y())) {
            if (settingsEntryAt(event.x(), event.y()) == SettingsCatalog.Entry.MAP_RECEIVE) {
                if (MapShareClient.receiveStatus() == MapShareClient.ReceiveStatus.AWAITING_CONFIRMATION) {
                    MapShareClient.declineReceive();
                } else if (MapShareClient.receiveStatus() == MapShareClient.ReceiveStatus.RECEIVING
                        || MapShareClient.receiveStatus() == MapShareClient.ReceiveStatus.CONNECTING) {
                    MapShareClient.stopReceiving();
                }
            }
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT
                && !isInsideSettingsButton(event.x(), event.y()) && !isInsideSettingsPanel(event.x(), event.y())
                && !isInsideXaeroButton(event.x(), event.y())) {

            rightDragging = true;
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            WaypointManager.Waypoint hit = hitTestWaypoint(event.x(), event.y());
            if (hit != null) {
                if (hit.id().equals(selectedWaypointId) && !blockedFromEditing(currentVoxyWorldId(), hit)) {
                    beginRenameWaypoint(hit);
                } else {
                    selectedWaypointId = hit.id();
                    selectedObjectId = waypointObjectId(hit);
                }
                lastLeftPressNanos = 0L;
                return true;
            }

            String schematicHit = hitTestSchematic(event.x(), event.y());
            if (schematicHit != null) {
                String objectId = SCHEMATIC_PREFIX + schematicHit;
                if (objectId.equals(selectedObjectId)) {
                    closeMapView();
                    LitematicaPlusBridge.openEditor(schematicHit);
                } else {
                    selectedObjectId = objectId;
                    selectedWaypointId = null;
                    LitematicaPlusBridge.select(schematicHit);
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
                lastLeftPressNanos = 0L;
                if (currentVoxyWorldId() != null) {
                    pickSurfaceAt(event.x(), event.y(), surface -> {
                        if (surface != null) {
                            beginAddWaypoint(surface.x() + 0.5, surface.y() + 1.0, surface.z() + 0.5);
                        } else {
                            pickFailureUntilNanos = System.nanoTime() + 3_000_000_000L;
                        }
                    });
                }
                return true;
            }
            lastLeftPressNanos = now;
            selectedWaypointId = null;
            selectedObjectId = null;

            dragging = true;
            moveVelocityX = 0.0;
            moveVelocityZ = 0.0;
            return true;
        }
        return super.mouseClicked(event, isInside);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            dragging = false;
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            rightDragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (dragging) {
            stopFollowing();

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
            viewYaw += deltaX * 0.01;
            viewPitch = Mth.clamp(viewPitch + deltaY * 0.006, 0.0, 1.285);
            syncWorldCamera();
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (mapShareSelectionActive) {
            if (keyCode == InputConstants.KEY_ESCAPE || keyCode == InputConstants.KEY_M) {
                cancelMapShareSelection();
                settingsOpen = true;
            }
            return true;
        }
        if (waypointDraftActive || textInputKind != null) {
            if (keyCode == InputConstants.KEY_ESCAPE) {
                if (waypointDraftActive) cancelWaypointDraft(); else cancelTextInput();
            } else if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
                if (waypointDraftActive) confirmWaypointDraft(); else confirmTextInput();
            } else if (keyCode == InputConstants.KEY_BACKSPACE && nameInputBuffer.length() > 0) {
                nameInputBuffer.deleteCharAt(nameInputBuffer.length() - 1);
            } else if (event.isPaste()) {
                pasteFromClipboard();
            } else if (textInputKind == TextInputKind.SYNC_PASSWORD && (event.isCopy() || event.isCut())) {
                return true;
            } else if (event.isCopy()) {
                minecraft.keyboardHandler.setClipboard(nameInputBuffer.toString());
            } else if (event.isCut()) {
                minecraft.keyboardHandler.setClipboard(nameInputBuffer.toString());
                nameInputBuffer.setLength(0);
            }

            return true;
        }
        if (objectPanel.keyPressed(keyCode)) {
            refreshObjectPanel();
            return true;
        }
        if (keyCode == InputConstants.KEY_ESCAPE || keyCode == InputConstants.KEY_M) {
            onClose();
            return true;
        }
        if (keyCode == InputConstants.KEY_O) {
            objectPanel.toggle();
            return true;
        }
        if (keyCode == InputConstants.KEY_F && selectedObjectId != null && selectedObjectId.startsWith("player:")) {
            followingPlayerId = selectedObjectId.equals(followingPlayerId) ? null : selectedObjectId;
            return true;
        }
        if (keyCode == InputConstants.KEY_DELETE) {
            deleteSelectedWaypoint();
            return true;
        }
        if (keyCode == InputConstants.KEY_Z && minecraft != null && minecraft.getWindow() != null) {
            if (keyDown( InputConstants.KEY_LCONTROL) || keyDown(InputConstants.KEY_RCONTROL)) {
                undoWaypointDelete();
                return true;
            }
        }
        if ((keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)) {
            WaypointManager.Waypoint selected = selectedWaypoint();
            if (selected != null && !blockedFromEditing(currentVoxyWorldId(), selected)) {
                beginRenameWaypoint(selected);
                return true;
            }
        }
        if (keyCode == InputConstants.KEY_W || keyCode == InputConstants.KEY_S ||
                keyCode == InputConstants.KEY_A || keyCode == InputConstants.KEY_D ||
                keyCode == InputConstants.KEY_UP || keyCode == InputConstants.KEY_DOWN ||
                keyCode == InputConstants.KEY_LEFT || keyCode == InputConstants.KEY_RIGHT) {
            return true;
        }
        if (keyCode == InputConstants.KEY_C && minecraft.player != null) {
            stopFollowing();
            centerOnPlayer();
            syncWorldCamera();
            return true;
        }
        if (keyCode == InputConstants.KEY_EQUALS || keyCode == InputConstants.KEY_ADD) {
            zoomAt(lastZoomMouseX(), lastZoomMouseY(), 0.8);
            return true;
        }
        if (keyCode == InputConstants.KEY_MINUS || keyCode == SDLScancode.SDL_SCANCODE_KP_MINUS) {
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
        if (objectPanel.charTyped(event.codepoint())) {
            refreshObjectPanel();
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
        boolean digitsOnly = textInputKind == TextInputKind.RECEIVE_CODE;
        for (int i = 0; i < clipboard.length() && nameInputBuffer.length() < maxLength; i++) {
            char c = clipboard.charAt(i);
            if (digitsOnly) {
                if (Character.isDigit(c)) {
                    nameInputBuffer.append(c);
                }
                continue;
            }
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
        surfacePickRequest++;
        surfacePickPending = false;
        hoverCursorPickRequest++;
        hoverCursorPickPending = false;
        hoverCursorHit = null;
        closeMapView();
        super.onClose();
    }

    @Override
    public void removed() {
        surfacePickRequest++;
        surfacePickPending = false;
        hoverCursorPickRequest++;
        hoverCursorPickPending = false;
        hoverCursorHit = null;
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
