package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapRendererTest {
    @Test
    void coordFloorsInsteadOfTruncatingTowardZero() {

        assertEquals(-1, MinimapRenderer.coord(-0.5));
        assertEquals(5, MinimapRenderer.coord(5.9));
        assertEquals(0, MinimapRenderer.coord(0.0));
    }

    @Test
    void argbToAbgrSwapsRedAndBlueButKeepsAlphaAndGreen() {
        int argb = 0x11223344;
        assertEquals(0x11443322, MinimapRenderer.argbToAbgr(argb));
    }

    @Test
    void rotationByZeroDegreesIsIdentity() {
        assertEquals(3.0, MinimapRenderer.rotatedX(3.0, 4.0, 1.0, 0.0));
        assertEquals(4.0, MinimapRenderer.rotatedZ(3.0, 4.0, 1.0, 0.0));
    }

    @Test
    void rotationByNinetyDegreesSwapsAxes() {

        assertEquals(-4.0, MinimapRenderer.rotatedX(3.0, 4.0, 0.0, 1.0), 1e-9);
        assertEquals(3.0, MinimapRenderer.rotatedZ(3.0, 4.0, 0.0, 1.0), 1e-9);
    }

    @Test
    void edgeIntersectionAlongAnAxisLandsExactlyOnThatEdge() {
        MinimapRenderer.SquareEdgePoint edge = MinimapRenderer.squareEdgeIntersection(10.0, 0.0, 50.0);
        assertEquals(50, edge.x());
        assertEquals(0, edge.y());
    }

    @Test
    void edgeIntersectionOnTheDiagonalIsClampedToTheNearerEdge() {

        MinimapRenderer.SquareEdgePoint edge = MinimapRenderer.squareEdgeIntersection(100.0, 100.0, 20.0);
        assertEquals(20, edge.x());
        assertEquals(20, edge.y());
    }

    @Test
    void edgeIntersectionScalesWithDirectionNotJustDistance() {

        MinimapRenderer.SquareEdgePoint edge = MinimapRenderer.squareEdgeIntersection(1.0, 100.0, 30.0);
        assertEquals(30, edge.y());
        assertEquals(0, edge.x());
    }

    @Test
    void verticalAlphaIsFullStrengthCloseToThePlayersOwnAltitude() {
        assertEquals(0xFF, MinimapRenderer.verticalAlpha(70.0, 64.0));
    }

    @Test
    void verticalAlphaIsDimmedFarFromThePlayersOwnAltitude() {
        assertEquals(0x50, MinimapRenderer.verticalAlpha(200.0, 64.0));
    }

    @Test
    void verticalAlphaFadesMonotonicallyBetweenTheNearAndFarThresholds() {
        int nearer = MinimapRenderer.verticalAlpha(64.0 + 30.0, 64.0);
        int farther = MinimapRenderer.verticalAlpha(64.0 + 50.0, 64.0);
        assertTrue(nearer > farther);
        assertTrue(nearer < 0xFF);
        assertTrue(farther > 0x50);
    }

    @Test
    void scaleAlphaKeepsRgbAndMultipliesOnlyAlpha() {

        assertEquals(0x80112233, MinimapRenderer.scaleAlpha(0xFF112233, 0x80));
    }

    @Test
    void scaleAlphaAtFullStrengthIsUnchanged() {
        assertEquals(0xFF112233, MinimapRenderer.scaleAlpha(0xFF112233, 0xFF));
    }
}
