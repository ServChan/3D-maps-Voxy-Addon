package com.voxymap.client.map;

/**
 * Finds the connected pocket of passable space the player is standing in (a room, a cave, a
 * building interior) via a bounded flood fill, and derives a per-column "ceiling" height from it.
 *
 * <p>This intentionally does NOT use a fixed XZ radius or a single {@code blockY > playerY} cutoff:
 * both cut through solid terrain and unrelated caves that merely happen to be nearby. Instead, only
 * cells actually reachable from the player without crossing a solid block are considered part of the
 * room, so a cave on the other side of a wall, or open sky/terrain above, never gets touched.
 *
 * <p>If the flood fill is not fully contained within the search window (it reaches the window's edge,
 * or exceeds the cell budget), the space is treated as open/too large and {@link Result#OPEN} is
 * returned — the caller must not cut anything in that case.
 */
public final class RoomCutawayComputer {
    public static final int MAX_CELLS = 12000;
    public static final int HORIZONTAL_RADIUS = 48;
    public static final int VERTICAL_RADIUS = 32;
    public static final int GRID_SIZE = HORIZONTAL_RADIUS * 2 + 1;
    private static final int VERTICAL_SIZE = VERTICAL_RADIUS * 2 + 1;
    public static final int NO_CEILING = Integer.MIN_VALUE;
    public static final float CEILING_MARGIN = 0.5f;

    private static final int[][] DIRECTIONS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    public static final int PASSABLE = 0;
    public static final int BLOCKED = 1;
    /** The client has no reliable data for this cell (chunk not loaded, e.g. on a multiplayer
     *  server). Must never be treated as passable air, or the flood fill leaks through it. */
    public static final int UNKNOWN = 2;

    @FunctionalInterface
    public interface CellClassifier {
        int classify(int x, int y, int z);
    }

    private RoomCutawayComputer() {
    }

    public static Result compute(int feetX, int feetY, int feetZ, CellClassifier classifier) {
        Search search = start(feetX, feetY, feetZ);
        while (!search.isComplete()) {
            search.advance(classifier, Integer.MAX_VALUE);
        }
        return search.result();
    }

    public static Search start(int feetX, int feetY, int feetZ) {
        return new Search(feetX, feetY, feetZ);
    }

    /** Incremental flood fill so client-world reads can be spread across screen ticks. */
    public static final class Search {
        private static final byte SEEN = 1;
        private static final byte REACHABLE = 2;

        private final int feetX;
        private final int feetY;
        private final int feetZ;
        private final byte[] cells = new byte[GRID_SIZE * GRID_SIZE * VERTICAL_SIZE];
        private final int[] queueX = new int[MAX_CELLS + 2];
        private final int[] queueY = new int[MAX_CELLS + 2];
        private final int[] queueZ = new int[MAX_CELLS + 2];
        private int queueHead;
        private int queueTail;
        private int visitedCount;
        private Result result;

        private Search(int feetX, int feetY, int feetZ) {
            this.feetX = feetX;
            this.feetY = feetY;
            this.feetZ = feetZ;
            seed(feetX, feetY, feetZ);
            seed(feetX, feetY + 1, feetZ);
        }

