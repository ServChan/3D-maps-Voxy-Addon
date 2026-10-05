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

    @Test
    void segmentsAreClippedToTheCircle() {
        double[] clipped = MinimapRenderer.clipToCircle(-100, 0, 100, 0, 0, 0, 10);
        assertEquals(-10.0, clipped[0], 1e-9);
        assertEquals(10.0, clipped[2], 1e-9);
        assertEquals(0.0, clipped[1], 1e-9);
    }

    @Test
    void segmentsInsideTheCircleAreKeptAndOnesOutsideAreDropped() {
        double[] inside = MinimapRenderer.clipToCircle(-2, 1, 3, 1, 0, 0, 10);
        assertEquals(-2.0, inside[0], 1e-9);
        assertEquals(3.0, inside[2], 1e-9);
        assertEquals(null, MinimapRenderer.clipToCircle(-100, 20, 100, 20, 0, 0, 10));
        assertEquals(null, MinimapRenderer.clipToCircle(20, 0, 30, 0, 0, 0, 10));
    }

    @Test
    void tilesAreLoadedFromThePlayerOutwards() {
        int[] order = MinimapRenderer.centerOutOrder(5);
        assertEquals(25, order.length);
        assertEquals(12, order[0]);
        java.util.Set<Integer> neighbours = java.util.Set.of(7, 11, 13, 17);
        for (int i = 1; i <= 4; i++) {
            assertTrue(neighbours.contains(order[i]));
        }
        assertEquals(25, java.util.Arrays.stream(order).distinct().count());
    }

    @Test
    void theMinimapShrinksToFitSmallWindowsButNotBelowTheMinimum() {
        assertEquals(128, MinimapRenderer.fitSize(128, 400, 300));
        assertEquals(90, MinimapRenderer.fitSize(128, 400, 90));
        assertEquals(64, MinimapRenderer.fitSize(128, 400, 20));
    }

    @Test
    void halfChordIsZeroOutsideTheCircle() {
        assertEquals(10.0, MinimapRenderer.halfChord(10.0, 0.0), 1e-9);
        assertEquals(0.0, MinimapRenderer.halfChord(10.0, 11.0));
    }

    @Test
    void heightArrowsAppearOnlyBeyondThreeBlocks() {
        assertEquals(0, MinimapRenderer.heightHint(3.0));
        assertEquals(1, MinimapRenderer.heightHint(3.5));
        assertEquals(-1, MinimapRenderer.heightHint(-10.0));
        assertEquals(0, MinimapRenderer.heightHint(-2.0));
    }
}
