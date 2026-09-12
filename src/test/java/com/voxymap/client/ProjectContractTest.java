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
    void metadataDeclaresTheSupportedRuntimeRange() throws IOException {
        JsonObject metadata = readJson(Path.of("src/main/resources/fabric.mod.json"));
        assertEquals("voxymap", metadata.get("id").getAsString());
        assertEquals("client", metadata.get("environment").getAsString());
        assertEquals("LTS_Server", metadata.getAsJsonArray("authors").get(0).getAsString());
        assertEquals(">=26.1.2 <=26.2", metadata.getAsJsonObject("depends").get("minecraft").getAsString());
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
    void voxyRenderMixinMatchesCurrentRenderOpaqueDescriptor() throws IOException {
        String source = Files.readString(Path.of(
                "src/client/java/com/voxymap/client/mixin/VoxyRenderSystemMixin.java"), StandardCharsets.UTF_8);
        assertTrue(source.contains("Viewport<?> viewport, int width, int height, CallbackInfo ci"),
                "Voxy 0.2.18+ renderOpaque requires viewport, width, and height arguments");
    }

    @Test
    void mapKeepsVanillaNearFieldForVoxyLodExclusionZone() throws IOException {
        String source = Files.readString(Path.of(
                "src/client/java/com/voxymap/client/map/VoxyMapGuiRenderer.java"), StandardCharsets.UTF_8);
        assertFalse(source.contains("glClear(GL11C.GL_COLOR_BUFFER_BIT"),
                "The vanilla near-field must remain behind Voxy's player-centered LOD exclusion zone");
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

    private static JsonObject readJson(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
