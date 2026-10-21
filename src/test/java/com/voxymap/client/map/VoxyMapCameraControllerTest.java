package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxyMapCameraControllerTest {
    @Test
    void lodNearPlaneStaysWellBelowTheLowestCameraHeight() {
        for (boolean end : new boolean[]{false, true}) {
            for (double pitch = 0.0; pitch <= 1.0; pitch += 0.25) {
                for (double blocksPerPixel : new double[]{0.05, 1.0, 40.0}) {
                    VoxyMapCameraController.Pose pose = VoxyMapCameraController.pose(end, 0.0, 64.0, 0.0, 0.0, pitch, blocksPerPixel);
                    double height = pose.y() - 64.0;
                    assertTrue(height >= VoxyMapCameraController.LOD_NEAR_PLANE * 15.0,
                            "camera only " + height + " blocks above the focus");
                }
            }
        }
    }
}
