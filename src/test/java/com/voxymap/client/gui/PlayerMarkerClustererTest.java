package com.voxymap.client.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerMarkerClustererTest {
    @Test
    void buildsTransitiveStableConnectedComponents() {
        List<PlayerMarkerClusterer.Marker> markers = List.of(
                new PlayerMarkerClusterer.Marker(10, 10, 40),
                new PlayerMarkerClusterer.Marker(50, 10, 40),
                new PlayerMarkerClusterer.Marker(90, 10, 40),
                new PlayerMarkerClusterer.Marker(300, 20, 40));

        List<PlayerMarkerClusterer.Cluster> clusters = PlayerMarkerClusterer.cluster(markers, 42, 18, 8);

        assertEquals(List.of(0, 1, 2), clusters.get(0).memberIndexes());
        assertEquals(50, clusters.get(0).anchorX());
        assertEquals(List.of(3), clusters.get(1).memberIndexes());
    }

    @Test
    void wideLabelsIncreaseOnlyTheRequiredHorizontalDistance() {
        List<PlayerMarkerClusterer.Marker> markers = List.of(
                new PlayerMarkerClusterer.Marker(0, 0, 120),
                new PlayerMarkerClusterer.Marker(100, 0, 80),
                new PlayerMarkerClusterer.Marker(0, 30, 120));

        List<PlayerMarkerClusterer.Cluster> clusters = PlayerMarkerClusterer.cluster(markers, 42, 18, 8);

        assertEquals(List.of(0, 1), clusters.get(0).memberIndexes());
        assertEquals(List.of(2), clusters.get(1).memberIndexes());
    }
}
