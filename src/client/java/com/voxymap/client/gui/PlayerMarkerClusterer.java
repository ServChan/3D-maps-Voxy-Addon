package com.voxymap.client.gui;

import java.util.ArrayList;
import java.util.List;

/** Deterministic connected-component clustering for projected player labels. */
final class PlayerMarkerClusterer {
    private PlayerMarkerClusterer() {
    }

    static List<Cluster> cluster(List<Marker> markers, int minimumXDistance, int yDistance, int labelGap) {
        List<Cluster> result = new ArrayList<>();
        boolean[] visited = new boolean[markers.size()];
        for (int start = 0; start < markers.size(); start++) {
            if (visited[start]) continue;
            List<Integer> members = new ArrayList<>();
            List<Integer> pending = new ArrayList<>();
            pending.add(start);
            visited[start] = true;
            for (int cursor = 0; cursor < pending.size(); cursor++) {
                int index = pending.get(cursor);
                Marker candidate = markers.get(index);
                members.add(index);
                for (int other = 0; other < markers.size(); other++) {
                    if (!visited[other] && overlaps(candidate, markers.get(other), minimumXDistance, yDistance, labelGap)) {
                        visited[other] = true;
                        pending.add(other);
                    }
                }
            }
            long sumX = 0;
            long sumY = 0;
            for (int index : members) {
                sumX += markers.get(index).x;
                sumY += markers.get(index).y;
            }
            result.add(new Cluster(List.copyOf(members),
                    Math.round((float) sumX / members.size()),
                    Math.round((float) sumY / members.size())));
        }
        return List.copyOf(result);
    }

    private static boolean overlaps(Marker first, Marker second, int minimumXDistance, int yDistance, int labelGap) {
        int xDistance = Math.max(minimumXDistance, (first.labelWidth + second.labelWidth) / 2 + labelGap);
        return Math.abs(first.x - second.x) <= xDistance && Math.abs(first.y - second.y) <= yDistance;
    }

    record Marker(int x, int y, int labelWidth) {
    }

    record Cluster(List<Integer> memberIndexes, int anchorX, int anchorY) {
    }
}
