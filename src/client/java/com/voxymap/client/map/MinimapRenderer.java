package com.voxymap.client.map;

import java.util.List;
import com.mojang.blaze3d.platform.NativeImage;
import com.voxymap.client.integration.LitematicaPlusBridge;
import com.voxymap.client.VoxyMapClient;
import com.voxymap.client.gui.PlayerHeadIcon;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

public final class MinimapRenderer {

    private static final int GRID_CHUNKS = 17;
    private static final int TEXTURE_SIZE = GRID_CHUNKS * MinimapTileCache.TILE_SIZE;
    private static final int DISPLAY_SIZE = 128;
    private static final float ZOOM_SCALE = 1.6f;
    private static final int MARGIN = 8;
    private static final int MARKER_RADIUS = 2;
    private static final int MOB_MARKER_RADIUS = 1;
    private static final int PLAYER_HEAD_SIZE = 8;
    private static final int MOB_HEAD_SIZE = 8;
    private static final int FACE_UV_GRID = 4096;
    private static final double OFFSCREEN_EDGE_INSET = 9.0;
    private static final int COORD_BAR_HEIGHT = 14;
    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath("voxymap", "minimap_dynamic");

    private static final double MAX_BLIT_RADIUS = (GRID_CHUNKS / 2) * 16.0 - 4.0;

    private static final int Y_REFRESH_THRESHOLD_BLOCKS = MinimapTileCache.Y_TOLERANCE_BLOCKS;

    private static final int TILE_RETENTION_RADIUS_CHUNKS = GRID_CHUNKS;

    private static final long MISSING_TILE_RETRY_MILLIS = 500L;

    private static final long BACKGROUND_REFRESH_MILLIS = 250L;

    private static final int BACKGROUND_REFRESH_TILES = 12;

    private static final double NEAR_Y_BLOCKS = 24.0;
    private static final double FAR_Y_BLOCKS = 64.0;
    private static final int FAR_Y_ALPHA = 0x50;

    private static final int UNLOADED_CHUNK_HINT_COLOR = 0x40FFB000;

    private static final MinimapTileCache TILES = new MinimapTileCache();
    private static DynamicTexture texture;
    private static int centerChunkX = Integer.MIN_VALUE;
    private static int centerChunkZ = Integer.MIN_VALUE;
    private static int lastRebuildPlayerY = Integer.MIN_VALUE;
    private static ResourceKey<Level> lastDimension = null;

    private static Level lastLevel = null;

    private static boolean hasMissingTiles = false;
    private static long nextRetryAtMillis = 0L;
    private static MinimapTileCache.ScanMode lastMode = null;
    private static int refreshCursor = 0;
    private static long nextBackgroundRefreshAtMillis = 0L;

    private static final boolean[] missingMask = new boolean[GRID_CHUNKS * GRID_CHUNKS];

    private static volatile boolean hudHookFired = false;

    private MinimapRenderer() {
    }

    public static void extract(GuiGraphicsExtractor g, Minecraft minecraft) {
        hudHookFired = true;
        if (!VoxyMapSettings.minimapEnabled() || minecraft.level == null || minecraft.player == null
                || VoxyMapCameraController.isActive()
                || minecraft.gui.screen() instanceof com.voxymap.client.gui.WindowMapScreen) {
            return;
        }

        Player player = minecraft.player;
        Level level = minecraft.level;
        int playerChunkX = Math.floorDiv((int) Math.floor(player.getX()), 16);
        int playerChunkZ = Math.floorDiv((int) Math.floor(player.getZ()), 16);
        int playerY = (int) Math.floor(player.getY());
        ResourceKey<Level> dimension = level.dimension();

        ensureTexture(minecraft);

        MinimapTileCache.ScanMode mode = MinimapTileCache.scanMode(level.dimensionType().hasCeiling(), playerY,
                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        (int) Math.floor(player.getX()), (int) Math.floor(player.getZ())));
        boolean changedPosition = centerChunkX != playerChunkX || centerChunkZ != playerChunkZ;
        boolean changedWorld = !dimension.equals(lastDimension) || level != lastLevel;
        boolean changedMode = mode != lastMode;
        boolean movedVertically = mode == MinimapTileCache.ScanMode.CAVE
                && Math.abs(playerY - lastRebuildPlayerY) >= Y_REFRESH_THRESHOLD_BLOCKS;
        long now = System.currentTimeMillis();
        if (changedPosition || changedWorld || changedMode || movedVertically) {
            lastDimension = dimension;
            lastLevel = level;
            lastMode = mode;
            lastRebuildPlayerY = playerY;
            rebuild(level, playerChunkX, playerChunkZ, mode, playerY, Refresh.FULL_WINDOW);
        } else if (hasMissingTiles && now >= nextRetryAtMillis) {
            rebuild(level, centerChunkX, centerChunkZ, mode, lastRebuildPlayerY, Refresh.MISSING);
        } else if (now >= nextBackgroundRefreshAtMillis) {
            nextBackgroundRefreshAtMillis = now + BACKGROUND_REFRESH_MILLIS;
            rebuild(level, centerChunkX, centerChunkZ, mode, lastRebuildPlayerY, Refresh.NEXT_BATCH);
        }

