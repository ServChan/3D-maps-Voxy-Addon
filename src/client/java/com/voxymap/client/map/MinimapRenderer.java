package com.voxymap.client.map;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import com.mojang.blaze3d.platform.NativeImage;
import com.voxymap.client.integration.LitematicaPlusBridge;
import com.voxymap.client.VoxyMapClient;
import com.voxymap.client.gui.PlayerHeadIcon;
import com.voxymap.client.gui.MinimapLayoutScreen;
import com.voxymap.client.gui.WaypointIcons;
import com.voxymap.client.gui.WindowMapScreen;
import com.voxymap.client.gui.ui.UiDraw;
import com.voxymap.client.gui.ui.UiTheme;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

public final class MinimapRenderer {

    private static final int GRID_CHUNKS = 33;
    private static final int TEXTURE_SIZE = GRID_CHUNKS * MinimapTileCache.TILE_SIZE;
    private static final float ZOOM_SCALE = 1.6f;
    private static final int MARGIN = 8;
    private static final int MIN_DISPLAY_SIZE = 64;
    private static final int MARKER_RADIUS = 2;
    private static final int WAYPOINT_ICON_SIZE = 9;
    private static final int MOB_MARKER_RADIUS = 1;
    private static final int PLAYER_HEAD_SIZE = 8;
    private static final int MOB_ICON_MAX = 12;
    private static final int PASSIVE_OUTLINE = 0xE0101418;
    private static final int[][] OUTLINE_OFFSETS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private static final double OFFSCREEN_EDGE_INSET = 9.0;
    private static final int INFO_LINE_HEIGHT = 10;
    private static final int EFFECT_ROW_HEIGHT = 26;
    private static final int BACKGROUND_COLOR = UiTheme.SURFACE;
    private static final int FRAME_COLOR = UiTheme.BORDER_STRONG;
    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath("voxymap", "minimap_dynamic");

    private static final double MAX_BLIT_RADIUS = (GRID_CHUNKS / 2) * 16.0 - 4.0;

    private static final int Y_REFRESH_THRESHOLD_BLOCKS = MinimapTileCache.Y_TOLERANCE_BLOCKS;

    private static final int TILE_RETENTION_RADIUS_CHUNKS = GRID_CHUNKS;

    private static final long MISSING_TILE_RETRY_MILLIS = 500L;

    private static final long BACKGROUND_REFRESH_MILLIS = 250L;

    private static final int BACKGROUND_REFRESH_TILES = 12;

    private static final int TILE_COMPUTE_BUDGET = 40;

    private static final double HEIGHT_HINT_BLOCKS = 3.0;
    private static final double NEAR_Y_BLOCKS = 24.0;
    private static final double FAR_Y_BLOCKS = 64.0;
    private static final int FAR_Y_ALPHA = 0x50;

    private static final int UNLOADED_CHUNK_HINT_COLOR = 0x40FFB000;

    private static final int[] TILE_ORDER = centerOutOrder(GRID_CHUNKS);

    private static final MinimapTileCache TILES = new MinimapTileCache();
    private static DynamicTexture texture;
    private static int centerChunkX = Integer.MIN_VALUE;
    private static int centerChunkZ = Integer.MIN_VALUE;
    private static int lastRebuildPlayerY = Integer.MIN_VALUE;
    private static ResourceKey<Level> lastDimension = null;
    private static TileLook lastLook = null;

    private static Level lastLevel = null;

    private static boolean hasMissingTiles = false;
    private static long nextRetryAtMillis = 0L;
    private static MinimapTileCache.ScanMode lastMode = null;
    private static int refreshCursor = 0;
    private static int visibleTileCount = TILE_ORDER.length;
    private static long nextBackgroundRefreshAtMillis = 0L;

    private static final boolean[] missingMask = new boolean[GRID_CHUNKS * GRID_CHUNKS];

    private static volatile boolean hudHookFired = false;
    private static int lastDisplaySize = 128;
    private static MinimapPlacement.Placement lastPlacement;
    private static long lastPlacementNanos;
    private static final long PLACEMENT_FRESH_NANOS = 200_000_000L;
    private static int terrainTint = 0xFFFFFFFF;
    private static float backgroundAlpha = 1.0f;
    private static final int DIRTY_TILES_PER_FRAME = 16;
    private static final long DIRTY_REFRESH_MILLIS = 100L;
    private static long nextDirtyRefreshAtMillis = 0L;
    private static final Set<Long> DIRTY_TILES = new HashSet<>();
    private static float partialTick;
    private static double renderX;
    private static double renderY;
    private static double renderZ;

    private MinimapRenderer() {
    }

    private record TileLook(VoxyMapSettings.MinimapColors colors, Set<MapOverlay> overlays, Long slimeSeed) {
    }

    record View(int screenX, int screenY, int size, int centerX, int centerY, double scale,
                double cosA, double sinA, boolean circle) {
        double radius() {
            return size / 2.0;
        }

        boolean contains(int px, int py) {
            if (circle) {
                double dx = px + 0.5 - centerX;
                double dy = py + 0.5 - centerY;
                double r = radius() - 1.0;
                return dx * dx + dy * dy <= r * r;
            }
            return px >= screenX && px < screenX + size && py >= screenY && py < screenY + size;
        }

        int screenX(double dx, double dz) {
            return centerX + (int) Math.round(rotatedX(dx, dz, cosA, sinA) * scale);
        }

        int screenY(double dx, double dz) {
            return centerY + (int) Math.round(rotatedZ(dx, dz, cosA, sinA) * scale);
        }

        double exactX(double dx, double dz) {
            return centerX + rotatedX(dx, dz, cosA, sinA) * scale;
        }

        double exactY(double dx, double dz) {
            return centerY + rotatedZ(dx, dz, cosA, sinA) * scale;
        }

        boolean rotated() {
            return !(cosA == 1.0 && sinA == 0.0);
        }
    }

