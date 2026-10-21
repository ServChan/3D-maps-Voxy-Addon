package com.voxymap.client.nav;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class RoutePlanner {
    static final float WALK_LAND = 0.20f;
    static final float WALK_FOREST = 0.30f;
    static final float WALK_ROAD = 0.17f;
    static final float WALK_ICE = 0.22f;
    static final float WALK_HAZARD = 0.80f;
    static final float WALK_UNKNOWN = 0.35f;
    static final float SWIM = 0.50f;
    static final float BOAT_WATER = 0.125f;
    static final float BOAT_ICE = 0.05f;
    static final float CLIMB_PER_BLOCK = 0.35f;
    static final float DROP_PER_BLOCK = 0.05f;
    static final float BOARD_SECONDS = 2.5f;
    static final float LEAVE_SECONDS = 1.0f;
    static final float HEURISTIC_PER_BLOCK = 0.15f;
    static final int SNAP_RADIUS = 8;
    static final double SIMPLIFY_CELLS = 0.9;
    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DZ = {0, 0, 1, -1, 1, -1, 1, -1};
    private static final double DIAGONAL = Math.sqrt(2.0);

    private RoutePlanner() {
    }

    public static Route plan(NavGrid grid, Capabilities caps, double startX, double startY, double startZ,
                             double goalX, double goalY, double goalZ, BooleanSupplier cancelled) {
        int start = snap(grid, grid.cellX(startX), grid.cellZ(startZ));
        int goal = snap(grid, grid.cellX(goalX), grid.cellZ(goalZ));
        if (start < 0 || goal < 0) {
            return null;
        }
        int width = grid.width();
        int layers = caps.boat() ? 2 : 1;
        int cells = width * grid.depth();
        float[] cost = new float[cells * layers];
        Arrays.fill(cost, Float.POSITIVE_INFINITY);
        int[] parent = new int[cells * layers];
        int goalCellX = goal % width;
        int goalCellZ = goal / width;
        double heuristicScale = HEURISTIC_PER_BLOCK * grid.cell();
        StateHeap open = new StateHeap(4096);
        int startState = start * layers;
        cost[startState] = 0.0f;
        parent[startState] = -1;
        open.push(startState, heuristic(start % width, start / width, goalCellX, goalCellZ, heuristicScale));
        int found = -1;
        int popped = 0;
        while (!open.isEmpty()) {
            if ((++popped & 0xFFF) == 0 && cancelled.getAsBoolean()) {
                return null;
            }
            float key = open.peekKey();
            int state = open.pop();
            int index = state / layers;
            int boat = state % layers;
            int cellX = index % width;
            int cellZ = index / width;
            float spent = cost[state];
            if (key > spent + heuristic(cellX, cellZ, goalCellX, goalCellZ, heuristicScale) + 1.0e-3f) {
                continue;
            }
            if (index == goal) {
                found = state;
                break;
            }
            if (layers == 2) {
                float switchCost = boat == 1 ? LEAVE_SECONDS
                        : NavKind.floats(grid.kind(index)) ? BOARD_SECONDS : Float.NaN;
                if (!Float.isNaN(switchCost)) {
                    int other = index * 2 + (1 - boat);
                    float next = spent + switchCost;
                    if (next < cost[other]) {
                        cost[other] = next;
                        parent[other] = state;
                        open.push(other, next + heuristic(cellX, cellZ, goalCellX, goalCellZ, heuristicScale));
                    }
                }
            }
            for (int direction = 0; direction < 8; direction++) {
                int nextX = cellX + DX[direction];
                int nextZ = cellZ + DZ[direction];
                if (!grid.contains(nextX, nextZ)) {
                    continue;
                }
                boolean diagonal = direction >= 4;
                if (diagonal && !cornerOpen(grid, cellX, cellZ, nextX, nextZ, boat == 1)) {
                    continue;
                }
                int nextIndex = grid.index(nextX, nextZ);
                double length = grid.cell() * (diagonal ? DIAGONAL : 1.0);
                float edge = boat == 1 ? boatEdge(grid, index, nextIndex, length) : walkEdge(grid, index, nextIndex, length);
                if (Float.isNaN(edge)) {
                    continue;
                }
                int nextState = nextIndex * layers + boat;
                float next = spent + edge;
                if (next < cost[nextState]) {
                    cost[nextState] = next;
                    parent[nextState] = state;
                    open.push(nextState, next + heuristic(nextX, nextZ, goalCellX, goalCellZ, heuristicScale));
                }
            }
        }
        if (found < 0) {
            return null;
        }
        return build(grid, layers, parent, found, cost[found], startX, startY, startZ, goalX, goalY, goalZ);
    }

    static float walkCost(byte kind) {
        return switch (kind) {
            case NavKind.LAND -> WALK_LAND;
            case NavKind.FOREST -> WALK_FOREST;
            case NavKind.ROAD -> WALK_ROAD;
            case NavKind.ICE -> WALK_ICE;
            case NavKind.HAZARD -> WALK_HAZARD;
            case NavKind.UNKNOWN -> WALK_UNKNOWN;
            case NavKind.WATER -> SWIM;
            default -> -1.0f;
        };
    }

    static float boatCost(byte kind) {
        return switch (kind) {
            case NavKind.WATER -> BOAT_WATER;
            case NavKind.ICE -> BOAT_ICE;
            default -> -1.0f;
        };
    }

    static int maxRise(int cell) {
        return Math.max(1, cell);
    }

    static int maxDrop(int cell) {
        return Math.max(3, cell * 2);
    }

    private static float walkEdge(NavGrid grid, int from, int to, double length) {
        byte fromKind = grid.kind(from);
        byte toKind = grid.kind(to);
        float fromCost = walkCost(fromKind);
        float toCost = walkCost(toKind);
        if (fromCost < 0.0f || toCost < 0.0f) {
            return Float.NaN;
        }
        float base = (float) (length * (fromCost + toCost) * 0.5);
        if (fromKind == NavKind.UNKNOWN || toKind == NavKind.UNKNOWN) {
            return base;
        }
        int rise = grid.height(to) - grid.height(from);
        if (rise > maxRise(grid.cell())) {
            return Float.NaN;
        }
        if (rise >= 0) {
            return base + rise * CLIMB_PER_BLOCK;
        }
        int drop = -rise;
        if (drop > maxDrop(grid.cell()) && toKind != NavKind.WATER) {
            return Float.NaN;
        }
        return base + drop * DROP_PER_BLOCK;
    }

    private static float boatEdge(NavGrid grid, int from, int to, double length) {
        float fromCost = boatCost(grid.kind(from));
        float toCost = boatCost(grid.kind(to));
        if (fromCost < 0.0f || toCost < 0.0f || grid.height(to) - grid.height(from) > 1) {
            return Float.NaN;
        }
        return (float) (length * (fromCost + toCost) * 0.5);
    }

    private static boolean cornerOpen(NavGrid grid, int cellX, int cellZ, int nextX, int nextZ, boolean boat) {
        byte sideA = grid.kind(grid.index(nextX, cellZ));
        byte sideB = grid.kind(grid.index(cellX, nextZ));
        if (boat) {
            return NavKind.floats(sideA) && NavKind.floats(sideB);
        }
        return sideA != NavKind.BLOCKED && sideB != NavKind.BLOCKED;
    }

    private static float heuristic(int cellX, int cellZ, int goalX, int goalZ, double scale) {
        return (float) (Math.hypot(cellX - goalX, cellZ - goalZ) * scale);
    }

    static int snap(NavGrid grid, int cellX, int cellZ) {
        for (int radius = 0; radius <= SNAP_RADIUS; radius++) {
            int best = -1;
            int bestDistance = Integer.MAX_VALUE;
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int x = cellX + dx;
                    int z = cellZ + dz;
                    if (!grid.contains(x, z) || grid.kind(grid.index(x, z)) == NavKind.BLOCKED) {
                        continue;
                    }
                    int distance = dx * dx + dz * dz;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = grid.index(x, z);
                    }
                }
            }
            if (best >= 0) {
                return best;
            }
        }
        return -1;
    }

    record Step(int index, TravelMode mode, boolean unknown) {
        boolean sameLeg(Step other) {
            return mode == other.mode && unknown == other.unknown;
        }
    }

    private static Route build(NavGrid grid, int layers, int[] parent, int goalState, float seconds,
                               double startX, double startY, double startZ, double goalX, double goalY, double goalZ) {
        List<Integer> chain = new ArrayList<>();
        for (int state = goalState; state >= 0; state = parent[state]) {
            chain.add(state);
        }
        Collections.reverse(chain);
        List<Step> steps = new ArrayList<>();
        for (int state : chain) {
            int index = state / layers;
            boolean boat = state % layers == 1;
            byte kind = grid.kind(index);
            TravelMode mode = boat ? TravelMode.BOAT : kind == NavKind.WATER ? TravelMode.SWIM : TravelMode.WALK;
            Step step = new Step(index, mode, kind == NavKind.UNKNOWN);
            if (!steps.isEmpty() && steps.getLast().index() == index) {
                steps.set(steps.size() - 1, step);
            } else {
                steps.add(step);
            }
        }
        int width = grid.width();
        double length = 0.0;
        for (int i = 1; i < steps.size(); i++) {
            int a = steps.get(i - 1).index();
            int b = steps.get(i).index();
            length += grid.cell() * Math.hypot(a % width - b % width, a / width - b / width);
        }
        List<Integer> kept = simplify(width, steps);
        List<Route.Point> points = new ArrayList<>();
        double lastY = startY;
        for (int k = 0; k < kept.size(); k++) {
            int i = kept.get(k);
            Step step = steps.get(i);
            Step leg = i + 1 < steps.size() ? steps.get(i + 1) : step;
            double y = grid.kind(step.index()) == NavKind.UNKNOWN ? lastY : grid.height(step.index());
            double x = grid.centerX(step.index() % width);
            double z = grid.centerZ(step.index() / width);
            if (k == 0) {
                x = startX;
                y = startY;
                z = startZ;
            } else if (k == kept.size() - 1) {
                x = goalX;
                z = goalZ;
                if (!Double.isNaN(goalY)) {
                    y = goalY;
                }
            }
            lastY = y;
            points.add(new Route.Point(x, y, z, leg.mode(), leg.unknown()));
        }
        if (points.size() == 1) {
            Route.Point only = points.getFirst();
            points.add(new Route.Point(goalX, Double.isNaN(goalY) ? only.y() : goalY, goalZ, only.mode(), only.unknown()));
            length = Math.hypot(goalX - startX, goalZ - startZ);
        }
        return new Route(points, seconds, length, false, 0, 0);
    }

    static List<Integer> simplify(int width, List<Step> steps) {
        int count = steps.size();
        List<Integer> kept = new ArrayList<>();
        if (count <= 2) {
            for (int i = 0; i < count; i++) {
                kept.add(i);
            }
            return kept;
        }
        int runStart = 0;
        for (int i = 1; i < count - 1; i++) {
            if (!steps.get(i + 1).sameLeg(steps.get(runStart + 1))) {
                reduce(width, steps, runStart, i, kept);
                runStart = i;
            }
        }
        reduce(width, steps, runStart, count - 1, kept);
        return kept;
    }

    private static void reduce(int width, List<Step> steps, int from, int to, List<Integer> kept) {
        if (kept.isEmpty() || kept.getLast() != from) {
            kept.add(from);
        }
        if (to <= from) {
            return;
        }
        boolean[] keep = new boolean[to - from + 1];
        keep[keep.length - 1] = true;
        ArrayDeque<int[]> pending = new ArrayDeque<>();
        pending.push(new int[]{from, to});
        while (!pending.isEmpty()) {
            int[] span = pending.pop();
            int a = span[0];
            int b = span[1];
            if (b - a < 2) {
                continue;
            }
            int ia = steps.get(a).index();
            int ib = steps.get(b).index();
            double worst = -1.0;
            int worstAt = -1;
            for (int i = a + 1; i < b; i++) {
                int ip = steps.get(i).index();
                double distance = segmentDistance(ip % width, ip / width, ia % width, ia / width, ib % width, ib / width);
                if (distance > worst) {
                    worst = distance;
                    worstAt = i;
                }
            }
            if (worst > SIMPLIFY_CELLS) {
                keep[worstAt - from] = true;
                pending.push(new int[]{a, worstAt});
                pending.push(new int[]{worstAt, b});
            }
        }
        for (int i = 1; i < keep.length; i++) {
            if (keep[i]) {
                kept.add(from + i);
            }
        }
    }

    static double segmentDistance(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double lengthSquared = dx * dx + dz * dz;
        double t = lengthSquared <= 0.0 ? 0.0
                : Math.max(0.0, Math.min(1.0, ((px - ax) * dx + (pz - az) * dz) / lengthSquared));
        return Math.hypot(px - (ax + t * dx), pz - (az + t * dz));
    }
}