        int displaySize = VoxyMapClient.isMinimapZoomHeld() ? Math.round(DISPLAY_SIZE * ZOOM_SCALE) : DISPLAY_SIZE;
        double worldRadius = displaySize / 2.0;
        int screenX = MARGIN;
        int screenY = MARGIN;
        int centerX = screenX + displaySize / 2;
        int centerY = screenY + displaySize / 2;

        double rotation = 0.0;
        if (VoxyMapSettings.minimapRotates()) {
            double dirX = PlayerViewConeGeometry.directionX(player.getYRot());
            double dirZ = PlayerViewConeGeometry.directionZ(player.getYRot());
            rotation = Math.atan2(-dirX, -dirZ);
        }
        double cosA = Math.cos(rotation);
        double sinA = Math.sin(rotation);

        double originX = (double) ((centerChunkX - GRID_CHUNKS / 2) << 4);
        double originZ = (double) ((centerChunkZ - GRID_CHUNKS / 2) << 4);

        g.fill(screenX - 2, screenY - 2, screenX + displaySize + 2, screenY + displaySize + 2, 0xAA030914);
        g.enableScissor(screenX, screenY, screenX + displaySize, screenY + displaySize);

        double diagonalFactor = rotation == 0.0 ? 1.0 : 1.4143;
        double blitRadius = Math.min(worldRadius * diagonalFactor, MAX_BLIT_RADIUS);
        int blitSize = (int) Math.ceil(blitRadius * 2.0);
        float u = (float) (player.getX() - blitRadius - originX);
        float v = (float) (player.getZ() - blitRadius - originZ);
        int blitX = centerX - blitSize / 2;
        int blitY = centerY - blitSize / 2;
        if (rotation != 0.0) {
            g.pose().pushMatrix();
            g.pose().translate(centerX, centerY);
            g.pose().rotate((float) rotation);
            g.pose().translate(-centerX, -centerY);
        }
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_ID, blitX, blitY, u, v, blitSize, blitSize,
                blitSize, blitSize, TEXTURE_SIZE, TEXTURE_SIZE, 0xFFFFFFFF);
        if (rotation != 0.0) {
            g.pose().popMatrix();
        }

        drawChunkGrid(g, player, centerX, centerY, displaySize, cosA, sinA);
        drawMarkers(g, minecraft, screenX, screenY, displaySize, centerX, centerY, cosA, sinA);
        drawCardinalLetters(g, minecraft, centerX, centerY, displaySize, cosA, sinA);
        g.disableScissor();
        g.outline(screenX - 2, screenY - 2, displaySize + 4, displaySize + 4, 0xFF3BA4FF);
        drawCoordinateBar(g, minecraft, screenX - 2, screenY + displaySize + 3, displaySize + 4);
    }

    public static boolean hudHookFired() {
        return hudHookFired;
    }

    private static void drawCoordinateBar(GuiGraphicsExtractor g, Minecraft minecraft, int x, int y, int width) {
        Player player = minecraft.player;
        g.fill(x, y, x + width, y + COORD_BAR_HEIGHT, 0xCC030914);
        g.fill(x, y, x + width, y + 1, 0xFF3BA4FF);
        String text = coord(player.getX()) + ", " + coord(player.getY()) + ", " + coord(player.getZ());
        g.centeredText(minecraft.font, text, x + width / 2, y + 3, 0xFFDFF8FF);
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

    private enum Refresh { FULL_WINDOW, MISSING, NEXT_BATCH }

    private static void rebuild(Level level, int chunkX, int chunkZ, MinimapTileCache.ScanMode mode, int playerY,
                                Refresh refresh) {
        centerChunkX = chunkX;
        centerChunkZ = chunkZ;
        NativeImage pixels = texture.getPixels();
        int half = GRID_CHUNKS / 2;
        int tileSize = MinimapTileCache.TILE_SIZE;
        int tileCount = GRID_CHUNKS * GRID_CHUNKS;
        int batchStart = refreshCursor;
        if (refresh == Refresh.NEXT_BATCH) {
            refreshCursor = (refreshCursor + BACKGROUND_REFRESH_TILES) % tileCount;
        }
        for (int index = 0; index < tileCount; index++) {
            int cx = index % GRID_CHUNKS - half;
            int cz = index / GRID_CHUNKS - half;
            boolean isPlayerChunk = cx == 0 && cz == 0;
            boolean inBatch = Math.floorMod(index - batchStart, tileCount) < BACKGROUND_REFRESH_TILES;
            boolean selected = switch (refresh) {
                case FULL_WINDOW -> true;
                case MISSING -> isPlayerChunk || missingMask[index];
                case NEXT_BATCH -> isPlayerChunk || inBatch;
            };
            if (!selected) {
                continue;
            }
            boolean force = isPlayerChunk || refresh == Refresh.NEXT_BATCH;
            int[] tile = TILES.tileFor(level, chunkX + cx, chunkZ + cz, mode, playerY, force);
            paintTile(pixels, (cx + half) * tileSize, (cz + half) * tileSize, tileSize, tile);
            missingMask[index] = tile == null;
        }
        boolean missing = false;
        for (boolean tileMissing : missingMask) {
            missing |= tileMissing;
        }
        if (refresh == Refresh.FULL_WINDOW) {
            TILES.retainWithin(chunkX, chunkZ, TILE_RETENTION_RADIUS_CHUNKS);
        }
        if (refresh != Refresh.NEXT_BATCH) {
            nextRetryAtMillis = System.currentTimeMillis() + MISSING_TILE_RETRY_MILLIS;
        }
        hasMissingTiles = missing;
        texture.upload();
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

    private static void drawMarkers(GuiGraphicsExtractor g, Minecraft minecraft, int screenX, int screenY,
                                     int displaySize, int centerX, int centerY, double cosA, double sinA) {
        Player player = minecraft.player;
        boolean heads = MinimapHeads.shown(minecraft);

        if (VoxyMapSettings.mobsEnabled()) {
            for (MobMarkerCache.MobSnapshot mob : MobMarkerCache.snapshot()) {
                double dx = mob.x() - player.getX();
                double dz = mob.z() - player.getZ();
                int px = centerX + (int) Math.round(rotatedX(dx, dz, cosA, sinA));
                int py = centerY + (int) Math.round(rotatedZ(dx, dz, cosA, sinA));
                if (px < screenX || px >= screenX + displaySize || py < screenY || py >= screenY + displaySize) continue;
                int baseColor = mob.hostile() ? 0xFFFF5555 : 0xFF7CFC6E;
                int alpha = verticalAlpha(mob.y(), player.getY());
                if (heads && mob.face() != null) {
                    drawMobHead(g, mob.face(), px, py, scaleAlpha(baseColor, alpha), alpha);
                } else {
                    drawDot(g, px, py, MOB_MARKER_RADIUS, scaleAlpha(baseColor, alpha));
                }
            }
        }

        for (Player other : minecraft.level.players()) {
            if (other == player) continue;
            double dx = other.getX() - player.getX();
            double dz = other.getZ() - player.getZ();
            int px = centerX + (int) Math.round(rotatedX(dx, dz, cosA, sinA));
            int py = centerY + (int) Math.round(rotatedZ(dx, dz, cosA, sinA));
            if (px < screenX || px >= screenX + displaySize || py < screenY || py >= screenY + displaySize) continue;
            drawPlayerMarker(g, minecraft, other, px, py, verticalAlpha(other.getY(), player.getY()), heads);
        }

        String worldId = currentWorldId(minecraft);
        if (worldId != null && VoxyMapSettings.waypointsEnabled()) {
            double edgeHalf = displaySize / 2.0 - OFFSCREEN_EDGE_INSET;
            for (WaypointManager.Waypoint waypoint : WaypointManager.forWorld(worldId)) {
                double dx = rotatedX(waypoint.x() - player.getX(), waypoint.z() - player.getZ(), cosA, sinA);
                double dz = rotatedZ(waypoint.x() - player.getX(), waypoint.z() - player.getZ(), cosA, sinA);
                int px = centerX + (int) Math.round(dx);
                int py = centerY + (int) Math.round(dz);
                if (px >= screenX && px < screenX + displaySize && py >= screenY && py < screenY + displaySize) {
                    drawDot(g, px, py, MARKER_RADIUS, waypoint.color());
                } else {
                    drawOffscreenWaypoint(g, centerX, centerY, edgeHalf, dx, dz, waypoint.color());
                }
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
            double reach = displaySize * 1.5;
            if (x1 < player.getX() - reach || x0 > player.getX() + reach || z1 < player.getZ() - reach
                    || z0 > player.getZ() + reach) {
                continue;
            }
            x0 = Math.max(x0, player.getX() - reach);
            x1 = Math.min(x1, player.getX() + reach);
            z0 = Math.max(z0, player.getZ() - reach);
            z1 = Math.min(z1, player.getZ() + reach);
            drawGridLine(g, player, centerX, centerY, x0, z0, x1, z0, cosA, sinA, color);
            drawGridLine(g, player, centerX, centerY, x1, z0, x1, z1, cosA, sinA, color);
            drawGridLine(g, player, centerX, centerY, x1, z1, x0, z1, cosA, sinA, color);
            drawGridLine(g, player, centerX, centerY, x0, z1, x0, z0, cosA, sinA, color);
        }

        if (cosA == 1.0 && sinA == 0.0) {
            drawFacingIndicator(g, centerX, centerY, player.getYRot());
        } else {
            drawFacingIndicator(g, centerX, centerY, 0.0, -1.0);
        }
        drawDot(g, centerX, centerY, MARKER_RADIUS + 1, 0xFF0A0A12);
        drawDot(g, centerX, centerY, MARKER_RADIUS, 0xFF55E7FF);
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

    private static void drawChunkGrid(GuiGraphicsExtractor g, Player player, int centerX, int centerY,
                                       int displaySize, double cosA, double sinA) {
        if (!VoxyMapSettings.chunkGridEnabled()) {
            return;
        }
        double radius = displaySize / 2.0 + 2.0;
        double minX = player.getX() - radius, maxX = player.getX() + radius;
        double minZ = player.getZ() - radius, maxZ = player.getZ() + radius;
        int color = 0x2AFFFFFF;

        int startGx = (int) (Math.floor(minX / 16.0) * 16);
        for (int gx = startGx; gx <= maxX; gx += 16) {
            drawGridLine(g, player, centerX, centerY, gx, minZ, gx, maxZ, cosA, sinA, color);
        }
        int startGz = (int) (Math.floor(minZ / 16.0) * 16);
        for (int gz = startGz; gz <= maxZ; gz += 16) {
            drawGridLine(g, player, centerX, centerY, minX, gz, maxX, gz, cosA, sinA, color);
        }
    }

    private static void drawGridLine(GuiGraphicsExtractor g, Player player, int centerX, int centerY,
                                      double worldX1, double worldZ1, double worldX2, double worldZ2,
                                      double cosA, double sinA, int color) {
        double dx1 = worldX1 - player.getX(), dz1 = worldZ1 - player.getZ();
        double dx2 = worldX2 - player.getX(), dz2 = worldZ2 - player.getZ();
        int x1 = centerX + (int) Math.round(rotatedX(dx1, dz1, cosA, sinA));
        int y1 = centerY + (int) Math.round(rotatedZ(dx1, dz1, cosA, sinA));
        int x2 = centerX + (int) Math.round(rotatedX(dx2, dz2, cosA, sinA));
        int y2 = centerY + (int) Math.round(rotatedZ(dx2, dz2, cosA, sinA));

        if (cosA == 1.0 && sinA == 0.0) {
            g.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
        } else {
            drawLine(g, x1, y1, x2, y2, color);
        }
    }

    private static void drawCardinalLetters(GuiGraphicsExtractor g, Minecraft minecraft, int centerX, int centerY,
                                             int displaySize, double cosA, double sinA) {
        double radius = displaySize / 2.0 - 8.0;
        drawCardinalLetter(g, minecraft, centerX, centerY, 0.0, -radius, cosA, sinA, "N", 0xFFFF6B5B);
        drawCardinalLetter(g, minecraft, centerX, centerY, radius, 0.0, cosA, sinA, "E", 0xFFDFF8FF);
        drawCardinalLetter(g, minecraft, centerX, centerY, 0.0, radius, cosA, sinA, "S", 0xFFDFF8FF);
        drawCardinalLetter(g, minecraft, centerX, centerY, -radius, 0.0, cosA, sinA, "W", 0xFFDFF8FF);
    }

    private static void drawCardinalLetter(GuiGraphicsExtractor g, Minecraft minecraft, int centerX, int centerY,
                                            double baseX, double baseZ, double cosA, double sinA, String letter, int color) {
        int x = centerX + (int) Math.round(rotatedX(baseX, baseZ, cosA, sinA));
        int y = centerY + (int) Math.round(rotatedZ(baseX, baseZ, cosA, sinA));
        int textWidth = minecraft.font.width(letter);
        g.text(minecraft.font, letter, x - textWidth / 2, y - 4, color);
    }

    private static void drawMobHead(GuiGraphicsExtractor g, MobHeadIcons.Face face, int px, int py, int frameColor, int alpha) {
        int half = MOB_HEAD_SIZE / 2;
        g.fill(px - half - 1, py - half - 1, px - half + MOB_HEAD_SIZE + 1, py - half + MOB_HEAD_SIZE + 1, frameColor);
        MobHeadIcons.FaceRect rect = face.rect();
        g.blit(RenderPipelines.GUI_TEXTURED, face.texture(), px - half, py - half,
                rect.u0() * FACE_UV_GRID, rect.v0() * FACE_UV_GRID, MOB_HEAD_SIZE, MOB_HEAD_SIZE,
                Math.max(1, Math.round((rect.u1() - rect.u0()) * FACE_UV_GRID)),
                Math.max(1, Math.round((rect.v1() - rect.v0()) * FACE_UV_GRID)),
                FACE_UV_GRID, FACE_UV_GRID, scaleAlpha(0xFFFFFFFF, alpha));
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

    private static void drawOffscreenWaypoint(GuiGraphicsExtractor g, int centerX, int centerY, double edgeHalf,
                                               double dx, double dz, int color) {
        if (Math.hypot(dx, dz) < 0.001) {
            return;
        }
        SquareEdgePoint edge = squareEdgeIntersection(dx, dz, edgeHalf);
        int ex = centerX + edge.x();
        int ey = centerY + edge.y();
        int tailX = ex + (int) Math.round(edge.dirX() * 6.0);
        int tailY = ey + (int) Math.round(edge.dirZ() * 6.0);
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

    static String currentWorldId(Minecraft minecraft) {
        if (minecraft.level == null) return null;
        WorldIdentifier identifier = WorldIdentifier.of(minecraft.level);
        return identifier == null ? null : identifier.getWorldId();
    }
}