    public static void extract(GuiGraphicsExtractor g, Minecraft minecraft) {
        hudHookFired = true;
        if (!VoxyMapSettings.minimapEnabled() || !ServerRules.minimapAllowed() || minecraft.level == null || minecraft.player == null
                || VoxyMapCameraController.isActive() || minecraft.gui.hud.isHidden() || hiddenByScreen(minecraft)) {
            return;
        }

        Player player = minecraft.player;
        Level level = minecraft.level;
        partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        renderX = Mth.lerp(partialTick, player.xo, player.getX());
        renderY = Mth.lerp(partialTick, player.yo, player.getY());
        renderZ = Mth.lerp(partialTick, player.zo, player.getZ());
        int playerChunkX = Math.floorDiv((int) Math.floor(player.getX()), 16);
        int playerChunkZ = Math.floorDiv((int) Math.floor(player.getZ()), 16);
        int playerY = (int) Math.floor(player.getY());
        ResourceKey<Level> dimension = level.dimension();

        ensureTexture(minecraft);

        double scale = VoxyMapSettings.minimapPixelsPerBlock();
        List<MinimapInfo.Line> lines = MinimapInfo.lines(minecraft);
        int infoHeight = lines.isEmpty() ? 0 : 7 + lines.size() * INFO_LINE_HEIGHT;
        VoxyMapSettings.MinimapCorner corner = VoxyMapSettings.minimapCorner();
        int topOffset = corner == VoxyMapSettings.MinimapCorner.TOP_RIGHT ? effectOffset(player) : 0;
        int baseSize = VoxyMapClient.isMinimapZoomHeld()
                ? Math.round(VoxyMapSettings.minimapSize().pixels() * ZOOM_SCALE)
                : VoxyMapSettings.minimapSize().pixels();
        int displaySize = fitSize(baseSize, g.guiWidth() - 2 * MARGIN,
                g.guiHeight() - 2 * MARGIN - infoHeight - topOffset);
        lastDisplaySize = displaySize;
        boolean circle = VoxyMapSettings.minimapShape() == VoxyMapSettings.MinimapShape.CIRCLE;

        double rotation = 0.0;
        float yaw = player.getViewYRot(partialTick);
        if (VoxyMapSettings.minimapRotates()) {
            double dirX = PlayerViewConeGeometry.directionX(yaw);
            double dirZ = PlayerViewConeGeometry.directionZ(yaw);
            rotation = Math.atan2(-dirX, -dirZ);
        }
        double worldRadius = displaySize / 2.0 / scale;
        double diagonalFactor = rotation == 0.0 || circle ? 1.0 : 1.4143;
        double blitRadius = Math.min(worldRadius * diagonalFactor, MAX_BLIT_RADIUS);
        visibleTileCount = visibleTileCount(blitRadius);

        MinimapTileCache.ScanMode mode = scanMode(level, player);
        TileLook look = new TileLook(VoxyMapSettings.minimapColors(), VoxyMapSettings.mapOverlays(),
                VoxyMapSettings.overlayShown(MapOverlay.SLIME_CHUNKS) ? MinimapOverlays.slimeSeed(level) : null);
        boolean changedColors = !look.equals(lastLook);
        if (changedColors) {
            TILES.clear();
            lastLook = look;
        }
        boolean changedPosition = centerChunkX != playerChunkX || centerChunkZ != playerChunkZ;
        boolean changedWorld = !dimension.equals(lastDimension) || level != lastLevel;
        boolean changedMode = mode != lastMode;
        boolean movedVertically = mode == MinimapTileCache.ScanMode.CAVE
                && Math.abs(playerY - lastRebuildPlayerY) >= Y_REFRESH_THRESHOLD_BLOCKS;
        long now = System.currentTimeMillis();
        if (changedPosition || changedWorld || changedMode || movedVertically || changedColors) {
            lastDimension = dimension;
            lastLevel = level;
            lastMode = mode;
            lastRebuildPlayerY = playerY;
            rebuild(level, playerChunkX, playerChunkZ, mode, playerY, Refresh.FULL_WINDOW);
        } else if (!DIRTY_TILES.isEmpty() && now >= nextDirtyRefreshAtMillis) {
            nextDirtyRefreshAtMillis = now + DIRTY_REFRESH_MILLIS;
            refreshDirty(level, mode);
        } else if (hasMissingTiles && now >= nextRetryAtMillis) {
            rebuild(level, centerChunkX, centerChunkZ, mode, lastRebuildPlayerY, Refresh.MISSING);
        } else if (now >= nextBackgroundRefreshAtMillis) {
            nextBackgroundRefreshAtMillis = now + BACKGROUND_REFRESH_MILLIS;
            rebuild(level, centerChunkX, centerChunkZ, mode, lastRebuildPlayerY, Refresh.NEXT_BATCH);
        }

        MinimapPlacement.Placement placement = MinimapPlacement.place(g.guiWidth(), g.guiHeight(), displaySize, infoHeight,
                corner, VoxyMapSettings.minimapX(), VoxyMapSettings.minimapY(), topOffset);
        lastPlacement = placement;
        lastPlacementNanos = System.nanoTime();
        int screenX = placement.x();
        int screenY = placement.y();
        float opacity = VoxyMapSettings.minimapOpacity().alpha();
        double brightness = mode == MinimapTileCache.ScanMode.SURFACE && level.dimensionType().hasSkyLight()
                && VoxyMapSettings.minimapNightShading()
                ? MinimapPlacement.nightBrightness(level.getSkyDarken()) : 1.0;
        terrainTint = MapColorMath.scale(((int) Math.round(opacity * 255) << 24) | 0xFFFFFF, brightness);
        backgroundAlpha = opacity;
        View view = new View(screenX, screenY, displaySize, screenX + displaySize / 2, screenY + displaySize / 2,
                scale, Math.cos(rotation), Math.sin(rotation), circle);

        drawBackground(g, view);
        drawTerrain(g, view, blitRadius, rotation);
        g.enableScissor(screenX, screenY, screenX + displaySize, screenY + displaySize);
        drawChunkGrid(g, view, worldRadius);
        drawMarkers(g, minecraft, view, worldRadius, yaw);
        drawCardinalLetters(g, minecraft, view);
        g.disableScissor();
        drawFrame(g, view);
        int barY = placement.infoAbove() ? screenY - infoHeight : screenY + displaySize + 3;
        drawInfoBar(g, minecraft, lines, placement.anchorRight() ? screenX + displaySize + 2 : screenX - 2,
                barY, displaySize + 4, placement.anchorRight());
    }

