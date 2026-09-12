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
    void netherUsesItsNativeVoxyWorldInsteadOfAnUnsupportedScreen() throws IOException {
        String source = Files.readString(Path.of(
                "src/client/java/com/voxymap/client/gui/MapScreen.java"), StandardCharsets.UTF_8);
        assertFalse(source.contains("isUnsupportedDimension"));
        assertFalse(source.contains("minecraft:the_nether"));
        assertTrue(source.contains("VoxyMapGuiRenderer.open(minecraft)"));
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
