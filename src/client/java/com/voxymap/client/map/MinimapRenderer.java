package com.voxymap.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.voxymap.client.VoxyMapClient;
import com.voxymap.client.gui.PlayerHeadIcon;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Always-on gameplay minimap - a small corner overlay distinct from the fullscreen VoxyMap opened with
 * {@code M}. Deliberately cheap: no second 3D camera pass over the scene (the fullscreen map achieves
 * its view by swapping out the game's one real camera for the whole frame, see {@code CameraMixin} -
 * doing that AND rendering the normal first-person view in the same frame would mean rendering the
 * world twice). Instead this draws a cached 2D texture built from {@link MinimapTileCache}, only
 * rebuilding and re-uploading it when the player crosses into a new chunk - every other frame is just
 * one cheap textured blit plus a handful of marker fills.
 */
public final class MinimapRenderer {
    // Texture covers a 17x17 chunk (272x272 block) area - bigger than the largest on-screen window
    // (the enlarged size below) needs, so panning within it never has to rebuild mid-chunk.
    private static final int GRID_CHUNKS = 17;
    private static final int TEXTURE_SIZE = GRID_CHUNKS * MinimapTileCache.TILE_SIZE;
    private static final int DISPLAY_SIZE = 128; // Normal on-screen size, in pixels.
    private static final float ZOOM_SCALE = 1.6f; // Xaero-style "hold to enlarge".
    private static final int MARGIN = 8;
    private static final int MARKER_RADIUS = 2;
    private static final int MOB_MARKER_RADIUS = 1;
    private static final int PLAYER_HEAD_SIZE = 8;
    private static final double OFFSCREEN_EDGE_INSET = 9.0;
    private static final int COORD_BAR_HEIGHT = 14;
    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath("voxymap", "minimap_dynamic");

    private static final MinimapTileCache TILES = new MinimapTileCache();
    private static DynamicTexture texture;
    private static int centerChunkX = Integer.MIN_VALUE;
    private static int centerChunkZ = Integer.MIN_VALUE;

    private MinimapRenderer() {
    }

