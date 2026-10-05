package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobHeadIconsTest {
    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;
    private static final int GREEN = 0xFF00FF00;
    private static final int CLEAR = 0x00000000;

    private static HeadIconComposer.Quad quad(float minX, float maxX, float depth, float uLeft, float uRight) {
        return new HeadIconComposer.Quad(
                new float[]{minX, maxX, maxX, minX}, new float[]{0.0f, 0.0f, 1.0f, 1.0f},
                new float[]{uLeft, uRight, uRight, uLeft}, new float[]{0.0f, 0.0f, 1.0f, 1.0f}, depth);
    }

    @Test
    void theFrontFaceIsCopiedTexelForTexel() {
        HeadIconComposer.Icon icon = HeadIconComposer.compose(List.of(quad(0, 1, 0, 0, 1)), 2, 1, new int[]{RED, BLUE});
        assertEquals(2, icon.width());
        assertEquals(2, icon.height());
        assertArrayEquals(new int[]{RED, BLUE, RED, BLUE}, icon.argb());
    }

    @Test
    void mirroredUvsAreDrawnMirrored() {
        HeadIconComposer.Icon icon = HeadIconComposer.compose(List.of(quad(0, 1, 0, 1, 0)), 2, 1, new int[]{RED, BLUE});
        assertArrayEquals(new int[]{BLUE, RED, BLUE, RED}, icon.argb());
    }

    @Test
    void nearerPartsCoverTheFaceButTheirTransparentTexelsDoNot() {
        int[] texture = {RED, BLUE, GREEN, CLEAR};
        HeadIconComposer.Quad face = quad(0, 1, 0.0f, 0.0f, 0.5f);
        HeadIconComposer.Quad snout = quad(0, 1, -0.2f, 0.5f, 1.0f);
        HeadIconComposer.Icon icon = HeadIconComposer.compose(List.of(face, snout), 4, 1, texture);
        assertArrayEquals(new int[]{GREEN, BLUE, GREEN, BLUE}, icon.argb());
    }

    @Test
    void aFullyTransparentHeadHasNoIcon() {
        assertNull(HeadIconComposer.compose(List.of(quad(0, 1, 0, 0, 1)), 2, 1, new int[]{CLEAR, CLEAR}));
        assertNull(HeadIconComposer.compose(List.of(), 2, 1, new int[]{RED, BLUE}));
    }

    @Test
    void geometryIsTurnedSoTheMainFaceReadsLeftToRight() {
        HeadGeometry.RawVertex[] face = {
                new HeadGeometry.RawVertex(0.0f, 0.0f, -0.25f, 0.25f, 0.0f),
                new HeadGeometry.RawVertex(0.5f, 0.0f, -0.25f, 0.0f, 0.0f),
                new HeadGeometry.RawVertex(0.5f, 0.5f, -0.25f, 0.0f, 0.25f),
                new HeadGeometry.RawVertex(0.0f, 0.5f, -0.25f, 0.25f, 0.25f)};
        double[] gradient = HeadGeometry.textureGradient(face);
        assertTrue(gradient[0] < 0.0);
        assertTrue(gradient[1] > 0.0);
        HeadIconComposer.Quad quad = HeadGeometry.toQuads(List.<HeadGeometry.RawVertex[]>of(face), 0).getFirst();
        assertEquals(0.0f, quad.xs()[0], 1e-6f);
        assertEquals(-0.5f, quad.xs()[1], 1e-6f);
        assertTrue(HeadGeometry.textureGradient(new HeadGeometry.RawVertex[]{
                new HeadGeometry.RawVertex(quad.xs()[0], quad.ys()[0], 0, quad.us()[0], quad.vs()[0]),
                new HeadGeometry.RawVertex(quad.xs()[1], quad.ys()[1], 0, quad.us()[1], quad.vs()[1]),
                new HeadGeometry.RawVertex(quad.xs()[2], quad.ys()[2], 0, quad.us()[2], quad.vs()[2]),
                new HeadGeometry.RawVertex(quad.xs()[3], quad.ys()[3], 0, quad.us()[3], quad.vs()[3])})[0] > 0.0);
        assertTrue(HeadGeometry.toQuads(List.<HeadGeometry.RawVertex[]>of(face), -1).isEmpty());
    }

    @Test
    void aRotatedEarCoversOnlyItsOwnSlantedStripNotItsBoundingBox() {
        HeadIconComposer.Quad face = new HeadIconComposer.Quad(
                new float[]{0.0f, 4.0f, 4.0f, 0.0f}, new float[]{0.0f, 0.0f, 4.0f, 4.0f},
                new float[]{0.0f, 0.5f, 0.5f, 0.0f}, new float[]{0.0f, 0.0f, 1.0f, 1.0f}, 0.0f);
        HeadIconComposer.Quad ear = new HeadIconComposer.Quad(
                new float[]{0.0f, 1.0f, 4.0f, 3.0f}, new float[]{0.0f, 0.0f, 4.0f, 4.0f},
                new float[]{0.5f, 1.0f, 1.0f, 0.5f}, new float[]{0.0f, 0.0f, 1.0f, 1.0f}, -0.5f);
        int[] texture = new int[8 * 4];
        for (int i = 0; i < texture.length; i++) {
            texture[i] = i % 8 < 4 ? RED : GREEN;
        }
        HeadIconComposer.Icon icon = HeadIconComposer.compose(List.of(face, ear), 8, 4, texture);
        assertEquals(4, icon.width());
        assertEquals(4, icon.height());
        int width = icon.width();
        int greenTopLeft = icon.argb()[0];
        int bottomLeft = icon.argb()[(icon.height() - 1) * width];
        assertEquals(GREEN, greenTopLeft);
        assertEquals(RED, bottomLeft);
    }

    @Test
    void bigIconsShrinkToFitAndKeepTheirShape() {
        assertArrayEquals(new int[]{8, 8}, MinimapRenderer.iconSize(8, 8, 12));
        assertArrayEquals(new int[]{12, 6}, MinimapRenderer.iconSize(24, 12, 12));
    }

    static class SingleModelRenderer {
        private final float shadowRadius = 0.5f;
        private static final Map<String, String> SHARED = Map.of();
    }

    static class VariantRenderer {
        private final Map<String, Object> models = Map.of();
    }

    static class TwoModelRenderer {
        private final List<Object> models = List.of();
    }

    @Test
    void onlyRenderersWithoutExtraModelFieldsMayUseTheirCurrentModelBeforeLearning() {
        assertTrue(MobHeadIcons.isSingleModelRenderer(SingleModelRenderer.class));
        assertFalse(MobHeadIcons.isSingleModelRenderer(VariantRenderer.class));
        assertFalse(MobHeadIcons.isSingleModelRenderer(TwoModelRenderer.class));
    }
}
