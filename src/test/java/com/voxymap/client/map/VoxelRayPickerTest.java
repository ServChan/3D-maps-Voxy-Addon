package com.voxymap.client.map;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VoxelRayPickerTest {
    @Test
    void hitsTheFirstOccupiedVoxelAndReportsItsEnteredFace() {
        VoxelRayPicker.Hit hit = VoxelRayPicker.pick(0.5, 10.5, 0.5, 0, -1, 0, 20,
                (x, y, z) -> x == 0 && y == 3 && z == 0);

        assertEquals(3, hit.blockY());
        assertEquals(Direction.UP, hit.face());
        assertEquals(4.0, hit.y(), 1.0e-9);
    }

    @Test
    void handlesNegativeCoordinatesWithoutTruncatingTowardZero() {
        VoxelRayPicker.Hit hit = VoxelRayPicker.pick(-0.25, 2.5, -0.25, -1, 0, 0, 5,
                (x, y, z) -> x == -2 && y == 2 && z == -1);

        assertEquals(-2, hit.blockX());
        assertEquals(Direction.EAST, hit.face());
    }

    @Test
    void returnsNullWhenNothingAlongTheRayIsKnownSolid() {
        assertNull(VoxelRayPicker.pick(0, 0, 0, 1, 0, 0, 10, (x, y, z) -> false));
    }
}
