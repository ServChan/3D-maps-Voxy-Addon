package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobHeadIconsTest {
    @Test
    void faceRectIsTheBoundingBoxOfTheFrontPolygonWhateverTheVertexOrder() {
        MobHeadIcons.FaceRect rect = MobHeadIcons.boundingRect(
                new float[]{16 / 64f, 8 / 64f, 8 / 64f, 16 / 64f},
                new float[]{8 / 64f, 8 / 64f, 16 / 64f, 16 / 64f});

        assertEquals(new MobHeadIcons.FaceRect(8 / 64f, 8 / 64f, 16 / 64f, 16 / 64f), rect);
        assertFalse(rect.isEmpty());
        assertTrue(MobHeadIcons.boundingRect(new float[]{0.5f, 0.5f}, new float[]{0.1f, 0.2f}).isEmpty());
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
