package com.voxymap.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.voxymap.client.VoxyMapClient;
import com.voxymap.client.compat.MinecraftCompat;
import com.voxymap.client.gui.ui.UiButton;
import com.voxymap.client.gui.ui.UiCycler;
import com.voxymap.client.gui.ui.UiDraw;
import com.voxymap.client.gui.ui.UiIcons;
import com.voxymap.client.gui.ui.UiMotion;
import com.voxymap.client.gui.ui.UiTheme;
import com.voxymap.client.integration.LitematicaPlusBridge;
import com.voxymap.client.integration.VanillaSquadWebMap;
import com.voxymap.client.integration.VanillaSquadFlagMapBridge;
import com.voxymap.client.integration.RemotePlayerMarkers;
import com.voxymap.client.integration.SpatialGuiCompat;
import com.voxymap.client.integration.VanillaSquadServer;
import com.voxymap.client.integration.XaeroWorldMapBridge;
import com.voxymap.client.map.Map2dGeometry;
import com.voxymap.client.map.Map2dRenderer;
import com.voxymap.client.map.Map2dTiles;
import com.voxymap.client.map.MapEntityScale;
import com.voxymap.client.map.MapRenderSettingsGuard;
import com.voxymap.client.map.MapViewMode;
import com.voxymap.client.map.MapWeatherOverlay;
import com.voxymap.client.map.MapOverlay;
import com.voxymap.client.map.MinimapOverlays;
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
import com.voxymap.client.map.ServerRules;
import com.voxymap.client.map.TemporaryWaypoints;
import com.voxymap.client.map.WaypointDisplay;
import com.voxymap.client.map.WaypointManager;
import com.voxymap.client.map.WaypointSyncSettings;
import com.voxymap.client.map.WaypointWorlds;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.sdl.SDLScancode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class MapScreen extends Screen implements SettingsModel.Host {

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
    private static final double CLICK_MOVE_TOLERANCE = 4.0;
    private static final long DOUBLE_CLICK_NANOS = 350_000_000L;
    private static final double DOUBLE_CLICK_DISTANCE = 6.0;
    private double viewCenterX;
    private double viewCenterY;
    private double viewCenterZ;
    private double blocksPerPixel = 4.0;
    private boolean flat = false;
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
    private final SettingsView settingsView;
    private final List<AbstractWidget> toolbar = new ArrayList<>();
    private UiButton view3dButton;
    private UiButton view2dButton;
    private UiButton settingsButton;
    private UiButton worldsButton;
    private UiButton xaeroButton;
    private UiButton helpButton;
    private UiCycler timeCycler;
    private UiCycler weatherCycler;
    private CompassButton compass;
    private boolean helpOpen;
    private Double yawTarget;
    private int toolbarBottom;
    private int footerTop;
    private long lastFrameNanos = 0L;
    private long openedAtNanos = 0L;
    private boolean openLogged = false;
    private boolean firstInitDone = false;
    private boolean xaeroTransitionActive = false;
    private boolean xaeroOpenScheduled = false;
    private long xaeroTransitionStartedAt = 0L;
    private boolean settingsOpen = false;
    private final MapObjectPanel objectPanel = new MapObjectPanel();
    private long lastObjectPanelRefreshNanos;
    private String selectedObjectId;
    private String followingPlayerId;
    private List<ProjectedWaypoint> lastProjectedWaypoints = List.of();
    private List<ProjectedWaypoint> lastDrawnLabels = List.of();
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
    private WaypointForm waypointForm;
    private final MapContextMenu contextMenu = new MapContextMenu();
    private boolean pinPressPending;
    private double pinPressX;
    private double pinPressY;
    private double rightPressX;
    private double rightPressY;
    private boolean rightPressMoved;
    private String transientMessage;
    private long transientMessageUntilNanos;
    private boolean mapShareSelectionActive = false;
    private GroundPoint mapShareFirstCorner = null;
    private MapShareBounds mapShareBounds = null;
    private String pendingMapShareAddress = null;
    private final StringBuilder nameInputBuffer = new StringBuilder();
    private static final int HUD_MARGIN = 12;
    private static final int HUD_GAP = 6;
    private static final int HUD_BUTTON_HEIGHT = 22;
    private static final int TOOLBAR_WIDTH = 156;
    private static final int COMPASS_SIZE = 36;
    private static final int FOOTER_HEIGHT = 18;
    private static final int SETTINGS_MAX_WIDTH = 440;
    private static final double GRID_MAX_BLOCKS_PER_PIXEL = 2.0;
    private static final double SLIME_MAX_BLOCKS_PER_PIXEL = 8.0;
    private static final int MAX_GRID_LINES = 160;
    private static final long MAX_SLIME_CHUNKS = 6400L;
    private static final int CHUNK_GRID_COLOR = 0x44FFFFFF;
    private static final int SLIME_OUTLINE_COLOR = 0xDD55FF55;
    private static final int SLIME_INNER_COLOR = 0x8855FF55;

    public MapScreen() {
        super(TITLE);
        settingsView = new SettingsView(this, new SettingsView.Children() {
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
        }, () -> SettingsCatalog.sections(true, true, VanillaSquadServer.isConnected(), RemotePlayerMarkers.available(),
                LitematicaPlusBridge.isAvailable(), SpatialGuiCompat.isAvailable()));
    }

    @Override
    public String worldId() {
        return currentWaypointWorldId();
    }

    @Override
    public void openMinimapLayout() {
        minecraft.setScreenAndShow(new MinimapLayoutScreen(null));
    }

    @Override
    public Component actionValue(SettingsCatalog.Entry entry) {
        String worldId = currentWaypointWorldId();
        String address = worldId != null ? WaypointSyncSettings.address(worldId) : null;
        String notSet = Component.translatable("screen.voxymap.settings.sync.not_set").getString();
        return Component.literal(entry == SettingsCatalog.Entry.MAP_SHARE ? mapShareValue(address, notSet)
                : mapReceiveValue(address, notSet));
    }

    @Override
    public void runAction(SettingsCatalog.Entry entry) {
        String worldId = currentWaypointWorldId();
        String address = worldId != null ? WaypointSyncSettings.address(worldId) : null;
        if (entry == SettingsCatalog.Entry.MAP_SHARE) {
            if (address == null) {
                return;
            }
            if (MapShareClient.isSharing()) {
                MapShareClient.stopSharing();
                mapShareBounds = null;
            } else {
                closeSettings();
                beginMapShareSelection(address);
            }
            return;
        }
        if (MapShareClient.receiveStatus() == MapShareClient.ReceiveStatus.AWAITING_CONFIRMATION) {
            MapShareClient.confirmReceive(minecraft);
        } else if (MapShareClient.receiveStatus() == MapShareClient.ReceiveStatus.RECEIVING
                || MapShareClient.receiveStatus() == MapShareClient.ReceiveStatus.CONNECTING) {
            MapShareClient.stopReceiving();
        } else if (address != null) {
            closeSettings();
            helpOpen = false;
            textInputKind = TextInputKind.RECEIVE_CODE;
            nameInputBuffer.setLength(0);
        }
    }

    private void openSettings() {
        settingsOpen = true;
        helpOpen = false;
    }

    private void closeSettings() {
        if (!settingsOpen) {
            return;
        }
        settingsOpen = false;
        settingsView.close();
        setFocused(null);
    }

    private void rebuildToolbar() {
        for (AbstractWidget widget : toolbar) {
            removeWidget(widget);
        }
        toolbar.clear();
        int half = (TOOLBAR_WIDTH - UiTheme.SPACE_S) / 2;
        view3dButton = toolbarWidget(new UiButton(0, 0, half, HUD_BUTTON_HEIGHT, Component.literal("3D"),
                UiButton.Variant.SECONDARY, button -> setView(false)));
        view3dButton.setTooltip(Tooltip.create(Component.translatable("overlay.voxymap.key.view.3d")));
        view2dButton = toolbarWidget(new UiButton(0, 0, half, HUD_BUTTON_HEIGHT, Component.literal("2D"),
                UiButton.Variant.SECONDARY, button -> setView(true)));
        view2dButton.setTooltip(Tooltip.create(Component.translatable("overlay.voxymap.key.view.2d")));
        settingsButton = toolbarWidget(UiButton.iconButton(0, 0, HUD_BUTTON_HEIGHT, UiIcons.Icon.SETTINGS,
                Component.translatable("overlay.voxymap.settings"), button -> {
                    if (settingsOpen) {
                        closeSettings();
                    } else {
                        openSettings();
                    }
                }));
        settingsButton.setTooltip(Tooltip.create(Component.translatable("overlay.voxymap.settings")));
        worldsButton = toolbarWidget(UiButton.iconButton(0, 0, HUD_BUTTON_HEIGHT, UiIcons.Icon.WORLD,
                Component.translatable("overlay.voxymap.worlds"), button -> openWebMap()));
        worldsButton.setTooltip(Tooltip.create(Component.translatable("overlay.voxymap.worlds")));
        xaeroButton = toolbarWidget(UiButton.iconButton(0, 0, HUD_BUTTON_HEIGHT, UiIcons.Icon.MINIMAP,
                Component.translatable("overlay.voxymap.xaero"), button -> startXaeroTransition()));
        xaeroButton.setTooltip(Tooltip.create(Component.translatable("overlay.voxymap.xaero")));
        helpButton = toolbarWidget(UiButton.iconButton(0, 0, HUD_BUTTON_HEIGHT, UiIcons.Icon.KEYBOARD,
                Component.translatable("overlay.voxymap.help"), button -> toggleHelp()));
        helpButton.setTooltip(Tooltip.create(Component.translatable("overlay.voxymap.help")));
        timeCycler = toolbarWidget(new UiCycler(0, 0, TOOLBAR_WIDTH, HUD_BUTTON_HEIGHT,
                Component.translatable("screen.voxymap.settings.timeofday"),
                () -> Component.translatable(VoxyMapSettings.timeOfDayMode().translationKey()),
                VoxyMapSettings::cycleTimeOfDayMode).icon(UiIcons.Icon.CLOCK));
        timeCycler.setTooltip(Tooltip.create(Component.translatable("screen.voxymap.settings.timeofday")));
        weatherCycler = toolbarWidget(new UiCycler(0, 0, TOOLBAR_WIDTH, HUD_BUTTON_HEIGHT,
                Component.translatable("screen.voxymap.settings.weather"),
                () -> Component.translatable(VoxyMapSettings.mapWeather().translationKey()),
                VoxyMapSettings::cycleMapWeather).icon(UiIcons.Icon.CLOUD));
        weatherCycler.setTooltip(Tooltip.create(Component.translatable("screen.voxymap.settings.weather")));
        compass = toolbarWidget(new CompassButton(COMPASS_SIZE, this::northScreenAngle, this::alignNorth));
        compass.setTooltip(Tooltip.create(Component.translatable("overlay.voxymap.compass")));
    }

    private <T extends AbstractWidget> T toolbarWidget(T widget) {
        toolbar.add(widget);
        addWidget(widget);
        return widget;
    }

    private void layoutToolbar(int slide) {
        int x = width - TOOLBAR_WIDTH - HUD_MARGIN;
        int y = HUD_MARGIN - slide;
        int half = (TOOLBAR_WIDTH - UiTheme.SPACE_S) / 2;
        view3dButton.setX(x);
        view3dButton.setY(y);
        view2dButton.setX(x + TOOLBAR_WIDTH - half);
        view2dButton.setY(y);
        view3dButton.selected(!flat);
        view2dButton.selected(flat);
        y += HUD_BUTTON_HEIGHT + UiTheme.SPACE_S;
        worldsButton.visible = VanillaSquadWebMap.available();
        xaeroButton.visible = XaeroWorldMapBridge.isAvailable();
        int iconX = x;
        for (UiButton button : List.of(settingsButton, worldsButton, xaeroButton, helpButton)) {
            if (!button.visible) {
                continue;
            }
            button.setX(iconX);
            button.setY(y);
            iconX += HUD_BUTTON_HEIGHT + UiTheme.SPACE_S;
        }
        settingsButton.selected(settingsOpen);
        helpButton.selected(helpOpen);
        y += HUD_BUTTON_HEIGHT + UiTheme.SPACE_S;
        toolbarBottom = y;
        timeCycler.visible = !settingsOpen;
        weatherCycler.visible = !settingsOpen;
        timeCycler.setX(x);
        timeCycler.setY(y);
        weatherCycler.setX(x);
        weatherCycler.setY(y + HUD_BUTTON_HEIGHT + UiTheme.SPACE_S);
        compass.visible = !settingsOpen;
        compass.setX(width - HUD_MARGIN - COMPASS_SIZE);
        compass.setY(footerTop - UiTheme.SPACE_M - COMPASS_SIZE);
    }

    private void drawToolbar(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        for (AbstractWidget widget : toolbar) {
            if (widget.visible) {
                widget.extractRenderState(g, mouseX, mouseY, delta);
            }
        }
    }

    private AbstractWidget toolbarWidgetAt(double mouseX, double mouseY) {
        for (AbstractWidget widget : toolbar) {
            if (widget.visible && widget.isMouseOver(mouseX, mouseY)) {
                return widget;
            }
        }
        return null;
    }

    private double northScreenAngle() {
        if (flat) {
            return 0.0;
        }
        ScreenPoint center = projectToScreen(viewCenterX, viewCenterY, viewCenterZ);
        ScreenPoint north = projectToScreen(viewCenterX, viewCenterY, viewCenterZ - 64.0);
        if (center == null || north == null || (center.x == north.x && center.y == north.y)) {
            return 0.0;
        }
        return Math.atan2(north.x - center.x, -(north.y - center.y));
    }

    private void alignNorth() {
        if (!flat) {
            yawTarget = Math.PI;
        }
    }

    private void toggleHelp() {
        helpOpen = !helpOpen;
        if (helpOpen) {
            closeSettings();
        }
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
            setFlat(VoxyMapSettings.map2d());
            VoxyMapGuiRenderer.open(minecraft);
        }
        capturePlayerMarkers();
        cachedKeyHints = buildKeyHints();
        rebuildToolbar();
        if (settingsOpen) {
            settingsView.close();
        }
        if (waypointForm != null) {
            installWaypointForm();
        }
        VoxyBridge.suppressEnvironmentalFogForMap();
        MapRenderSettingsGuard.applyForMap(minecraft);
        syncWorldCamera();

        if (!openLogged) {
            VoxyMapClient.LOGGER.debug("Map opened");
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
            if (flat) {
                drawFlatTerrain(g);
            }
            drawFlagMarkers(g);
            drawWaypoints(g, -1, -1);
            drawPlayerMarkers(g, -1, -1);
            return;
        }
        if (flat) {
            drawFlatTerrain(g);
        }
        MapWeatherOverlay.extract(g, minecraft, width, height, viewCenterX, viewCenterY, viewCenterZ);
        drawWorldViewerOverlay(g, mouseX, mouseY, delta);
        drawOpeningAnimation(g);
        drawTextInputOverlay(g);
        drawWaypointForm(g, mouseX, mouseY, delta);
        drawTransientMapMessage(g);
        drawHelpOverlay(g);
        contextMenu.render(g, minecraft.font, mouseX, mouseY);
        syncTextInput();
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

        if (yawTarget != null) {
            double difference = Math.atan2(Math.sin(yawTarget - viewYaw), Math.cos(yawTarget - viewYaw));
            if (Math.abs(difference) < 0.002 || !UiMotion.enabled()) {
                viewYaw = yawTarget;
                yawTarget = null;
            } else {
                viewYaw += difference * (1.0 - Math.exp(-dt * 9.0));
            }
        }

        if (textInputKind != null || (waypointForm != null && waypointForm.typing(getFocused()))
                || (settingsOpen && settingsView.typing(getFocused()))
                || objectPanel.searchFocused() || contextMenu.isOpen()) {

            moveVelocityX = 0.0;
            moveVelocityZ = 0.0;
            return;
        }

        boolean arrows = getFocused() == null;
        double forwardInput = keyDown(InputConstants.KEY_W) || arrows && keyDown(InputConstants.KEY_UP) ? 1.0 : 0.0;
        forwardInput -= keyDown(InputConstants.KEY_S) || arrows && keyDown(InputConstants.KEY_DOWN) ? 1.0 : 0.0;
        double strafeInput = keyDown(InputConstants.KEY_A) || arrows && keyDown(InputConstants.KEY_LEFT) ? 1.0 : 0.0;
        strafeInput -= keyDown(InputConstants.KEY_D) || arrows && keyDown(InputConstants.KEY_RIGHT) ? 1.0 : 0.0;

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
        double moveYaw = flat ? Math.PI : viewYaw;
        double forwardX = -Math.sin(moveYaw);
        double forwardZ = Math.cos(moveYaw);
        double rightX = Math.cos(moveYaw);
        double rightZ = Math.sin(moveYaw);
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
        if (flat) {
            return new GroundPoint(Map2dGeometry.worldX(mouseX, viewCenterX, blocksPerPixel, width),
                    Map2dGeometry.worldZ(mouseY, viewCenterZ, blocksPerPixel, height));
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
        if (flat) {
            GroundPoint ground = mapPointAt(mouseX, mouseY);
            int slice = flatSlice();
            double top = slice == Map2dGeometry.SURFACE
                    ? (minecraft.level != null ? minecraft.level.getMinY() + minecraft.level.getHeight() : 320.0)
                    : slice + 0.5;
            return ground == null ? null : new VoxyMapPicker.Ray(ground.x(), top, ground.z(), 0.0, -1.0, 0.0);
        }
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
                VoxyMapClient.LOGGER.warn("Could not pick the map surface under the cursor", throwable);
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

    private static String formatEstimatedSize(long bytes) {
        double megabytes = bytes / (1024.0 * 1024.0);
        return String.format(java.util.Locale.ROOT, megabytes < 10 ? "%.1f" : "%.0f", megabytes);
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

    private void drawWorldViewerOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        drawMapGrids(g);
        drawFlagMarkers(g);
        drawSchematicPlacements(g, mouseX, mouseY);
        drawWaypoints(g, mouseX, mouseY);
        drawPlayerMarkers(g, mouseX, mouseY);
        drawMapShareSelection(g);

        int slide = (int) ((1.0 - openingProgress()) * 28.0);
        drawInfoCard(g, mouseX, mouseY, slide);
        footerTop = height - HUD_MARGIN - FOOTER_HEIGHT + slide;
        drawFooter(g, slide);
        layoutToolbar(slide);
        drawToolbar(g, mouseX, mouseY, delta);
        drawScaleBar(g);
        if (settingsOpen) {
            int panelWidth = Math.min(SETTINGS_MAX_WIDTH, width - HUD_MARGIN * 2);
            int panelTop = toolbarBottom;
            settingsView.layout(width - HUD_MARGIN - panelWidth, panelTop, panelWidth,
                    Math.max(120, footerTop - UiTheme.SPACE_M - panelTop));
            settingsView.extract(g, mouseX, mouseY, delta, getFocused());
        }
        if (objectPanel.isOpen() && System.nanoTime() - lastObjectPanelRefreshNanos > 500_000_000L) {
            refreshObjectPanel();
        }
        objectPanel.render(g, minecraft.font, width, height, mouseX, mouseY);
    }

    private void drawInfoCard(GuiGraphicsExtractor g, int mouseX, int mouseY, int slide) {
        int x = HUD_MARGIN;
        int y = HUD_MARGIN - slide;
        String mode = flat ? "2D" : "3D";
        List<String[]> lines = new ArrayList<>();
        if (minecraft.player != null) {
            lines.add(new String[]{Component.translatable("overlay.voxymap.player").getString(),
                    Mth.floor(minecraft.player.getX()) + " / " + Mth.floor(minecraft.player.getY()) + " / "
                            + Mth.floor(minecraft.player.getZ())});
        }
        String camera = Mth.floor(viewCenterX) + " / " + Mth.floor(viewCenterY) + " / " + Mth.floor(viewCenterZ);
        if (!flat && NetherRoofController.isActive()) {
            camera += "  " + Component.translatable("overlay.voxymap.slice").getString();
        }
        lines.add(new String[]{Component.translatable("overlay.voxymap.camera").getString(), camera});
        updateHoverCursorHit(mouseX, mouseY);
        VoxyMapPicker.Hit cursorHit = hoverCursorHit;
        lines.add(new String[]{Component.translatable("overlay.voxymap.cursor").getString(),
                cursorHit != null ? cursorHit.blockX() + " / " + cursorHit.blockY() + " / " + cursorHit.blockZ() : "—"});
        int labelWidth = 0;
        int valueWidth = minecraft.font.width("VoxyMap") + 30;
        for (String[] line : lines) {
            labelWidth = Math.max(labelWidth, minecraft.font.width(line[0]));
            valueWidth = Math.max(valueWidth, minecraft.font.width(line[1]));
        }
        int cardWidth = Math.min(width - HUD_MARGIN * 2, labelWidth + valueWidth + UiTheme.SPACE_XL + UiTheme.SPACE_L);
        int cardHeight = 20 + lines.size() * 11 + UiTheme.SPACE_S;
        UiDraw.card(g, x, y, cardWidth, cardHeight);
        g.text(minecraft.font, "VoxyMap", x + UiTheme.SPACE_M, y + 6, UiTheme.TEXT);
        int chipX = x + UiTheme.SPACE_M + minecraft.font.width("VoxyMap") + UiTheme.SPACE_S + 2;
        UiDraw.panel(g, chipX, y + 4, minecraft.font.width(mode) + 8, 12, UiTheme.CONTROL_SELECTED);
        g.text(minecraft.font, mode, chipX + 4, y + 6, UiTheme.ACCENT_BRIGHT);
        String zoom = String.format(java.util.Locale.ROOT, "%.2f", blocksPerPixel);
        g.text(minecraft.font, zoom, x + cardWidth - UiTheme.SPACE_M - minecraft.font.width(zoom), y + 6, UiTheme.TEXT_MUTED);
        int lineY = y + 20;
        for (String[] line : lines) {
            g.text(minecraft.font, line[0], x + UiTheme.SPACE_M, lineY, UiTheme.TEXT_MUTED);
            UiDraw.text(g, minecraft.font, line[1], x + UiTheme.SPACE_M + labelWidth + UiTheme.SPACE_M, lineY,
                    cardWidth - labelWidth - UiTheme.SPACE_XL - UiTheme.SPACE_S, UiTheme.TEXT);
            lineY += 11;
        }
    }

    private String[][] buildKeyHints() {
        return new String[][] {
                {Component.translatable("overlay.voxymap.key.drag.button").getString(), Component.translatable("overlay.voxymap.key.drag").getString()},
                {Component.translatable("overlay.voxymap.key.menu.button").getString(), Component.translatable("overlay.voxymap.key.menu").getString()},
                {Component.translatable("overlay.voxymap.key.view.button").getString(), Component.translatable(flat ? "overlay.voxymap.key.view.3d" : "overlay.voxymap.key.view.2d").getString()},
                {Component.translatable("overlay.voxymap.key.waypoint.button", VoxyMapClient.addWaypointKeyName()).getString(), Component.translatable("overlay.voxymap.key.waypoint").getString()},
                {Component.translatable("overlay.voxymap.key.rotate.button").getString(), Component.translatable("overlay.voxymap.key.rotate").getString()},
                {Component.translatable("overlay.voxymap.key.move.button").getString(), Component.translatable("overlay.voxymap.key.move").getString()},
                {Component.translatable("overlay.voxymap.key.fast.button").getString(), Component.translatable("overlay.voxymap.key.fast").getString()},
                {Component.translatable("overlay.voxymap.key.zoom.button").getString(), Component.translatable("overlay.voxymap.key.zoom").getString()},
                {Component.translatable("overlay.voxymap.key.center.button").getString(), Component.translatable("overlay.voxymap.key.center").getString()},
                {Component.translatable("overlay.voxymap.key.goto.button").getString(), Component.translatable("overlay.voxymap.key.goto").getString()},
                {Component.translatable("overlay.voxymap.key.objects.button").getString(), Component.translatable("overlay.voxymap.key.objects").getString()},
                {Component.translatable("overlay.voxymap.key.follow.button").getString(), Component.translatable("overlay.voxymap.key.follow").getString()},
                {Component.translatable("overlay.voxymap.key.delete.button").getString(), Component.translatable("overlay.voxymap.key.delete").getString()},
                {Component.translatable("overlay.voxymap.key.help.button").getString(), Component.translatable("overlay.voxymap.key.help").getString()},
                {Component.translatable("overlay.voxymap.key.close.button").getString(), Component.translatable("overlay.voxymap.key.close").getString()}
        };
    }

    private static final int FOOTER_HINTS = 4;

    private void drawFooter(GuiGraphicsExtractor g, int slide) {
        int x = HUD_MARGIN;
        int y = footerTop + 2;
        int limit = width - HUD_MARGIN - COMPASS_SIZE - UiTheme.SPACE_L;
        String[] help = cachedKeyHints[cachedKeyHints.length - 2];
        int helpWidth = UiDraw.keyCapWidth(minecraft.font, help[0], help[1]);
        for (int i = 0; i < Math.min(FOOTER_HINTS, cachedKeyHints.length); i++) {
            String[] hint = cachedKeyHints[i];
            int hintWidth = UiDraw.keyCapWidth(minecraft.font, hint[0], hint[1]);
            if (x + hintWidth + UiTheme.SPACE_L + helpWidth > limit) {
                break;
            }
            UiDraw.keyCap(g, minecraft.font, hint[0], hint[1], x, y);
            x += hintWidth + UiTheme.SPACE_L;
        }
        UiDraw.keyCap(g, minecraft.font, help[0], help[1], x, y);
    }

    private void drawHelpOverlay(GuiGraphicsExtractor g) {
        if (!helpOpen) {
            return;
        }
        g.fill(0, 0, width, height, UiTheme.SCRIM);
        int rows = (cachedKeyHints.length + 1) / 2;
        int columnWidth = 0;
        for (String[] hint : cachedKeyHints) {
            columnWidth = Math.max(columnWidth, UiDraw.keyCapWidth(minecraft.font, hint[0], hint[1]));
        }
        boolean twoColumns = columnWidth * 2 + UiTheme.SPACE_XL * 3 <= width - HUD_MARGIN * 2;
        int columns = twoColumns ? 2 : 1;
        int rowCount = twoColumns ? rows : cachedKeyHints.length;
        int cardWidth = columns * columnWidth + (columns + 1) * UiTheme.SPACE_XL;
        int cardHeight = 34 + rowCount * 18 + UiTheme.SPACE_M;
        int x = (width - cardWidth) / 2;
        int y = Math.max(HUD_MARGIN, (height - cardHeight) / 2);
        UiDraw.panel(g, x, y, cardWidth, cardHeight, UiTheme.SURFACE_RAISED);
        UiDraw.outline(g, x, y, cardWidth, cardHeight, UiTheme.BORDER_STRONG);
        UiIcons.draw(g, UiIcons.Icon.KEYBOARD, x + UiTheme.SPACE_XL, y + 11, UiTheme.ACCENT_BRIGHT);
        g.text(minecraft.font, Component.translatable("overlay.voxymap.help"), x + UiTheme.SPACE_XL + 14, y + 12, UiTheme.TEXT);
        String close = Component.translatable("overlay.voxymap.help.close").getString();
        g.text(minecraft.font, close, x + cardWidth - UiTheme.SPACE_XL - minecraft.font.width(close), y + 12,
                UiTheme.TEXT_MUTED);
        UiDraw.divider(g, x + UiTheme.SPACE_M, y + 26, cardWidth - UiTheme.SPACE_M * 2);
        for (int i = 0; i < cachedKeyHints.length; i++) {
            int column = twoColumns ? i / rows : 0;
            int row = twoColumns ? i % rows : i;
            String[] hint = cachedKeyHints[i];
            UiDraw.keyCap(g, minecraft.font, hint[0], hint[1],
                    x + UiTheme.SPACE_XL + column * (columnWidth + UiTheme.SPACE_XL), y + 34 + row * 18);
        }
    }

    private void drawScaleBar(GuiGraphicsExtractor g) {
        if (settingsOpen) {
            return;
        }
        double target = 70.0 * blocksPerPixel;
        double magnitude = Math.pow(10.0, Math.floor(Math.log10(Math.max(1.0, target))));
        double blocks = magnitude;
        for (double factor : new double[]{1.0, 2.0, 5.0, 10.0}) {
            if (magnitude * factor <= target) {
                blocks = magnitude * factor;
            }
        }
        int pixels = (int) Math.max(8, Math.round(blocks / blocksPerPixel));
        int right = width - HUD_MARGIN - COMPASS_SIZE - UiTheme.SPACE_M;
        int y = footerTop - UiTheme.SPACE_M - 8;
        int left = right - pixels;
        g.fill(left, y, right, y + 2, UiTheme.TEXT);
        g.fill(left, y - 3, left + 1, y + 2, UiTheme.TEXT);
        g.fill(right - 1, y - 3, right, y + 2, UiTheme.TEXT);
        String label = (flat ? "" : "≈ ") + (blocks >= 1000.0
                ? String.format(java.util.Locale.ROOT, "%.0f km", blocks / 1000.0)
                : String.format(java.util.Locale.ROOT, "%.0f m", blocks));
        g.text(minecraft.font, label, right - minecraft.font.width(label), y - 12, UiTheme.TEXT_SECONDARY);
    }

    private void drawMapGrids(GuiGraphicsExtractor g) {
        boolean grid = VoxyMapSettings.chunkGridEnabled() && blocksPerPixel <= GRID_MAX_BLOCKS_PER_PIXEL;
        Long seed = VoxyMapSettings.overlayShown(MapOverlay.SLIME_CHUNKS) && blocksPerPixel <= SLIME_MAX_BLOCKS_PER_PIXEL
                ? MinimapOverlays.slimeSeed(minecraft.level) : null;
        if (!grid && seed == null) {
            return;
        }
        double[] bounds = visibleGroundBounds();
        int minChunkX = Math.floorDiv(Mth.floor(bounds[0]), 16);
        int minChunkZ = Math.floorDiv(Mth.floor(bounds[1]), 16);
        int maxChunkX = Math.floorDiv(Mth.floor(bounds[2]), 16);
        int maxChunkZ = Math.floorDiv(Mth.floor(bounds[3]), 16);
        int spanX = maxChunkX - minChunkX + 1;
        int spanZ = maxChunkZ - minChunkZ + 1;
        double y = viewCenterY;
        if (grid && spanX <= MAX_GRID_LINES && spanZ <= MAX_GRID_LINES) {
            for (int cx = minChunkX; cx <= maxChunkX + 1; cx++) {
                draw3DLine(g, cx * 16.0, y, minChunkZ * 16.0, cx * 16.0, y, (maxChunkZ + 1) * 16.0, CHUNK_GRID_COLOR);
            }
            for (int cz = minChunkZ; cz <= maxChunkZ + 1; cz++) {
                draw3DLine(g, minChunkX * 16.0, y, cz * 16.0, (maxChunkX + 1) * 16.0, y, cz * 16.0, CHUNK_GRID_COLOR);
            }
        }
        if (seed != null && (long) spanX * spanZ <= MAX_SLIME_CHUNKS) {
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    if (MinimapOverlays.isSlimeChunk(seed, cx, cz)) {
                        drawChunkOutline(g, cx, cz, y, 0.0, SLIME_OUTLINE_COLOR);
                        drawChunkOutline(g, cx, cz, y, 1.0, SLIME_INNER_COLOR);
                    }
                }
            }
        }
    }

    private void drawChunkOutline(GuiGraphicsExtractor g, int chunkX, int chunkZ, double y, double inset, int color) {
        double x0 = chunkX * 16.0 + inset;
        double z0 = chunkZ * 16.0 + inset;
        double x1 = chunkX * 16.0 + 16.0 - inset;
        double z1 = chunkZ * 16.0 + 16.0 - inset;
        draw3DLine(g, x0, y, z0, x1, y, z0, color);
        draw3DLine(g, x1, y, z0, x1, y, z1, color);
        draw3DLine(g, x1, y, z1, x0, y, z1, color);
        draw3DLine(g, x0, y, z1, x0, y, z0, color);
    }

    private double[] visibleGroundBounds() {
        double reach = blocksPerPixel * Math.max(width, height);
        double minX = viewCenterX - 16.0;
        double minZ = viewCenterZ - 16.0;
        double maxX = viewCenterX + 16.0;
        double maxZ = viewCenterZ + 16.0;
        double[][] samples = {{0, 0}, {width, 0}, {0, height}, {width, height}, {width / 2.0, 0},
                {width / 2.0, height}, {0, height / 2.0}, {width, height / 2.0}};
        int hits = 0;
        for (double[] sample : samples) {
            GroundPoint point = mapPointAt(sample[0], sample[1]);
            if (point == null) {
                continue;
            }
            hits++;
            minX = Math.min(minX, point.x());
            minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x());
            maxZ = Math.max(maxZ, point.z());
        }
        if (hits < samples.length) {
            minX = Math.min(minX, viewCenterX - reach);
            minZ = Math.min(minZ, viewCenterZ - reach);
            maxX = Math.max(maxX, viewCenterX + reach);
            maxZ = Math.max(maxZ, viewCenterZ + reach);
        }
        double limit = reach * 2.0;
        return new double[]{
                Math.max(minX, viewCenterX - limit), Math.max(minZ, viewCenterZ - limit),
                Math.min(maxX, viewCenterX + limit), Math.min(maxZ, viewCenterZ + limit)};
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
                        marker.local ? 0xFF55D8FF : marker.remote ? 0xFFFFC94D : 0xFFFF55CC, !marker.hidden(), false,
                        !marker.local && marker.uuid != null, marker.hidden()));
            }
        }
        String worldId = currentWaypointWorldId();
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
        for (LitematicaPlusBridge.Placement placement : VoxyMapSettings.schematicsEnabled()
                ? LitematicaPlusBridge.placements() : List.<LitematicaPlusBridge.Placement>of()) {
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
            if (waypoint != null) beginEditWaypoint(waypoint);
        } else if (action.type() == MapObjectPanel.ActionType.DELETE) {
            selectObject(entry);
            deleteSelectedWaypoint();
        } else if (action.type() == MapObjectPanel.ActionType.HIDE) {
            PlayerMarker marker = playerByObjectId(entry.id());
            if (marker != null && marker.uuid != null && !marker.local) {
                VoxyMapSettings.togglePlayerHidden(marker.uuid);
                if (entry.id().equals(followingPlayerId)) {
                    stopFollowing();
                }
            }
            refreshObjectPanel();
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
            openSchematicEditor(id);
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
        if (!VoxyMapCameraController.isActive() || !LitematicaPlusBridge.isAvailable()
                || !VoxyMapSettings.schematicsEnabled()) {
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

    private void capturePlayerMarkers() {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            playerMarkers = List.of();
            return;
        }

        List<PlayerMarker> snapshot = new ArrayList<>();
        Set<String> knownUuids = new HashSet<>();
        Set<String> knownNames = new HashSet<>();
        boolean radar = ServerRules.radarAllowed();
        for (Player player : minecraft.level.players()) {
            knownUuids.add(RemotePlayerMarkers.normalizeUuid(player.getUUID().toString()));
            knownNames.add(player.getName().getString().toLowerCase(java.util.Locale.ROOT));
            if (player != minecraft.player && (!radar || player.isInvisibleTo(minecraft.player))) {
                continue;
            }
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

        if (radar && VoxyMapSettings.vanillaSquadPlayersActive()) {
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
        lastDrawnLabels = List.of();
        if (!VoxyMapCameraController.isActive() || !VoxyMapSettings.waypointsEnabled()) {
            return;
        }
        String worldId = currentWaypointWorldId();
        if (worldId == null) {
            return;
        }

        List<WaypointManager.Waypoint> waypoints = WaypointManager.visibleForWorld(worldId, WaypointDisplay.Surface.MAP);
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
        List<ProjectedWaypoint> drawnLabels = new ArrayList<>();
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
            drawnLabels.add(entry);
        }
        lastProjectedWaypoints = List.copyOf(projected);
        lastDrawnLabels = List.copyOf(drawnLabels);

        if (waypointForm != null) {
            double[] pending = waypointForm.previewPosition();
            ScreenPoint pendingPoint = projectToScreen(pending[0], pending[1], pending[2]);
            if (pendingPoint != null) {
                drawWaypointPin(g, pendingPoint, waypointForm.color(), true);
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
        }
        for (int i = lastDrawnLabels.size() - 1; i >= 0; i--) {
            ProjectedWaypoint entry = lastDrawnLabels.get(i);
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
        String worldId = currentWaypointWorldId();
        if (worldId == null || selectedWaypointId == null) return null;
        return WaypointManager.forWorld(worldId).stream()
                .filter(waypoint -> waypoint.id().equals(selectedWaypointId)).findFirst().orElse(null);
    }

    private void deleteSelectedWaypoint() {
        String worldId = currentWaypointWorldId();
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
        int accent = UiTheme.ACCENT_BRIGHT;
        if (surfacePickPending) {
            message = Component.translatable("screen.voxymap.waypoint.picking").getString();
        } else if (System.nanoTime() < pickFailureUntilNanos) {
            message = Component.translatable("screen.voxymap.waypoint.no_surface").getString();
            accent = UiTheme.WARNING;
        } else if (deletedWaypoint != null && System.nanoTime() < undoDeleteUntilNanos) {
            message = Component.translatable("screen.voxymap.waypoint.deleted_undo").getString();
            accent = UiTheme.WARNING;
        } else if (transientMessage != null && System.nanoTime() < transientMessageUntilNanos) {
            message = transientMessage;
        }
        if (message == null) return;
        int boxW = Math.min(width - HUD_MARGIN * 2, minecraft.font.width(message) + 24);
        int boxX = (width - boxW) / 2;
        int boxY = footerTop - UiTheme.SPACE_M - 22;
        UiDraw.panel(g, boxX, boxY, boxW, 22, UiTheme.SURFACE_RAISED);
        UiDraw.outline(g, boxX, boxY, boxW, 22, UiTheme.BORDER);
        g.fill(boxX + 3, boxY + 5, boxX + 5, boxY + 17, accent);
        UiDraw.centeredText(g, minecraft.font, message, width / 2 + 2, boxY + 7, boxW - 16, UiTheme.TEXT);
    }

    private ScreenRect textInputBox() {
        int boxW = Math.min(320, width - HUD_MARGIN * 2);
        return new ScreenRect(width / 2 - boxW / 2, HUD_MARGIN + 4, boxW, 58);
    }

    private void drawTextInputOverlay(GuiGraphicsExtractor g) {
        if (textInputKind == null) {
            return;
        }
        String titleKey = switch (textInputKind) {
            case RECEIVE_CODE -> "screen.voxymap.mapshare.receive_code_title";
            case GOTO -> "screen.voxymap.goto.title";
        };
        String title = Component.translatable(titleKey).getString();
        String text = nameInputBuffer.toString();
        ScreenRect box = textInputBox();
        int boxW = box.width();
        int boxH = box.height();
        int boxX = box.x();
        int boxY = box.y();
        UiDraw.panel(g, boxX, boxY, boxW, boxH, UiTheme.SURFACE_RAISED);
        UiDraw.outline(g, boxX, boxY, boxW, boxH, UiTheme.BORDER_STRONG);
        g.text(minecraft.font, title, boxX + 10, boxY + 7, UiTheme.TEXT);
        int fieldX = boxX + 10;
        int fieldY = boxY + 20;
        int fieldW = boxW - 20;
        int fieldH = 20;
        UiDraw.panel(g, fieldX, fieldY, fieldW, fieldH, UiTheme.SURFACE_SUNKEN);
        UiDraw.outline(g, fieldX, fieldY, fieldW, fieldH, UiTheme.ACCENT_BRIGHT);
        g.text(minecraft.font, UiDraw.ellipsize(minecraft.font, text, fieldW - 12), fieldX + 6, fieldY + 6, UiTheme.TEXT);
        minecraft.textInputManager().setTextInputArea(fieldX, fieldY, fieldX + fieldW, fieldY + fieldH);
        if ((System.nanoTime() / 500_000_000L) % 2 == 0) {
            int caretX = fieldX + 6 + Math.min(fieldW - 12, minecraft.font.width(text));
            g.fill(caretX, fieldY + 4, caretX + 1, fieldY + fieldH - 4, UiTheme.TEXT);
        }
        String hint = Component.translatable(textInputKind == TextInputKind.GOTO
                ? "screen.voxymap.goto.hint" : "screen.voxymap.waypoint.hint").getString();
        UiDraw.text(g, minecraft.font, hint, boxX + 10, boxY + boxH - 13, boxW - 20, UiTheme.TEXT_MUTED);
    }

    private void showTransientMessage(Component message) {
        transientMessage = message.getString();
        transientMessageUntilNanos = System.nanoTime() + 3_000_000_000L;
    }

    private void beginAddWaypoint(double worldX, double worldY, double worldZ) {
        String worldId = currentWaypointWorldId();
        if (worldId == null) return;
        openWaypointForm(WaypointForm.create(worldId, worldX, worldY, worldZ, waypointFormListener()));
    }

    private void beginEditWaypoint(WaypointManager.Waypoint waypoint) {
        String worldId = currentWaypointWorldId();
        if (worldId == null || blockedFromEditing(worldId, waypoint)) return;
        openWaypointForm(WaypointForm.edit(worldId, waypoint, waypointFormListener()));
    }

    private WaypointForm.Listener waypointFormListener() {
        return new WaypointForm.Listener() {
            @Override
            public void saved(WaypointManager.Waypoint waypoint) {
                selectedWaypointId = waypoint.id();
                selectedObjectId = waypointObjectId(waypoint);
                closeWaypointForm();
            }

            @Override
            public void cancelled() {
                closeWaypointForm();
            }

            @Override
            public void teleported() {
                closeWaypointForm();
                onClose();
            }
        };
    }

    private void cycleWaypointFormFocus(boolean backwards) {
        List<AbstractWidget> order = new ArrayList<>();
        for (AbstractWidget widget : waypointForm.widgets()) {
            if (widget.visible && widget.active) {
                order.add(widget);
            }
        }
        if (order.isEmpty()) {
            return;
        }
        int index = order.indexOf(getFocused());
        int next = index < 0 ? (backwards ? order.size() - 1 : 0)
                : Math.floorMod(index + (backwards ? -1 : 1), order.size());
        setFocused(order.get(next));
    }

    @Override
    public void setFocused(GuiEventListener focused) {
        super.setFocused(focused);
        if (focused != null) {
            objectPanel.blurSearch();
        }
    }

    private void openWaypointForm(WaypointForm form) {
        helpOpen = false;
        closeWaypointForm();
        cancelTextInput();
        contextMenu.close();
        objectPanel.blurSearch();
        form.setHint(Component.translatable("screen.voxymap.waypoint.map_hint"));
        waypointForm = form;
        installWaypointForm();
    }

    private void installWaypointForm() {
        if (waypointForm == null) return;
        for (AbstractWidget widget : waypointForm.widgets()) {
            removeWidget(widget);
        }
        for (AbstractWidget widget : waypointForm.layout(minecraft.font, width, height, HUD_MARGIN + 4)) {
            addRenderableWidget(widget);
        }
        setFocused(waypointForm.nameBox());
    }

    private void closeWaypointForm() {
        pinPressPending = false;
        if (waypointForm == null) return;
        setFocused(null);
        for (AbstractWidget widget : waypointForm.widgets()) {
            removeWidget(widget);
        }
        waypointForm = null;
    }

    private void drawWaypointForm(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (waypointForm == null) return;
        waypointForm.extractPanel(g, minecraft.font);
        for (AbstractWidget widget : waypointForm.widgets()) {
            widget.extractRenderState(g, mouseX, mouseY, delta);
        }
        waypointForm.extractOverlay(g, minecraft.font);
    }

    private void syncTextInput() {
        if (minecraft == null) return;
        if (textInputKind != null || objectPanel.searchFocused() || (settingsOpen && settingsView.typing(getFocused()))) {
            minecraft.textInputManager().startTextInput(this);
        } else {
            minecraft.textInputManager().stopTextInput(this);
        }
    }

    private void openContextMenu(double mouseX, double mouseY) {
        String worldId = currentWaypointWorldId();
        if (worldId == null) return;
        pickSurfaceAt(mouseX, mouseY, surface -> {
            if (surface == null) {
                pickFailureUntilNanos = System.nanoTime() + 3_000_000_000L;
                return;
            }
            int bx = surface.blockX();
            int by = surface.blockY() + 1;
            int bz = surface.blockZ();
            double wx = bx + 0.5;
            double wy = by;
            double wz = bz + 0.5;
            List<MapContextMenu.Item> items = new ArrayList<>();
            items.add(new MapContextMenu.Item(Component.translatable("screen.voxymap.context.add_waypoint"), true,
                    () -> beginAddWaypoint(wx, wy, wz)));
            items.add(new MapContextMenu.Item(Component.translatable("screen.voxymap.context.add_temporary"), true, () -> {
                WaypointManager.Waypoint added = TemporaryWaypoints.place(worldId, wx, wy, wz);
                if (added != null) {
                    selectedWaypointId = added.id();
                    selectedObjectId = waypointObjectId(added);
                    showTransientMessage(Component.translatable("message.voxymap.waypoint.temporary_placed", bx, by, bz));
                }
            }));
            items.add(new MapContextMenu.Item(Component.translatable("screen.voxymap.context.copy"), true, () -> {
                minecraft.keyboardHandler.setClipboard(bx + " " + by + " " + bz);
                showTransientMessage(Component.translatable("screen.voxymap.context.copied", bx, by, bz));
            }));
            if (canTeleport()) {
                items.add(new MapContextMenu.Item(Component.translatable("screen.voxymap.context.teleport"), true, () -> {
                    minecraft.getConnection().sendCommand("tp @s " + bx + " " + by + " " + bz);
                    onClose();
                }));
            }
            contextMenu.open(mouseX, mouseY, Component.literal(bx + " / " + by + " / " + bz), items,
                    minecraft.font, width, height);
        });
    }

    private boolean canTeleport() {
        return minecraft != null && minecraft.player != null && minecraft.getConnection() != null
                && minecraft.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private void addWaypointAtCursor() {
        if (currentWaypointWorldId() == null) return;
        pickSurfaceAt(lastZoomMouseX(), lastZoomMouseY(), surface -> {
            if (surface != null) {
                beginAddWaypoint(surface.blockX() + 0.5, surface.blockY() + 1.0, surface.blockZ() + 0.5);
            } else {
                pickFailureUntilNanos = System.nanoTime() + 3_000_000_000L;
            }
        });
    }

    private void moveWaypointFormTo(double mouseX, double mouseY) {
        if (waypointForm == null) return;
        WaypointForm form = waypointForm;
        pickSurfaceAt(mouseX, mouseY, surface -> {
            if (waypointForm != form) return;
            if (surface != null) {
                form.moveTo(surface.blockX() + 0.5, surface.blockY() + 1.0, surface.blockZ() + 0.5);
            } else {
                pickFailureUntilNanos = System.nanoTime() + 3_000_000_000L;
            }
        });
    }

    private void beginGoto() {
        helpOpen = false;
        textInputKind = TextInputKind.GOTO;
        nameInputBuffer.setLength(0);
        nameInputBuffer.append(Mth.floor(viewCenterX)).append(' ').append(Mth.floor(viewCenterZ));
    }

    private boolean applyGoto(String value) {
        MapGoto.Target target = MapGoto.parse(value);
        if (target == null) {
            return false;
        }
        stopFollowing();
        viewCenterX = target.x() + 0.5;
        viewCenterZ = target.z() + 0.5;
        if (target.y() != null) {
            viewCenterY = target.y();
        }
        moveVelocityX = 0.0;
        moveVelocityZ = 0.0;
        syncWorldCamera();
        return true;
    }

    private void cancelTextInput() {
        textInputKind = null;
        nameInputBuffer.setLength(0);
    }

    private void confirmTextInput() {
        String worldId = currentWaypointWorldId();
        String value = nameInputBuffer.toString().trim();
        if (textInputKind == TextInputKind.GOTO) {
            if (applyGoto(value)) {
                cancelTextInput();
            }
            return;
        }
        if (worldId != null) {
            switch (textInputKind) {
                case RECEIVE_CODE -> {
                    String address = WaypointSyncSettings.address(worldId);
                    if (address != null && value.matches("[0-9]{6}")) {
                        MapShareClient.startReceiving(minecraft, address, value);
                    }
                }
                case GOTO -> {
                }
            }
        }
        cancelTextInput();
    }

    private int textInputMaxLength() {
        return switch (textInputKind) {
            case RECEIVE_CODE -> 6;
            case GOTO -> 40;
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
            if (marker.hidden()) {
                continue;
            }
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
        ScreenPoint modelTop = modelTop(marker);
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
        if (modelTop == null) {
            drawDiamond(g, point.x, point.y, radius + 2, outline);
            drawDiamond(g, point.x, point.y, radius, color);
            g.fill(point.x - 1, point.y - 1, point.x + 2, point.y + 2, 0xFFFFFFFF);
        }
        if (!showLabel) {
            return;
        }

        String label = playerLabel(marker);
        Identifier skin = resolveCachedSkin(marker.uuid);
        int headSize = 10;
        int headGap = skin != null ? headSize + 3 : 0;
        int labelWidth = minecraft.font.width(label) + 8 + headGap;
        int labelX = Mth.clamp(point.x - labelWidth / 2, 3, Math.max(3, width - labelWidth - 3));
        int labelY = modelTop != null ? Math.min(point.y - radius - 15, modelTop.y - 14) : point.y - radius - 15;
        g.fill(labelX, labelY, labelX + labelWidth, labelY + 11, hovered ? 0xEE030914 : 0xCC030914);
        g.fill(labelX, labelY + 10, labelX + labelWidth, labelY + 11, color);
        if (skin != null) {
            PlayerHeadIcon.draw(g, skin, labelX + 3, labelY, headSize);
        }
        g.text(minecraft.font, label, labelX + 4 + headGap, labelY + 2, 0xFFFFFFFF);
    }

    private ScreenPoint modelTop(PlayerMarker marker) {
        if (flat || marker.remote || marker.uuid == null || minecraft == null || minecraft.level == null) {
            return null;
        }
        Player player = minecraft.level.getPlayerByUUID(marker.uuid);
        if (player == null || !MapEntityScale.shown(player)) {
            return null;
        }
        double height = player.getBbHeight() * MapEntityScale.factor(player, marker.x, marker.y, marker.z);
        return projectToScreen(marker.x, marker.y + height, marker.z);
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
        if (resolved != null && !PlayerHeadIcon.isDefaultSkin(uuid, resolved)) {
            skinTextureCache.put(uuid, resolved);
            missingSkinRetryNanos.remove(uuid);
        } else if (resolved == null) {
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

    private void drawFlatTerrain(GuiGraphicsExtractor g) {
        Map2dRenderer.extract(g, minecraft, new Map2dRenderer.View(viewCenterX, viewCenterZ, blocksPerPixel, width,
                height, flatSlice()));
    }

    private int flatSlice() {
        if (minecraft == null || minecraft.level == null || !minecraft.level.dimensionType().hasCeiling()
                || !ServerRules.caveMapsAllowed(minecraft.level.dimension().equals(Level.NETHER))) {
            return Map2dGeometry.SURFACE;
        }
        return Map2dGeometry.caveSlice(viewCenterY);
    }

    private void setView(boolean value) {
        if (flat != value) {
            toggleFlat();
        }
    }

    private void setFlat(boolean value) {
        flat = value;
        yawTarget = null;
        MapViewMode.setFlat(value);
        VoxyMapSettings.setMap2d(value);
        if (value) {
            blocksPerPixel = Math.min(blocksPerPixel, Map2dGeometry.MAX_BLOCKS_PER_PIXEL);
        }
        hoverCursorHit = null;
        cachedKeyHints = buildKeyHints();
    }

    private void toggleFlat() {
        setFlat(!flat);
        syncWorldCamera();
    }

    private ScreenPoint projectToScreen(double worldX, double worldY, double worldZ) {
        if (flat) {
            double x = Mth.clamp(Map2dGeometry.screenX(worldX, viewCenterX, blocksPerPixel, width), -1.0E6, 1.0E6);
            double y = Mth.clamp(Map2dGeometry.screenY(worldZ, viewCenterZ, blocksPerPixel, height), -1.0E6, 1.0E6);
            return new ScreenPoint((int) Math.round(x), (int) Math.round(y));
        }
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

    private void openWebMap() {
        if (minecraft.level == null) {
            return;
        }
        MinecraftCompat.showScreen(minecraft, new WebMapScreen(VanillaSquadWebMap.currentMapId(minecraft),
                minecraft.level.dimension().identifier().toString(), viewCenterX, viewCenterZ, blocksPerPixel));
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

    private String currentWaypointWorldId() {
        return WaypointWorlds.effective(currentVoxyWorldId());
    }

    private String currentVoxyWorldId() {
        if (minecraft == null || minecraft.level == null) return null;
        WorldIdentifier identifier = WorldIdentifier.of(minecraft.level);
        return identifier == null ? null : identifier.getWorldId();
    }

    private void drawXaeroTransition(GuiGraphicsExtractor g) {
        double raw = (System.nanoTime() - xaeroTransitionStartedAt) / (double) XAERO_TRANSITION_NANOS;
        raw = Mth.clamp(raw, 0.0, 1.0);
        double eased = 1.0 - Math.pow(1.0 - raw, 3.0);
        int maxRadius = Math.max(width, height) + 120;
        int radius = (int) (maxRadius * eased);
        int originX = xaeroButton != null ? xaeroButton.getX() + xaeroButton.getWidth() / 2 : width - 80;
        int originY = xaeroButton != null ? xaeroButton.getY() + xaeroButton.getHeight() / 2 : 24;

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
        GroundPoint point = new GroundPoint(hit.blockX() + 0.5, hit.blockZ() + 0.5);
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
        if (flat) {
            ScreenPoint p1 = projectToScreen(x1, y1, z1);
            ScreenPoint p2 = projectToScreen(x2, y2, z2);
            drawSegment(g, p1.x, p1.y, p2.x, p2.y, color);
            return;
        }
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

        double[] p1 = projectExact(x1, y1, z1);
        double[] p2 = projectExact(x2, y2, z2);
        if (p1 != null && p2 != null) {
            drawSegment(g, p1[0], p1[1], p2[0], p2[1], color);
        }
    }

    private double[] projectExact(double worldX, double worldY, double worldZ) {
        return MapProjection.projectExact(
                worldX, worldY, worldZ,
                VoxyMapCameraController.cameraX(), VoxyMapCameraController.cameraY(), VoxyMapCameraController.cameraZ(),
                camForwardX, camForwardY, camForwardZ,
                camRightX, camRightZ,
                camUpX, camUpY, camUpZ,
                camTanX, camTanY, width, height);
    }

    private void drawSegment(GuiGraphicsExtractor g, double x1, double y1, double x2, double y2, int color) {
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
        if (contextMenu.isOpen()) {
            contextMenu.close();
        }
        if (waypointForm != null && waypointForm.contains(mouseX, mouseY)) {
            return true;
        }
        if (objectPanel.scroll(mouseX, mouseY, scrollY)) {
            refreshObjectPanel();
            return true;
        }
        if (settingsOpen && settingsView.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (helpOpen) {
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
        double newBlocksPerPixel = Mth.clamp(oldBlocksPerPixel * factor, 0.125,
                flat ? Map2dGeometry.MAX_BLOCKS_PER_PIXEL : 256.0);
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
        if (contextMenu.click(event.x(), event.y())) {
            return true;
        }
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
        if (waypointForm != null) {
            return handleWaypointFormClick(event, isInside);
        }
        if (textInputKind != null) {
            ScreenRect box = textInputBox();
            if (event.x() < box.x() || event.x() >= box.right() || event.y() < box.y() || event.y() >= box.bottom()) {
                cancelTextInput();
            }
            return true;
        }
        if (helpOpen) {
            helpOpen = false;
            return true;
        }
        if (objectPanel.contains(event.x(), event.y())) {
            setFocused(null);
            MapObjectPanel.Action topPanelAction = objectPanel.click(event.x(), event.y(), event.button());
            if (topPanelAction != null) {
                handleObjectPanelAction(topPanelAction);
                return true;
            }
        }
        if (settingsOpen && event.button() == InputConstants.MOUSE_BUTTON_RIGHT
                && settingsView.entryAt(event.x(), event.y()) == SettingsCatalog.Entry.MAP_RECEIVE) {
            if (MapShareClient.receiveStatus() == MapShareClient.ReceiveStatus.AWAITING_CONFIRMATION) {
                MapShareClient.declineReceive();
            } else if (MapShareClient.receiveStatus() == MapShareClient.ReceiveStatus.RECEIVING
                    || MapShareClient.receiveStatus() == MapShareClient.ReceiveStatus.CONNECTING) {
                MapShareClient.stopReceiving();
            }
            return true;
        }
        if (settingsOpen && settingsView.mouseClicked(event, isInside)) {
            return true;
        }
        AbstractWidget toolbarHit = toolbarWidgetAt(event.x(), event.y());
        if (toolbarHit != null) {
            setFocused(null);
            toolbarHit.mouseClicked(event, isInside);
            return true;
        }
        if (settingsOpen) {
            closeSettings();
            return true;
        }
        setFocused(null);
        MapObjectPanel.Action panelAction = objectPanel.click(event.x(), event.y(), event.button());
        if (panelAction != null) {
            handleObjectPanelAction(panelAction);
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {

            startRightPress(event.x(), event.y());
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            WaypointManager.Waypoint hit = hitTestWaypoint(event.x(), event.y());
            if (hit != null) {
                if (hit.id().equals(selectedWaypointId)) {
                    beginEditWaypoint(hit);
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
                    openSchematicEditor(schematicHit);
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
                if (currentWaypointWorldId() != null) {
                    pickSurfaceAt(event.x(), event.y(), surface -> {
                        if (surface != null) {
                            beginAddWaypoint(surface.blockX() + 0.5, surface.blockY() + 1.0, surface.blockZ() + 0.5);
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

    private boolean handleWaypointFormClick(MouseButtonEvent event, boolean isInside) {
        if (waypointForm.contains(event.x(), event.y())) {
            for (AbstractWidget widget : waypointForm.widgets()) {
                if (widget.visible && widget.isMouseOver(event.x(), event.y()) && widget.mouseClicked(event, isInside)) {
                    setFocused(widget);
                    if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                        setDragging(true);
                    }
                    return true;
                }
            }
            setFocused(null);
            return true;
        }
        setFocused(null);
        if (toolbarWidgetAt(event.x(), event.y()) != null || objectPanel.contains(event.x(), event.y())
                || settingsOpen && settingsView.contains(event.x(), event.y())) {
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            pinPressPending = true;
            pinPressX = event.x();
            pinPressY = event.y();
            dragging = true;
            moveVelocityX = 0.0;
            moveVelocityZ = 0.0;
        } else if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            rightDragging = true;
            rightPressMoved = true;
        }
        return true;
    }

    private void startRightPress(double mouseX, double mouseY) {
        rightDragging = true;
        rightPressX = mouseX;
        rightPressY = mouseY;
        rightPressMoved = false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (settingsOpen && settingsView.mouseReleased(event)) {
            return true;
        }
        if (waypointForm != null) {
            super.mouseReleased(event);
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            dragging = false;
            if (pinPressPending) {
                pinPressPending = false;
                if (Math.hypot(event.x() - pinPressX, event.y() - pinPressY) <= CLICK_MOVE_TOLERANCE) {
                    moveWaypointFormTo(event.x(), event.y());
                }
            }
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            boolean click = rightDragging && !rightPressMoved
                    && Math.hypot(event.x() - rightPressX, event.y() - rightPressY) <= CLICK_MOVE_TOLERANCE;
            rightDragging = false;
            rightPressMoved = false;
            if (click && waypointForm == null && textInputKind == null) {
                openContextMenu(event.x(), event.y());
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (settingsOpen && settingsView.mouseDragged(event, deltaX, deltaY)) {
            return true;
        }
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
            if (Math.hypot(event.x() - rightPressX, event.y() - rightPressY) > CLICK_MOVE_TOLERANCE) {
                rightPressMoved = true;
            }
            if (flat) {
                return true;
            }
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
            if (keyCode == InputConstants.KEY_ESCAPE || VoxyMapClient.isOpenMapKey(event)) {
                cancelMapShareSelection();
                settingsOpen = true;
            }
            return true;
        }
        if (contextMenu.keyPressed(keyCode)) {
            return true;
        }
        if (helpOpen) {
            if (keyCode == InputConstants.KEY_ESCAPE || keyCode == InputConstants.KEY_H) {
                helpOpen = false;
            }
            return true;
        }
        if (waypointForm != null) {
            GuiEventListener focused = getFocused();
            boolean formFocused = focused != null && waypointForm.owns(focused);
            if (keyCode == InputConstants.KEY_ESCAPE) {
                closeWaypointForm();
            } else if (keyCode == InputConstants.KEY_TAB) {
                cycleWaypointFormFocus(event.hasShiftDown());
            } else if ((keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)
                    && !(formFocused && focused instanceof AbstractButton)) {
                waypointForm.submit();
            } else if (formFocused) {
                focused.keyPressed(event);
            }
            return true;
        }
        if (textInputKind != null) {
            if (keyCode == InputConstants.KEY_ESCAPE) {
                cancelTextInput();
            } else if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
                confirmTextInput();
            } else if (keyCode == InputConstants.KEY_BACKSPACE && nameInputBuffer.length() > 0) {
                nameInputBuffer.deleteCharAt(nameInputBuffer.length() - 1);
            } else if (event.isPaste()) {
                pasteFromClipboard();
            } else if (event.isCopy()) {
                minecraft.keyboardHandler.setClipboard(nameInputBuffer.toString());
            } else if (event.isCut()) {
                minecraft.keyboardHandler.setClipboard(nameInputBuffer.toString());
                nameInputBuffer.setLength(0);
            }

            return true;
        }
        if (settingsOpen) {
            if (keyCode == InputConstants.KEY_ESCAPE) {
                closeSettings();
                return true;
            }
            if (settingsView.keyPressed(event, getFocused())) {
                return true;
            }
        }
        if (getFocused() instanceof AbstractWidget focusedWidget && toolbar.contains(focusedWidget)
                && (event.isSelection() || event.isLeft() || event.isRight())
                && focusedWidget.keyPressed(event)) {
            return true;
        }
        if (objectPanel.keyPressed(keyCode)) {
            refreshObjectPanel();
            return true;
        }
        if (keyCode == InputConstants.KEY_ESCAPE && getFocused() != null) {
            setFocused(null);
            return true;
        }
        if (keyCode == InputConstants.KEY_ESCAPE || VoxyMapClient.isOpenMapKey(event)) {
            onClose();
            return true;
        }
        if (keyCode == InputConstants.KEY_H) {
            toggleHelp();
            return true;
        }
        if (keyCode == InputConstants.KEY_O) {
            objectPanel.toggle();
            return true;
        }
        if (keyCode == InputConstants.KEY_V) {
            toggleFlat();
            return true;
        }
        if (keyCode == InputConstants.KEY_G) {
            beginGoto();
            return true;
        }
        if (VoxyMapClient.isAddWaypointKey(event)) {
            addWaypointAtCursor();
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
            if (selected != null) {
                beginEditWaypoint(selected);
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
        if (waypointForm != null) {
            super.charTyped(event);
            return true;
        }
        if (textInputKind != null) {
            boolean allowed = event.isAllowedChatCharacter()
                    && (textInputKind != TextInputKind.RECEIVE_CODE || Character.isDigit(event.codepoint()))
                    && (textInputKind != TextInputKind.GOTO || MapGoto.allowedCharacter(event.codepoint()));
            if (allowed && nameInputBuffer.length() < activeInputMaxLength()) {
                nameInputBuffer.append(event.codepointAsString());
            }
            return true;
        }
        if (objectPanel.charTyped(event.codepoint())) {
            refreshObjectPanel();
            return true;
        }
        if (settingsOpen && settingsView.charTyped(event, getFocused())) {
            return true;
        }
        return super.charTyped(event);
    }

    private int activeInputMaxLength() {
        return textInputMaxLength();
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
            if (textInputKind == TextInputKind.GOTO && !MapGoto.allowedCharacter(c)) continue;
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
        closeWaypointForm();
        contextMenu.close();
        surfacePickRequest++;
        surfacePickPending = false;
        hoverCursorPickRequest++;
        hoverCursorPickPending = false;
        hoverCursorHit = null;
        closeMapView();
        if (openLogged) {
            VoxyMapClient.LOGGER.debug("Map closed");
            openLogged = false;
        }
        super.removed();
    }

    private void openSchematicEditor(String id) {
        closeMapView();
        if (LitematicaPlusBridge.openEditor(id)) {
            return;
        }
        MapViewMode.setFlat(flat);
        VoxyBridge.suppressEnvironmentalFogForMap();
        MapRenderSettingsGuard.applyForMap(minecraft);
        syncWorldCamera();
        VoxyMapGuiRenderer.open(minecraft);
    }

    private void closeMapView() {
        closeSettings();
        helpOpen = false;
        cancelMapShareSelection();
        xaeroTransitionActive = false;
        MapViewMode.setFlat(false);
        Map2dTiles.clear(minecraft);
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

    private enum TextInputKind { RECEIVE_CODE, GOTO }

    private record PlayerMarker(String name, double x, double y, double z, boolean local, boolean remote,
                                UUID uuid, float yawDegrees) {
        private boolean isActivePlayer() {
            return !local && !remote && Float.isFinite(yawDegrees);
        }

        private boolean hidden() {
            return !local && VoxyMapSettings.playerHidden(uuid);
        }
    }
}