    public static void extract(GuiGraphicsExtractor g, Minecraft minecraft) {
        if (!VoxyMapSettings.minimapEnabled() || minecraft.level == null || minecraft.player == null
                || VoxyMapCameraController.isActive()) {
            return; // Hidden while the fullscreen map itself is open, or disabled in Settings.
        }

        Player player = minecraft.player;
        Level level = minecraft.level;
        int playerChunkX = Math.floorDiv((int) Math.floor(player.getX()), 16);
        int playerChunkZ = Math.floorDiv((int) Math.floor(player.getZ()), 16);

        ensureTexture(minecraft);
        if (centerChunkX != playerChunkX || centerChunkZ != playerChunkZ) {
            rebuild(level, playerChunkX, playerChunkZ, (int) Math.floor(player.getY()));
        }

        int displaySize = VoxyMapClient.isMinimapZoomHeld() ? Math.round(DISPLAY_SIZE * ZOOM_SCALE) : DISPLAY_SIZE;
        double worldRadius = displaySize / 2.0; // 1 block == 1 texture pixel == 1 screen pixel, at any size.
        int screenX = MARGIN;
        int screenY = MARGIN;
        int centerX = screenX + displaySize / 2;
        int centerY = screenY + displaySize / 2;

        // Rotates every offset drawn on the minimap (background, markers, grid, cardinal letters) so the
        // player's own facing direction always points straight up, Xaero-"rotate" style, instead of the
        // map staying fixed north-up. cosA=1/sinA=0 (rotation=0) when the setting is off, which makes
        // every rotatedX/rotatedZ call below an identity - the non-rotating code path is unchanged.
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

        // A rotated square must sample a wider source area to still cover every corner of the display
        // square after rotation (classic "diagonal of a square" factor) - clamped so it never reads past
        // the cached texture's own edge.
        double blitRadius = rotation == 0.0 ? worldRadius
                : Math.min(worldRadius * 1.4143, TEXTURE_SIZE / 2.0 - 4.0);
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

    private static void drawCoordinateBar(GuiGraphicsExtractor g, Minecraft minecraft, int x, int y, int width) {
        Player player = minecraft.player;
        g.fill(x, y, x + width, y + COORD_BAR_HEIGHT, 0xCC030914);
        g.fill(x, y, x + width, y + 1, 0xFF3BA4FF);
        String text = coord(player.getX()) + ", " + coord(player.getY()) + ", " + coord(player.getZ());
        g.centeredText(minecraft.font, text, x + width / 2, y + 3, 0xFFDFF8FF);
    }

    private static int coord(double value) {
        return (int) Math.floor(value);
    }

    private static void ensureTexture(Minecraft minecraft) {
        if (texture != null) {
            return;
        }
        texture = new DynamicTexture(() -> "VoxyMap minimap", TEXTURE_SIZE, TEXTURE_SIZE, false);
        minecraft.getTextureManager().register(TEXTURE_ID, texture);
    }

    private static void rebuild(Level level, int chunkX, int chunkZ, int playerY) {
        centerChunkX = chunkX;
        centerChunkZ = chunkZ;
        NativeImage pixels = texture.getPixels();
        int half = GRID_CHUNKS / 2;
        int tileSize = MinimapTileCache.TILE_SIZE;
        for (int cz = -half; cz <= half; cz++) {
            for (int cx = -half; cx <= half; cx++) {
                int[] tile = TILES.tileFor(level, chunkX + cx, chunkZ + cz, playerY);
                int baseX = (cx + half) * tileSize;
                int baseY = (cz + half) * tileSize;
                for (int ty = 0; ty < tileSize; ty++) {
                    for (int tx = 0; tx < tileSize; tx++) {
                        int argb = tile == null ? 0 : tile[ty * tileSize + tx];
                        pixels.setPixelABGR(baseX + tx, baseY + ty, argbToAbgr(argb));
                    }
                }
            }
        }
        texture.upload();
    }

    private static int argbToAbgr(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int gr = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (gr << 8) | r;
    }

    private static void drawMarkers(GuiGraphicsExtractor g, Minecraft minecraft, int screenX, int screenY,
                                     int displaySize, int centerX, int centerY, double cosA, double sinA) {
        Player player = minecraft.player;

        for (Player other : minecraft.level.players()) {
            if (other == player) continue;
            double dx = other.getX() - player.getX();
            double dz = other.getZ() - player.getZ();
            int px = centerX + (int) Math.round(rotatedX(dx, dz, cosA, sinA));
            int py = centerY + (int) Math.round(rotatedZ(dx, dz, cosA, sinA));
            if (px < screenX || px >= screenX + displaySize || py < screenY || py >= screenY + displaySize) continue;
            drawPlayerMarker(g, minecraft, other, px, py);
        }

        if (VoxyMapSettings.mobsEnabled()) {
            for (MobMarkerCache.MobSnapshot mob : MobMarkerCache.snapshot()) {
                double dx = mob.x() - player.getX();
                double dz = mob.z() - player.getZ();
                int px = centerX + (int) Math.round(rotatedX(dx, dz, cosA, sinA));
                int py = centerY + (int) Math.round(rotatedZ(dx, dz, cosA, sinA));
                if (px < screenX || px >= screenX + displaySize || py < screenY || py >= screenY + displaySize) continue;
                drawDot(g, px, py, MOB_MARKER_RADIUS, mob.hostile() ? 0xFFFF5555 : 0xFF7CFC6E);
            }
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

        // When the map itself rotates to track the player's facing, that facing is by definition always
        // straight up - only the static north-up mode needs the wedge computed from the real yaw.
        if (cosA == 1.0 && sinA == 0.0) {
            drawFacingIndicator(g, centerX, centerY, player.getYRot());
        } else {
            drawFacingIndicator(g, centerX, centerY, 0.0, -1.0);
        }
        drawDot(g, centerX, centerY, MARKER_RADIUS + 1, 0xFF0A0A12);
        drawDot(g, centerX, centerY, MARKER_RADIUS, 0xFF55E7FF);
    }

    private static double rotatedX(double dx, double dz, double cosA, double sinA) {
        return dx * cosA - dz * sinA;
    }

    private static double rotatedZ(double dx, double dz, double cosA, double sinA) {
        return dx * sinA + dz * cosA;
    }

    /** Subtle lines at every chunk boundary within the display window, rotated the same way markers are
     * so they stay aligned with the terrain underneath in rotate mode. */
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
        // Every grid line is axis-aligned in world space (one endpoint pair shares an X, the other a Z),
        // and cosA==1/sinA==0 (see the facing-indicator branch above) means north-up mode leaves it
        // axis-aligned on screen too - draw it as one filled rect instead of walking it a pixel at a
        // time, since the whole grid redraws every frame the minimap is on screen.
        if (cosA == 1.0 && sinA == 0.0) {
            g.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
        } else {
            drawLine(g, x1, y1, x2, y2, color);
        }
    }

    /** N/E/S/W around the minimap's edge, orbiting with it in rotate mode - drawn upright regardless of
     * rotation since only their orbit position (not the glyph itself) is rotated. */
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

    /** Draws another player as their skin's face (falling back to a plain dot if the client hasn't
     * cached that player's skin yet, e.g. the tick they join) plus a name label above it, clipped to the
     * minimap by the scissor rect {@link #extract} already has active. */
    private static void drawPlayerMarker(GuiGraphicsExtractor g, Minecraft minecraft, Player other, int px, int py) {
        Identifier skin = PlayerHeadIcon.resolveSkinTexture(minecraft, other.getUUID());
        if (skin != null) {
            drawDot(g, px, py, MARKER_RADIUS + 2, 0xEE0A0A12);
            PlayerHeadIcon.draw(g, skin, px - PLAYER_HEAD_SIZE / 2, py - PLAYER_HEAD_SIZE / 2, PLAYER_HEAD_SIZE);
        } else {
            drawDot(g, px, py, MARKER_RADIUS, 0xFFFF62D4);
        }

        String name = other.getName().getString();
        int nameWidth = minecraft.font.width(name);
        int nameX = px - nameWidth / 2;
        int nameY = py - MARKER_RADIUS - 11;
        g.fill(nameX - 2, nameY - 1, nameX + nameWidth + 2, nameY + 8, 0x99030914);
        g.text(minecraft.font, name, nameX, nameY, 0xFFFFFFFF);
    }

    /** For a waypoint too far to fit inside the minimap's own display window - clamps its direction to
     * a point on the minimap's edge (so a waypoint thousands of blocks away still shows which way to go,
     * not just ones already close enough to plot at their real position) and draws it as a marker with a
     * short tail pointing further outward, distinguishing it from an in-range dot at a glance. */
    private static void drawOffscreenWaypoint(GuiGraphicsExtractor g, int centerX, int centerY, double edgeHalf,
                                               double dx, double dz, int color) {
        double distance = Math.hypot(dx, dz);
        if (distance < 0.001) {
            return;
        }
        double dirX = dx / distance;
        double dirZ = dz / distance;
        double t = Math.min(
                dirX == 0.0 ? Double.MAX_VALUE : edgeHalf / Math.abs(dirX),
                dirZ == 0.0 ? Double.MAX_VALUE : edgeHalf / Math.abs(dirZ));
        int ex = centerX + (int) Math.round(dirX * t);
        int ey = centerY + (int) Math.round(dirZ * t);
        int tailX = ex + (int) Math.round(dirX * 6.0);
        int tailY = ey + (int) Math.round(dirZ * 6.0);
        drawLine(g, ex, ey, tailX, tailY, color);
        drawDot(g, ex, ey, MARKER_RADIUS + 1, 0xEE0A0A12);
        drawDot(g, ex, ey, MARKER_RADIUS, color);
    }

    /** A short wedge pointing where the player is currently facing - the only facing cue on the minimap
     * in north-up mode, since the map itself doesn't rotate there. */
    private static void drawFacingIndicator(GuiGraphicsExtractor g, int centerX, int centerY, float yawDegrees) {
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

    private static void drawLine(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1))));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int px = (int) Math.round(x1 + (x2 - x1) * t);
            int py = (int) Math.round(y1 + (y2 - y1) * t);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    private static void drawDot(GuiGraphicsExtractor g, int centerX, int centerY, int radius, int color) {
        g.fill(centerX - radius, centerY - radius, centerX + radius + 1, centerY + radius + 1, color);
    }

    private static String currentWorldId(Minecraft minecraft) {
        if (minecraft.level == null) return null;
        WorldIdentifier identifier = WorldIdentifier.of(minecraft.level);
        return identifier == null ? null : identifier.getWorldId();
    }
}