    public static boolean hudHookFired() {
        return hudHookFired;
    }

    public static int lastDisplaySize() {
        return lastDisplaySize;
    }

    public static void forgetLevel() {
        lastLevel = null;
        TILES.forget();
        MinimapInfo.forgetLevel();
        OreMaskController.forgetModels();
    }

    public static MinimapTileCache.ScanMode scanMode(Level level, Player player) {
        MinimapTileCache.ScanMode autoMode = MinimapTileCache.scanMode(level.dimensionType().hasCeiling(),
                (int) Math.floor(player.getY()),
                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        (int) Math.floor(player.getX()), (int) Math.floor(player.getZ())));
        MinimapTileCache.ScanMode wantedMode = switch (VoxyMapSettings.caveMode()) {
            case AUTO -> autoMode;
            case OFF -> level.dimensionType().hasCeiling() ? autoMode : MinimapTileCache.ScanMode.SURFACE;
            case ALWAYS -> MinimapTileCache.ScanMode.CAVE;
        };
        return ServerRules.caveMapsAllowed(level.dimension().equals(Level.NETHER))
                ? wantedMode : MinimapTileCache.ScanMode.SURFACE;
    }

    public static MinimapPlacement.Placement lastPlacement() {
        return System.nanoTime() - lastPlacementNanos < PLACEMENT_FRESH_NANOS ? lastPlacement : null;
    }

    public static boolean hiddenByScreen(Minecraft minecraft) {
        Screen screen = minecraft.gui.screen();
        if (screen == null || screen instanceof ChatScreen || screen instanceof MinimapLayoutScreen) {
            return false;
        }
        if (screen instanceof WindowMapScreen) {
            return true;
        }
        return switch (VoxyMapSettings.minimapScreenVisibility()) {
            case ALWAYS -> false;
            case HIDE_IN_INVENTORIES -> screen instanceof AbstractContainerScreen<?>;
            case HIDE_IN_ALL_SCREENS -> true;
        };
    }

    static int fitSize(int wanted, int maxWidth, int maxHeight) {
        return Math.max(MIN_DISPLAY_SIZE, Math.min(wanted, Math.min(maxWidth, maxHeight)));
    }

    private static int effectOffset(Player player) {
        boolean beneficial = false;
        boolean harmful = false;
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (!effect.showIcon()) {
                continue;
            }
            if (effect.getEffect().value().isBeneficial()) {
                beneficial = true;
            } else {
                harmful = true;
            }
        }
        return (beneficial ? EFFECT_ROW_HEIGHT : 0) + (harmful ? EFFECT_ROW_HEIGHT : 0);
    }

    private static void drawBackground(GuiGraphicsExtractor g, View view) {
        if (!view.circle()) {
            UiDraw.panel(g, view.screenX() - 3, view.screenY() - 3, view.size() + 6, view.size() + 6, backgroundColor());
            return;
        }
        drawDisc(g, view.centerX(), view.centerY(), view.radius() + 1.0, 0.0, backgroundColor());
    }

    private static void drawFrame(GuiGraphicsExtractor g, View view) {
        if (!view.circle()) {
            UiDraw.outline(g, view.screenX() - 3, view.screenY() - 3, view.size() + 6, view.size() + 6, FRAME_COLOR);
            g.outline(view.screenX() - 1, view.screenY() - 1, view.size() + 2, view.size() + 2, 0x66000000);
            return;
        }
        drawDisc(g, view.centerX(), view.centerY(), view.radius() + 2.0, view.radius() + 1.0, FRAME_COLOR);
    }

    private static void drawDisc(GuiGraphicsExtractor g, int centerX, int centerY, double outer, double inner, int color) {
        int top = (int) Math.floor(centerY - outer);
        int bottom = (int) Math.ceil(centerY + outer);
        for (int y = top; y < bottom; y++) {
            double dy = y + 0.5 - centerY;
            double outerHalf = halfChord(outer, dy);
            if (outerHalf <= 0.0) {
                continue;
            }
            double innerHalf = halfChord(inner, dy);
            int outerLeft = (int) Math.round(centerX - outerHalf);
            int outerRight = (int) Math.round(centerX + outerHalf);
            if (innerHalf <= 0.0) {
                g.fill(outerLeft, y, outerRight, y + 1, color);
                continue;
            }
            int innerLeft = (int) Math.round(centerX - innerHalf);
            int innerRight = (int) Math.round(centerX + innerHalf);
            g.fill(outerLeft, y, innerLeft, y + 1, color);
            g.fill(innerRight, y, outerRight, y + 1, color);
        }
    }

    private static int backgroundColor() {
        return scaleAlpha(BACKGROUND_COLOR, Math.round(backgroundAlpha * 255));
    }

    static double halfChord(double radius, double offset) {
        double squared = radius * radius - offset * offset;
        return squared > 0.0 ? Math.sqrt(squared) : 0.0;
    }

    private record TerrainBlit(int x, int y, float u, float v, int screenSize, int sourceSize, float shiftX,
                               float shiftY) {
    }

    private static void drawTerrain(GuiGraphicsExtractor g, View view, double blitRadius, double rotation) {
        int sourceSize = 2 * (int) Math.ceil(blitRadius) + 2;
        int screenSize = (int) Math.round(sourceSize * view.scale());
        double originX = (double) ((centerChunkX - GRID_CHUNKS / 2) << 4);
        double originZ = (double) ((centerChunkZ - GRID_CHUNKS / 2) << 4);
        double exactU = renderX - sourceSize / 2.0 - originX;
        double exactV = renderZ - sourceSize / 2.0 - originZ;
        float u = (float) Math.floor(exactU);
        float v = (float) Math.floor(exactV);
        TerrainBlit blit = new TerrainBlit(view.centerX() - screenSize / 2, view.centerY() - screenSize / 2, u, v,
                screenSize, sourceSize, (float) ((exactU - u) * view.scale()), (float) ((exactV - v) * view.scale()));
        if (!view.circle()) {
            g.enableScissor(view.screenX(), view.screenY(), view.screenX() + view.size(), view.screenY() + view.size());
            blitTerrain(g, view, blit, rotation);
            g.disableScissor();
            return;
        }
        double radius = view.radius();
        for (int row = 0; row < view.size(); row++) {
            double half = halfChord(radius, row + 0.5 - radius);
            if (half <= 0.0) {
                continue;
            }
            int y = view.screenY() + row;
            g.enableScissor((int) Math.floor(view.centerX() - half), y, (int) Math.ceil(view.centerX() + half), y + 1);
            blitTerrain(g, view, blit, rotation);
            g.disableScissor();
        }
    }

    private static void blitTerrain(GuiGraphicsExtractor g, View view, TerrainBlit blit, double rotation) {
        g.pose().pushMatrix();
        if (rotation != 0.0) {
            g.pose().translate(view.centerX(), view.centerY());
            g.pose().rotate((float) rotation);
            g.pose().translate(-view.centerX(), -view.centerY());
        }
        g.pose().translate(-blit.shiftX(), -blit.shiftY());
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_ID, blit.x(), blit.y(), blit.u(), blit.v(), blit.screenSize(),
                blit.screenSize(), blit.sourceSize(), blit.sourceSize(), TEXTURE_SIZE, TEXTURE_SIZE, terrainTint);
        g.pose().popMatrix();
    }

    private static void drawInfoBar(GuiGraphicsExtractor g, Minecraft minecraft, List<MinimapInfo.Line> lines,
                                    int anchorX, int y, int minWidth, boolean anchorRight) {
        if (lines.isEmpty()) {
            return;
        }
        int width = minWidth;
        for (MinimapInfo.Line line : lines) {
            width = Math.max(width, minecraft.font.width(line.text()) + 8);
        }
        int x = anchorRight ? anchorX - width : anchorX;
        int barHeight = 4 + lines.size() * INFO_LINE_HEIGHT;
        UiDraw.panel(g, x, y, width, barHeight, UiTheme.scaleAlpha(UiTheme.SURFACE, backgroundAlpha));
        UiDraw.outline(g, x, y, width, barHeight, UiTheme.BORDER);
        for (int i = 0; i < lines.size(); i++) {
            MinimapInfo.Line line = lines.get(i);
            g.centeredText(minecraft.font, line.text(), x + width / 2, y + 3 + i * INFO_LINE_HEIGHT, line.color());
        }
    }

    static int coord(double value) {
        return (int) Math.floor(value);
    }

    private static void ensureTexture(Minecraft minecraft) {
        if (texture != null) {
            return;
        }
        texture = new DynamicTexture(() -> "VoxyMap minimap", TEXTURE_SIZE, TEXTURE_SIZE, false);
        minecraft.getTextureManager().register(TEXTURE_ID, texture);
    }

    static int[] centerOutOrder(int gridChunks) {
        int half = gridChunks / 2;
        Integer[] order = new Integer[gridChunks * gridChunks];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Integer.compare(distanceSquared(a, gridChunks, half), distanceSquared(b, gridChunks, half)));
        return Arrays.stream(order).mapToInt(Integer::intValue).toArray();
    }

    private static int distanceSquared(int index, int gridChunks, int half) {
        int cx = index % gridChunks - half;
        int cz = index / gridChunks - half;
        return cx * cx + cz * cz;
    }

    private static int visibleTileCount(double blitRadiusBlocks) {
        int chunkRadius = (int) Math.ceil(blitRadiusBlocks / 16.0) + 1;
        int limit = chunkRadius * chunkRadius * 2;
        int half = GRID_CHUNKS / 2;
        int count = 0;
        for (int index : TILE_ORDER) {
            if (distanceSquared(index, GRID_CHUNKS, half) > limit) {
                break;
            }
            count++;
        }
        return Math.max(1, count);
    }

    private enum Refresh { FULL_WINDOW, MISSING, NEXT_BATCH }

    public static void blockChanged(int blockX, int blockZ) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        markDirty(chunkX, chunkZ);
        if ((blockX & 15) == 15) {
            markDirty(chunkX + 1, chunkZ);
        }
        if ((blockZ & 15) == 15) {
            markDirty(chunkX, chunkZ + 1);
        }
    }

    public static void markDirty(int chunkX, int chunkZ) {
        if (texture == null || Math.abs(chunkX - centerChunkX) > GRID_CHUNKS / 2
                || Math.abs(chunkZ - centerChunkZ) > GRID_CHUNKS / 2) {
            return;
        }
        DIRTY_TILES.add(MinimapTileCache.key(chunkX, chunkZ));
    }

    private static void refreshDirty(Level level, MinimapTileCache.ScanMode mode) {
        NativeImage pixels = texture.getPixels();
        int half = GRID_CHUNKS / 2;
        int tileSize = MinimapTileCache.TILE_SIZE;
        int budget = DIRTY_TILES_PER_FRAME;
        boolean painted = false;
        Iterator<Long> iterator = DIRTY_TILES.iterator();
        while (iterator.hasNext() && budget > 0) {
            long key = iterator.next();
            iterator.remove();
            int cx = MinimapTileCache.keyX(key) - centerChunkX;
            int cz = MinimapTileCache.keyZ(key) - centerChunkZ;
            if (Math.abs(cx) > half || Math.abs(cz) > half) {
                continue;
            }
            int[] tile = TILES.tileFor(level, cx + centerChunkX, cz + centerChunkZ, mode, lastRebuildPlayerY, true);
            if (tile == null) {
                continue;
            }
            budget--;
            missingMask[(cz + half) * GRID_CHUNKS + cx + half] = false;
            paintTile(pixels, (cx + half) * tileSize, (cz + half) * tileSize, tileSize, tile);
            painted = true;
        }
        if (painted) {
            texture.upload();
        }
    }

    private static void rebuild(Level level, int chunkX, int chunkZ, MinimapTileCache.ScanMode mode, int playerY,
                                Refresh refresh) {
        centerChunkX = chunkX;
        centerChunkZ = chunkZ;
        NativeImage pixels = texture.getPixels();
        int half = GRID_CHUNKS / 2;
        int tileSize = MinimapTileCache.TILE_SIZE;
        int batchStart = refreshCursor % visibleTileCount;
        if (refresh == Refresh.NEXT_BATCH) {
            refreshCursor = (batchStart + BACKGROUND_REFRESH_TILES) % visibleTileCount;
        }
        int budget = TILE_COMPUTE_BUDGET;
        boolean deferred = false;
        boolean painted = false;
        for (int position = 0; position < TILE_ORDER.length; position++) {
            int index = TILE_ORDER[position];
            int cx = index % GRID_CHUNKS - half;
            int cz = index / GRID_CHUNKS - half;
            boolean isPlayerChunk = cx == 0 && cz == 0;
            boolean inBatch = position < visibleTileCount
                    && Math.floorMod(position - batchStart, visibleTileCount) < BACKGROUND_REFRESH_TILES;
            boolean selected = switch (refresh) {
                case FULL_WINDOW -> true;
                case MISSING -> isPlayerChunk || missingMask[index];
                case NEXT_BATCH -> isPlayerChunk || inBatch;
            };
            if (!selected) {
                continue;
            }
            int tileX = chunkX + cx;
            int tileZ = chunkZ + cz;
            boolean force = isPlayerChunk || refresh == Refresh.NEXT_BATCH;
            boolean wasMissing = missingMask[index];
            boolean loaded = level.getChunk(tileX, tileZ, ChunkStatus.FULL, false) != null;
            boolean needsCompute = loaded && (force || !TILES.isFreshFor(tileX, tileZ, mode, playerY));
            int[] tile;
            if (needsCompute && budget <= 0) {
                tile = TILES.cached(tileX, tileZ);
                deferred = true;
                missingMask[index] = true;
            } else {
                if (needsCompute) {
                    budget--;
                }
                tile = TILES.tileFor(level, tileX, tileZ, mode, playerY, force);
                missingMask[index] = tile == null;
            }
            if (refresh == Refresh.MISSING && wasMissing && tile == null) {
                continue;
            }
            paintTile(pixels, (cx + half) * tileSize, (cz + half) * tileSize, tileSize, tile);
            painted = true;
        }
        boolean missing = false;
        for (boolean tileMissing : missingMask) {
            missing |= tileMissing;
        }
        if (refresh == Refresh.FULL_WINDOW) {
            TILES.retainWithin(chunkX, chunkZ, TILE_RETENTION_RADIUS_CHUNKS);
        }
        if (refresh != Refresh.NEXT_BATCH || deferred) {
            nextRetryAtMillis = deferred ? 0L : System.currentTimeMillis() + MISSING_TILE_RETRY_MILLIS;
        }
        hasMissingTiles = missing;
        if (painted) {
            texture.upload();
        }
    }

    private static void paintTile(NativeImage pixels, int baseX, int baseY, int tileSize, int[] tile) {
        for (int ty = 0; ty < tileSize; ty++) {
            for (int tx = 0; tx < tileSize; tx++) {
                int argb;
                if (tile != null) {
                    argb = tile[ty * tileSize + tx];
                } else {
                    argb = ((tx + ty) & 3) == 0 ? UNLOADED_CHUNK_HINT_COLOR : 0;
                }
                pixels.setPixelABGR(baseX + tx, baseY + ty, argbToAbgr(argb));
            }
        }
    }

    static int argbToAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int gr = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (gr << 8) | r;
    }

    private static void drawMarkers(GuiGraphicsExtractor g, Minecraft minecraft, View view, double worldRadius,
                                    float yaw) {
        Player player = minecraft.player;
        boolean heads = MinimapHeads.shown(minecraft);

        boolean radar = ServerRules.radarAllowed();
        List<MobMarkerCache.MobSnapshot> mobs = radar ? MobMarkerCache.snapshot() : List.of();
        for (RadarCategory category : RadarCategory.MARKER_DRAW_ORDER) {
            if (!VoxyMapSettings.radarShows(category)) continue;
            int radius = category == RadarCategory.ITEMS ? 0 : MOB_MARKER_RADIUS;
            for (MobMarkerCache.MobSnapshot mob : mobs) {
                if (mob.category() != category) continue;
                double dx = mob.xAt(partialTick) - renderX;
                double dz = mob.zAt(partialTick) - renderZ;
                double ex = view.exactX(dx, dz);
                double ey = view.exactY(dx, dz);
                int px = (int) Math.floor(ex);
                int py = (int) Math.floor(ey);
                if (!view.contains(px, py)) continue;
                int alpha = verticalAlpha(mob.y(), renderY);
                g.pose().pushMatrix();
                g.pose().translate((float) (ex - px), (float) (ey - py));
                int extent;
                if (heads && mob.face() != null) {
                    extent = drawMobHead(g, mob.face(), px, py, outlineFor(category), alpha);
                } else {
                    drawDot(g, px, py, radius, scaleAlpha(category.color(), alpha));
                    extent = radius;
                }
                int hint = heightHint(mob.y() - renderY);
                if (category != RadarCategory.ITEMS && hint != 0) {
                    drawHeightArrow(g, px, hint > 0 ? py - extent - 3 : py + extent + 3, hint, alpha);
                }
                g.pose().popMatrix();
            }
        }

        boolean showPlayers = radar && VoxyMapSettings.radarShows(RadarCategory.PLAYERS);
        for (Player other : minecraft.level.players()) {
            if (other == player || !showPlayers || VoxyMapSettings.playerHidden(other.getUUID())
                    || other.isInvisibleTo(player)) continue;
            double otherX = Mth.lerp(partialTick, other.xo, other.getX());
            double otherY = Mth.lerp(partialTick, other.yo, other.getY());
            double otherZ = Mth.lerp(partialTick, other.zo, other.getZ());
            double dx = otherX - renderX;
            double dz = otherZ - renderZ;
            double ex = view.exactX(dx, dz);
            double ey = view.exactY(dx, dz);
            int px = (int) Math.floor(ex);
            int py = (int) Math.floor(ey);
            if (!view.contains(px, py)) continue;
            int alpha = verticalAlpha(otherY, renderY);
            g.pose().pushMatrix();
            g.pose().translate((float) (ex - px), (float) (ey - py));
            drawPlayerMarker(g, minecraft, other, px, py, alpha, heads);
            int hint = heightHint(otherY - renderY);
            if (hint != 0) {
                drawHeightArrow(g, px + (heads ? PLAYER_HEAD_SIZE / 2 + 1 : MARKER_RADIUS) + 4, py, hint, alpha);
            }
            g.pose().popMatrix();
        }

        String worldId = currentWorldId(minecraft);
        if (worldId != null && VoxyMapSettings.waypointsEnabled()) {
            for (DimensionLinks.Foreign foreign : DimensionLinks.foreignWaypoints(minecraft)) {
                if (WaypointPrefs.shownOn(foreign.original().id(), WaypointDisplay.Surface.MINIMAP)) {
                    drawWaypoint(g, minecraft, view, foreign.local(), WaypointIcons.OTHER_DIMENSION_ALPHA);
                }
            }
            for (WaypointManager.Waypoint waypoint : WaypointManager.visibleForWorld(worldId, WaypointDisplay.Surface.MINIMAP)) {
                drawWaypoint(g, minecraft, view, waypoint, 0xFF);
            }
        }

        for (LitematicaPlusBridge.Placement placement : VoxyMapSettings.schematicsEnabled()
                ? LitematicaPlusBridge.placements() : List.<LitematicaPlusBridge.Placement>of()) {
            if (!placement.shown()) {
                continue;
            }
            int color = placement.selected() ? 0xFF00E5FF : 0xCC2FC7FF;
            double x0 = placement.minX();
            double z0 = placement.minZ();
            double x1 = placement.maxX();
            double z1 = placement.maxZ();
            double reach = worldRadius * 1.5;
            if (x1 < renderX - reach || x0 > renderX + reach || z1 < renderZ - reach || z0 > renderZ + reach) {
                continue;
            }
            x0 = Math.max(x0, renderX - reach);
            x1 = Math.min(x1, renderX + reach);
            z0 = Math.max(z0, renderZ - reach);
            z1 = Math.min(z1, renderZ + reach);
            drawGridLine(g, view, x0, z0, x1, z0, color);
            drawGridLine(g, view, x1, z0, x1, z1, color);
            drawGridLine(g, view, x1, z1, x0, z1, color);
            drawGridLine(g, view, x0, z1, x0, z0, color);
        }

        if (!view.rotated()) {
            drawFacingIndicator(g, view.centerX(), view.centerY(), yaw);
        } else {
            drawFacingIndicator(g, view.centerX(), view.centerY(), 0.0, -1.0);
        }
        drawDot(g, view.centerX(), view.centerY(), MARKER_RADIUS + 1, 0xFF0A0A12);
        drawDot(g, view.centerX(), view.centerY(), MARKER_RADIUS, 0xFF55E7FF);
    }

    private static void drawWaypoint(GuiGraphicsExtractor g, Minecraft minecraft, View view,
                                     WaypointManager.Waypoint waypoint, int alpha) {
        double dx = rotatedX(waypoint.x() - renderX, waypoint.z() - renderZ, view.cosA(), view.sinA())
                * view.scale();
        double dz = rotatedZ(waypoint.x() - renderX, waypoint.z() - renderZ, view.cosA(), view.sinA())
                * view.scale();
        double ex = view.centerX() + dx;
        double ey = view.centerY() + dz;
        int px = (int) Math.floor(ex);
        int py = (int) Math.floor(ey);
        if (view.contains(px, py)) {
            g.pose().pushMatrix();
            g.pose().translate((float) (ex - px), (float) (ey - py));
            WaypointIcons.drawCentered(g, minecraft.font, px, py, WAYPOINT_ICON_SIZE, waypoint.color(),
                    waypoint.initial(), alpha);
            g.pose().popMatrix();
        } else {
            drawOffscreenWaypoint(g, view, view.radius() - OFFSCREEN_EDGE_INSET, dx, dz,
                    scaleAlpha(0xFF000000 | waypoint.color(), alpha));
        }
    }

    static int heightHint(double dy) {
        if (dy > HEIGHT_HINT_BLOCKS) return 1;
        if (dy < -HEIGHT_HINT_BLOCKS) return -1;
        return 0;
    }

    private static void drawHeightArrow(GuiGraphicsExtractor g, int x, int y, int direction, int alpha) {
        drawChevron(g, x + 1, y + 1, direction, scaleAlpha(0xCC0A0A12, alpha));
        drawChevron(g, x, y, direction, scaleAlpha(0xFFFFFFFF, alpha));
    }

    private static void drawChevron(GuiGraphicsExtractor g, int x, int y, int direction, int color) {
        int tipY = direction > 0 ? y - 1 : y;
        int baseY = direction > 0 ? y : y - 1;
        g.fill(x, tipY, x + 1, tipY + 1, color);
        g.fill(x - 1, baseY, x + 2, baseY + 1, color);
    }

    static int verticalAlpha(double markerY, double playerY) {
        double dy = Math.abs(markerY - playerY);
        if (dy <= NEAR_Y_BLOCKS) return 0xFF;
        if (dy >= FAR_Y_BLOCKS) return FAR_Y_ALPHA;
        double t = (dy - NEAR_Y_BLOCKS) / (FAR_Y_BLOCKS - NEAR_Y_BLOCKS);
        return (int) Math.round(0xFF - t * (0xFF - FAR_Y_ALPHA));
    }

    static int scaleAlpha(int argb, int alpha) {
        int baseAlpha = (argb >>> 24) & 0xFF;
        int scaled = (baseAlpha * alpha) / 0xFF;
        return (scaled << 24) | (argb & 0x00FFFFFF);
    }

    static double rotatedX(double dx, double dz, double cosA, double sinA) {
        return dx * cosA - dz * sinA;
    }

    static double rotatedZ(double dx, double dz, double cosA, double sinA) {
        return dx * sinA + dz * cosA;
    }

    private static void drawChunkGrid(GuiGraphicsExtractor g, View view, double worldRadius) {
        if (!VoxyMapSettings.chunkGridEnabled()) {
            return;
        }
        double radius = worldRadius * (view.rotated() ? 1.4143 : 1.0) + 2.0;
        double minX = renderX - radius, maxX = renderX + radius;
        double minZ = renderZ - radius, maxZ = renderZ + radius;
        int color = 0x2AFFFFFF;

        int startGx = (int) (Math.floor(minX / 16.0) * 16);
        for (int gx = startGx; gx <= maxX; gx += 16) {
            drawGridLine(g, view, gx, minZ, gx, maxZ, color);
        }
        int startGz = (int) (Math.floor(minZ / 16.0) * 16);
        for (int gz = startGz; gz <= maxZ; gz += 16) {
            drawGridLine(g, view, minX, gz, maxX, gz, color);
        }
    }

    private static void drawGridLine(GuiGraphicsExtractor g, View view,
                                      double worldX1, double worldZ1, double worldX2, double worldZ2, int color) {
        double dx1 = worldX1 - renderX, dz1 = worldZ1 - renderZ;
        double dx2 = worldX2 - renderX, dz2 = worldZ2 - renderZ;
        double x1 = view.centerX() + rotatedX(dx1, dz1, view.cosA(), view.sinA()) * view.scale();
        double y1 = view.centerY() + rotatedZ(dx1, dz1, view.cosA(), view.sinA()) * view.scale();
        double x2 = view.centerX() + rotatedX(dx2, dz2, view.cosA(), view.sinA()) * view.scale();
        double y2 = view.centerY() + rotatedZ(dx2, dz2, view.cosA(), view.sinA()) * view.scale();
        if (view.circle()) {
            double[] clipped = clipToCircle(x1, y1, x2, y2, view.centerX(), view.centerY(), view.radius() - 1.0);
            if (clipped == null) {
                return;
            }
            x1 = clipped[0];
            y1 = clipped[1];
            x2 = clipped[2];
            y2 = clipped[3];
        }
        if (!view.rotated()) {
            double left = Math.min(x1, x2);
            double top = Math.min(y1, y2);
            int baseX = (int) Math.floor(left);
            int baseY = (int) Math.floor(top);
            g.pose().pushMatrix();
            g.pose().translate((float) (left - baseX), (float) (top - baseY));
            g.fill(baseX, baseY, baseX + Math.max(1, (int) Math.round(Math.abs(x2 - x1))),
                    baseY + Math.max(1, (int) Math.round(Math.abs(y2 - y1))), color);
            g.pose().popMatrix();
        } else {
            drawLine(g, (int) Math.round(x1), (int) Math.round(y1), (int) Math.round(x2), (int) Math.round(y2), color);
        }
    }

    static double[] clipToCircle(double x1, double y1, double x2, double y2, double centerX, double centerY,
                                 double radius) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double fx = x1 - centerX;
        double fy = y1 - centerY;
        double a = dx * dx + dy * dy;
        double c = fx * fx + fy * fy - radius * radius;
        if (a < 1e-12) {
            return c <= 0.0 ? new double[]{x1, y1, x2, y2} : null;
        }
        double b = 2.0 * (fx * dx + fy * dy);
        double discriminant = b * b - 4.0 * a * c;
        if (discriminant < 0.0) {
            return null;
        }
        double root = Math.sqrt(discriminant);
        double t0 = Math.max(0.0, (-b - root) / (2.0 * a));
        double t1 = Math.min(1.0, (-b + root) / (2.0 * a));
        if (t0 > t1) {
            return null;
        }
        return new double[]{x1 + dx * t0, y1 + dy * t0, x1 + dx * t1, y1 + dy * t1};
    }

    private static void drawCardinalLetters(GuiGraphicsExtractor g, Minecraft minecraft, View view) {
        double radius = view.radius() - 8.0;
        drawCardinalLetter(g, minecraft, view, radius, 0.0, "E", UiTheme.TEXT_SECONDARY);
        drawCardinalLetter(g, minecraft, view, 0.0, radius, "S", UiTheme.TEXT_SECONDARY);
        drawCardinalLetter(g, minecraft, view, -radius, 0.0, "W", UiTheme.TEXT_SECONDARY);
        int x = view.centerX() + (int) Math.round(rotatedX(0.0, -radius, view.cosA(), view.sinA()));
        int y = view.centerY() + (int) Math.round(rotatedZ(0.0, -radius, view.cosA(), view.sinA()));
        UiDraw.panel(g, x - 5, y - 6, 11, 11, UiTheme.DANGER_FILL);
        g.text(minecraft.font, "N", x - minecraft.font.width("N") / 2 + 1, y - 4, UiTheme.TEXT);
    }

    private static void drawCardinalLetter(GuiGraphicsExtractor g, Minecraft minecraft, View view,
                                            double baseX, double baseZ, String letter, int color) {
        int x = view.centerX() + (int) Math.round(rotatedX(baseX, baseZ, view.cosA(), view.sinA()));
        int y = view.centerY() + (int) Math.round(rotatedZ(baseX, baseZ, view.cosA(), view.sinA()));
        int textWidth = minecraft.font.width(letter);
        g.text(minecraft.font, letter, x - textWidth / 2, y - 4, color);
    }

    static int outlineFor(RadarCategory category) {
        return switch (category) {
            case HOSTILE, NEUTRAL -> category.color();
            default -> PASSIVE_OUTLINE;
        };
    }

    static int[] iconSize(int width, int height, int max) {
        double scale = Math.min(1.0, max / (double) Math.max(width, height));
        return new int[]{Math.max(1, (int) Math.round(width * scale)), Math.max(1, (int) Math.round(height * scale))};
    }

    private static int drawMobHead(GuiGraphicsExtractor g, MobHeadIcons.Face face, int px, int py, int outlineColor,
                                   int alpha) {
        int[] size = iconSize(face.width(), face.height(), MOB_ICON_MAX);
        int x = px - size[0] / 2;
        int y = py - size[1] / 2;
        int textureWidth = face.width() * 2;
        int outline = scaleAlpha(outlineColor, alpha);
        for (int[] offset : OUTLINE_OFFSETS) {
            g.blit(RenderPipelines.GUI_TEXTURED, face.icon(), x + offset[0], y + offset[1], face.width(), 0.0f,
                    size[0], size[1], face.width(), face.height(), textureWidth, face.height(), outline);
        }
        g.blit(RenderPipelines.GUI_TEXTURED, face.icon(), x, y, 0.0f, 0.0f, size[0], size[1],
                face.width(), face.height(), textureWidth, face.height(), scaleAlpha(0xFFFFFFFF, alpha));
        return size[1] / 2 + 1;
    }

    static void drawPlayerMarker(GuiGraphicsExtractor g, Minecraft minecraft, Player other, int px, int py, int alpha,
                                         boolean heads) {
        Identifier skin = heads ? PlayerHeadIcon.resolveSkinTexture(minecraft, other.getUUID()) : null;
        if (skin != null) {
            drawDot(g, px, py, MARKER_RADIUS + 2, scaleAlpha(0xEE0A0A12, alpha));
            PlayerHeadIcon.draw(g, skin, px - PLAYER_HEAD_SIZE / 2, py - PLAYER_HEAD_SIZE / 2, PLAYER_HEAD_SIZE,
                    scaleAlpha(0xFFFFFFFF, alpha));
        } else {
            drawDot(g, px, py, MARKER_RADIUS, scaleAlpha(0xFFFF62D4, alpha));
        }

        String name = other.getName().getString();
        int nameWidth = minecraft.font.width(name);
        int nameX = px - nameWidth / 2;
        int nameY = py - MARKER_RADIUS - 11;
        g.fill(nameX - 2, nameY - 1, nameX + nameWidth + 2, nameY + 8, scaleAlpha(0x99030914, alpha));
        g.text(minecraft.font, name, nameX, nameY, scaleAlpha(0xFFFFFFFF, alpha));
    }

    private static void drawOffscreenWaypoint(GuiGraphicsExtractor g, View view, double edge, double dx, double dz,
                                               int color) {
        double distance = Math.hypot(dx, dz);
        if (distance < 0.001) {
            return;
        }
        int ex;
        int ey;
        double dirX = dx / distance;
        double dirZ = dz / distance;
        if (view.circle()) {
            ex = view.centerX() + (int) Math.round(dirX * edge);
            ey = view.centerY() + (int) Math.round(dirZ * edge);
        } else {
            SquareEdgePoint point = squareEdgeIntersection(dx, dz, edge);
            ex = view.centerX() + point.x();
            ey = view.centerY() + point.y();
        }
        int tailX = ex + (int) Math.round(dirX * 6.0);
        int tailY = ey + (int) Math.round(dirZ * 6.0);
        drawLine(g, ex, ey, tailX, tailY, color);
        drawDot(g, ex, ey, MARKER_RADIUS + 1, 0xEE0A0A12);
        drawDot(g, ex, ey, MARKER_RADIUS, color);
    }

    static SquareEdgePoint squareEdgeIntersection(double dx, double dz, double edgeHalf) {
        double distance = Math.hypot(dx, dz);
        double dirX = dx / distance;
        double dirZ = dz / distance;
        double t = Math.min(
                dirX == 0.0 ? Double.MAX_VALUE : edgeHalf / Math.abs(dirX),
                dirZ == 0.0 ? Double.MAX_VALUE : edgeHalf / Math.abs(dirZ));
        return new SquareEdgePoint((int) Math.round(dirX * t), (int) Math.round(dirZ * t), dirX, dirZ);
    }

    record SquareEdgePoint(int x, int y, double dirX, double dirZ) {
    }

    static void drawFacingIndicator(GuiGraphicsExtractor g, int centerX, int centerY, float yawDegrees) {
        drawFacingIndicator(g, centerX, centerY,
                PlayerViewConeGeometry.directionX(yawDegrees), PlayerViewConeGeometry.directionZ(yawDegrees));
    }

    private static void drawFacingIndicator(GuiGraphicsExtractor g, int centerX, int centerY, double dirX, double dirZ) {
        double halfAngle = Math.toRadians(24.0);
        double cos = Math.cos(halfAngle);
        double sin = Math.sin(halfAngle);
        double leftX = dirX * cos - dirZ * sin;
        double leftZ = dirX * sin + dirZ * cos;
        double rightX = dirX * cos + dirZ * sin;
        double rightZ = -dirX * sin + dirZ * cos;

        double length = 11.0;
        int lx = centerX + (int) Math.round(leftX * length);
        int lz = centerY + (int) Math.round(leftZ * length);
        int rx = centerX + (int) Math.round(rightX * length);
        int rz = centerY + (int) Math.round(rightZ * length);

        drawLine(g, centerX, centerY, lx, lz, 0xCC55E7FF);
        drawLine(g, centerX, centerY, rx, rz, 0xCC55E7FF);
        drawLine(g, lx, lz, rx, rz, 0x8855E7FF);
    }

    static void drawLine(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.hypot(dx, dy);
        if (length < 1.0) {
            g.fill(x1, y1, x1 + 1, y1 + 1, color);
            return;
        }
        float angle = (float) Math.atan2(dy, dx);
        g.pose().pushMatrix();
        g.pose().translate((float) x1, (float) y1);
        g.pose().rotate(angle);
        g.fill(0, 0, (int) Math.ceil(length) + 1, 1, color);
        g.pose().popMatrix();
    }

    static void drawDot(GuiGraphicsExtractor g, int centerX, int centerY, int radius, int color) {
        g.fill(centerX - radius, centerY - radius, centerX + radius + 1, centerY + radius + 1, color);
    }

    public static String currentWorldId(Minecraft minecraft) {
        return WaypointWorlds.effective(currentVoxyWorldId(minecraft));
    }

    public static String currentVoxyWorldId(Minecraft minecraft) {
        if (minecraft.level == null) return null;
        WorldIdentifier identifier = WorldIdentifier.of(minecraft.level);
        return identifier == null ? null : identifier.getWorldId();
    }
}
