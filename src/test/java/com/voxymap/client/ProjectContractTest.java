package com.voxymap.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectContractTest {
    @Test
    void metadataDeclaresTheSupportedRuntime() throws IOException {
        JsonObject metadata = readJson(Path.of("src/main/resources/fabric.mod.json"));
        assertEquals("voxymap", metadata.get("id").getAsString());
        assertEquals("client", metadata.get("environment").getAsString());
        assertEquals("LTS_Server", metadata.getAsJsonArray("authors").get(0).getAsString());
        assertEquals("26.2", metadata.getAsJsonObject("depends").get("minecraft").getAsString());
        assertEquals(">=25", metadata.getAsJsonObject("depends").get("java").getAsString());
    }

    @Test
    void everyConfiguredMixinHasASourceClass() throws IOException {
        JsonObject config = readJson(Path.of("src/main/resources/voxymap.client.mixins.json"));
        String packagePath = config.get("package").getAsString().replace('.', '/');
        JsonArray mixins = config.getAsJsonArray("client");
        for (var mixin : mixins) {
            Path source = Path.of("src/client/java", packagePath, mixin.getAsString() + ".java");
            assertTrue(Files.isRegularFile(source), "Missing mixin source: " + source);
        }
    }

    @Test
    void projectDoesNotShipMinecraftApiStubs() {
        Path forbidden = Path.of("src/client/java/net/minecraft");
        assertFalse(Files.exists(forbidden), "Do not compile fake classes in the net.minecraft namespace");
    }

    @Test
    void mapUsesOneNativeWorldRenderPass() throws IOException {
        String coordinator = Files.readString(Path.of(
                "src/client/java/com/voxymap/client/map/VoxyMapGuiRenderer.java"), StandardCharsets.UTF_8);
        String gameRendererMixin = Files.readString(Path.of(
                "src/client/java/com/voxymap/client/mixin/GameRendererMixin.java"), StandardCharsets.UTF_8);
        assertFalse(coordinator.contains("renderOpaque("),
                "Do not submit Voxy a second time after its native world pass");
        assertFalse(coordinator.contains("voxy$shutdownRenderer"),
                "Opening the map must not rebuild Voxy while node work is in flight");
        assertFalse(gameRendererMixin.contains("renderGuiMapBeforeHud"),
                "The map should use Minecraft's normal world then GUI ordering");
    }

    @Test
    void recenterUsesThePlayersRealHeightAndMarkersUseTheCachedPlayerList() throws IOException {
        String source = Files.readString(Path.of(
                "src/client/java/com/voxymap/client/gui/MapScreen.java"), StandardCharsets.UTF_8);
        assertTrue(source.contains("viewCenterY = minecraft.player.getY()"),
                "Centering must aim at the player instead of a fixed map height");
        assertTrue(source.contains("capturePlayerMarkers();"));
        assertTrue(source.contains("for (Player player : minecraft.level.players())"));
        assertFalse(source.substring(source.indexOf("extractRenderState"), source.indexOf("private double openingProgress"))
                        .contains("minecraft.level.players()"),
                "Entity snapshots must not be collected in the render-state loop");
    }

    @Test
    void mapUsesVoxysCachedLodsInsteadOfTheRealCameraSodiumVisibilitySet() throws IOException {
        String mixin = Files.readString(Path.of(
                "src/client/java/com/voxymap/client/mixin/VoxyClientMixin.java"), StandardCharsets.UTF_8);
        JsonObject config = readJson(Path.of("src/main/resources/voxymap.client.mixins.json"));

        assertTrue(config.getAsJsonArray("client").asList().stream()
                .anyMatch(element -> "VoxyClientMixin".equals(element.getAsString())));
        assertTrue(mixin.contains("disableSodiumChunkRender"),
                "Use Voxy's supported LOD-only switch instead of adding another render pass");
        assertTrue(mixin.contains("VoxyMapCameraController.isActive()"),
                "Normal gameplay must retain Sodium's vanilla-distance terrain");
        assertTrue(mixin.contains("cir.setReturnValue(true)"),
                "The map must let Voxy fill its normal near-camera exclusion zone");
        assertFalse(Files.exists(Path.of(
                "src/client/java/com/voxymap/client/mixin/MapVanillaTerrainMixin.java")),
                "Do not cancel the chunk pass independently of Voxy's own render hook");
    }

    @Test
    void duplicateVoxyNodeRequestsAreSilencedWithTheSameEarlyReturnCondition() throws IOException {
        String mixin = Files.readString(Path.of(
                "src/client/java/com/voxymap/client/mixin/VoxyNodeManagerMixin.java"), StandardCharsets.UTF_8);
        JsonObject config = readJson(Path.of("src/main/resources/voxymap.client.mixins.json"));

        assertTrue(config.getAsJsonArray("client").asList().stream()
                .anyMatch(element -> "VoxyNodeManagerMixin".equals(element.getAsString())));
        assertTrue(mixin.contains("target = \"Lme/cortex/voxy/common/Logger;error([Ljava/lang/Object;)V\""));
        assertTrue(mixin.contains("ordinal = 0"),
                "Cancel the already-confirmed duplicate branch instead of racing Voxy's mutable node map");
        assertTrue(mixin.contains("ci.cancel()"),
                "The mixin should only skip Voxy's existing log-and-return duplicate path");
    }

    @Test
    void netherRoofRemovalUsesWholeQuadsInsteadOfAntiXrayCrossSections() throws IOException {
        String controller = Files.readString(Path.of(
                "src/client/java/com/voxymap/client/map/NetherRoofController.java"), StandardCharsets.UTF_8);
        String shader = Files.readString(Path.of(
                "src/client/java/com/voxymap/client/map/VoxyShaderPatcher.java"), StandardCharsets.UTF_8);
        assertTrue(controller.contains("minecraft:the_nether"));
        assertTrue(controller.contains("NetherRoofPolicy.cutoff("));
        assertTrue(controller.contains("float netherRoofCutoff = isNether"),
                "Remote Nether maps also need their bedrock shell removed");
        assertTrue(shader.contains("voxymapGlobalCutawayY"));
        assertTrue(shader.contains("voxymapQuadMinY >= voxymapRoofCutY"));
        assertTrue(shader.contains("voxymapQuadCenterXZ"));
        assertTrue(shader.contains("voxymapApertureRadius"));
        assertTrue(controller.contains("updateViewContext"));
        assertFalse(shader.contains("min(voxymapCutY"),
                "Global clipping must not manufacture a tiled anti-xray cross-section");
    }

    @Test
    void theRemovedRoomCutawayAndGhastRouteStayRemoved() {
        for (String removed : new String[] {
                "src/client/java/com/voxymap/client/map/RoomCutawayComputer.java",
                "src/client/java/com/voxymap/client/map/RoomCutawayController.java",
                "src/client/java/com/voxymap/client/map/RoomCutawayTexture.java",
                "src/client/java/com/voxymap/client/map/RoomCutawayShaderPatcher.java",
                "src/client/java/com/voxymap/client/map/SpiralRouteGeometry.java",
                "src/client/java/com/voxymap/client/integration/HappyGhastScannerBridge.java"}) {
            assertFalse(Files.exists(Path.of(removed)), removed + " was removed on purpose");
        }
    }

    @Test
    void remoteMapResponsesAreBoundedBeforeTheyReachTheJsonParser() throws IOException {
        String players = Files.readString(Path.of(
                "src/client/java/com/voxymap/client/integration/VanillaSquadPlayerMapBridge.java"), StandardCharsets.UTF_8);
        String flags = Files.readString(Path.of(
                "src/client/java/com/voxymap/client/integration/VanillaSquadFlagMapBridge.java"), StandardCharsets.UTF_8);

        assertTrue(players.contains("BodyHandlers.limiting("));
        assertTrue(flags.contains("BodyHandlers.limiting("));
    }

    @Test
    void vanillaSquadPlayerFetchIsOffByDefaultAndGatedBehindTheSetting() throws IOException {
        String settings = Files.readString(Path.of(
                "src/client/java/com/voxymap/client/map/VoxyMapSettings.java"), StandardCharsets.UTF_8);
        String bridge = Files.readString(Path.of(
                "src/client/java/com/voxymap/client/integration/VanillaSquadPlayerMapBridge.java"), StandardCharsets.UTF_8);
        assertTrue(settings.contains("vanillaSquadPlayersEnabled = false"),
                "Fetching from a third-party site must require an explicit opt-in, not be on by default");
        assertTrue(bridge.contains("if (!VoxyMapSettings.vanillaSquadPlayersEnabled() && !VoxyMapSettings.vanillaSquadFlagsEnabled())"),
                "Realm detection may fetch only when at least one VanillaSquad overlay was explicitly enabled");
    }

    @Test
    void localMarkerHasACenteredFallbackAndCorrectCameraUpVector() throws IOException {
        String source = Files.readString(Path.of(
                "src/client/java/com/voxymap/client/gui/MapScreen.java"), StandardCharsets.UTF_8);
        assertTrue(source.contains("marker.local && isViewCenteredOn(marker)"));
        assertTrue(source.contains("new ScreenPoint(width / 2, height / 2)"));
        // The up-vector is computed once per camera update (see updateCameraBasis(), called from
        // syncWorldCamera()) rather than recomputed by every projectToScreen/mapPointAt call - see the
        // efficiency fix that moved it. camUpX/camUpZ are the cached fields projectToScreen reads.
        assertTrue(source.contains("camUpX = -Math.sin(viewYaw) * Math.sin(pitch)"));
        assertTrue(source.contains("camUpZ = Math.cos(viewYaw) * Math.sin(pitch)"));
    }

    @Test
    void playerHeadsUseDestinationBoundsInsteadOfTreatingSizeAsAnAbsoluteCorner() throws IOException {
        String source = Files.readString(Path.of(
                "src/client/java/com/voxymap/client/gui/PlayerHeadIcon.java"), StandardCharsets.UTF_8);
        assertTrue(source.contains("RenderPipelines.GUI_TEXTURED"));
        assertFalse(source.contains("x, y, size, size"));
        assertTrue(source.contains("8.0f, 8.0f"), "Base face must start at skin pixel 8,8");
        assertTrue(source.contains("40.0f, 8.0f"), "Hat layer must start at skin pixel 40,8");
        assertTrue(source.contains("size, size, 8, 8, 64, 64"),
                "The selected source must be an 8x8 face from a 64x64 skin");
    }

    @Test
    void viewConesUseLiveHeadYawOnlyForPinkActivePlayers() throws IOException {
        String source = Files.readString(Path.of(
                "src/client/java/com/voxymap/client/gui/MapScreen.java"), StandardCharsets.UTF_8);
        assertTrue(source.contains("player.getYHeadRot()"));
        assertTrue(source.contains("return !local && !remote && Float.isFinite(yawDegrees)"));
        assertTrue(source.contains("blocksPerPixel * PLAYER_VIEW_CONE_SCREEN_LENGTH"),
                "The cone should keep a useful screen size across map zoom levels");
        assertTrue(source.contains("nearestActiveMarkerDistance"),
                "Crowded active players must collapse to compact direction ticks");
        assertTrue(source.contains("hovered || nearest >= PLAYER_VIEW_CONE_CROWD_DISTANCE"),
                "Hovering a crowded player must restore its full cone");
        assertTrue(source.indexOf("drawPlayerViewCone(g, marker, point)")
                < source.indexOf("drawDiamond(g, point.x, point.y, radius + 2, outline)"),
                "The marker must remain legible on top of its cone");
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