        /** Processes at most {@code nodeBudget} queued cells and returns whether the scan finished. */
        public boolean advance(CellClassifier classifier, int nodeBudget) {
            if (result != null) {
                return true;
            }
            if (nodeBudget <= 0) {
                return false;
            }

            int processed = 0;
            while (queueHead < queueTail && processed++ < nodeBudget) {
                int cx = queueX[queueHead];
                int cy = queueY[queueHead];
                int cz = queueZ[queueHead++];
                for (int[] dir : DIRECTIONS) {
                    int nx = cx + dir[0];
                    int ny = cy + dir[1];
                    int nz = cz + dir[2];

                    if (Math.abs(nx - feetX) > HORIZONTAL_RADIUS || Math.abs(nz - feetZ) > HORIZONTAL_RADIUS
                            || Math.abs(ny - feetY) > VERTICAL_RADIUS) {
                        // Once reachable air leaves the bounded window, this cannot be a certified
                        // enclosed room. There is no value in scanning another 12k cells.
                        result = Result.OPEN;
                        return true;
                    }

                    int index = index(nx, ny, nz, feetX, feetY, feetZ);
                    if (cells[index] != 0) {
                        continue;
                    }
                    cells[index] = SEEN;
                    int state = classifier.classify(nx, ny, nz);
                    if (state == UNKNOWN) {
                        // We can't verify what's beyond an unloaded/unreported cell, so we can't
                        // certify the room is actually sealed there - treat it like leaving the
                        // search window entirely rather than risk guessing wrong.
                        result = Result.OPEN;
                        return true;
                    }
                    if (state == BLOCKED) {
                        continue;
                    }

                    cells[index] = REACHABLE;
                    visitedCount++;
                    if (visitedCount > MAX_CELLS) {
                        result = Result.OPEN;
                        return true;
                    }
                    enqueue(nx, ny, nz);
                }
            }

            if (queueHead >= queueTail) {
                result = buildResult(feetX, feetY, feetZ, cells);
                return true;
            }
            return false;
        }

        public boolean isComplete() {
            return result != null;
        }

        public Result result() {
            return result;
        }

        private void seed(int x, int y, int z) {
            int index = index(x, y, z, feetX, feetY, feetZ);
            if (cells[index] == 0) {
                cells[index] = REACHABLE;
                visitedCount++;
                enqueue(x, y, z);
            }
        }

        private void enqueue(int x, int y, int z) {
            queueX[queueTail] = x;
            queueY[queueTail] = y;
            queueZ[queueTail++] = z;
        }
    }

    private static int index(int x, int y, int z, int feetX, int feetY, int feetZ) {
        int gx = x - feetX + HORIZONTAL_RADIUS;
        int gy = y - feetY + VERTICAL_RADIUS;
        int gz = z - feetZ + HORIZONTAL_RADIUS;
        return (gx * GRID_SIZE + gz) * VERTICAL_SIZE + gy;
    }

    private static Result buildResult(int feetX, int feetY, int feetZ, byte[] cells) {
        int[][] topY = new int[GRID_SIZE][GRID_SIZE];
        for (int[] row : topY) {
            java.util.Arrays.fill(row, NO_CEILING);
        }

        for (int gx = 0; gx < GRID_SIZE; gx++) {
            for (int gz = 0; gz < GRID_SIZE; gz++) {
                int base = (gx * GRID_SIZE + gz) * VERTICAL_SIZE;
                for (int gy = VERTICAL_SIZE - 1; gy >= 0; gy--) {
                    if (cells[base + gy] == Search.REACHABLE) {
                        topY[gx][gz] = gy - VERTICAL_RADIUS + feetY;
                        break;
                    }
                }
            }
        }

        return new Result(true, topY, feetX - HORIZONTAL_RADIUS, feetZ - HORIZONTAL_RADIUS);
    }

    public static final class Result {
        public static final Result OPEN = new Result(false, null, 0, 0);

        public final boolean enclosed;
        public final int[][] topY;
        public final int minX;
        public final int minZ;

        private Result(boolean enclosed, int[][] topY, int minX, int minZ) {
            this.enclosed = enclosed;
            this.topY = topY;
            this.minX = minX;
            this.minZ = minZ;
        }

        /** Highest reachable-room block in this column, or {@link #NO_CEILING} if the column isn't part of the room. */
        public int ceilingAt(int worldX, int worldZ) {
            if (!enclosed) {
                return NO_CEILING;
            }
            int gx = worldX - minX;
            int gz = worldZ - minZ;
            if (gx < 0 || gx >= GRID_SIZE || gz < 0 || gz >= GRID_SIZE) {
                return NO_CEILING;
            }
            return topY[gx][gz];
        }
    }
}
