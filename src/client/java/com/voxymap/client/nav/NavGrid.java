package com.voxymap.client.nav;

public record NavGrid(int originX, int originZ, int cell, int width, int depth, byte[] kinds, short[] heights) {
    public static NavGrid empty(int originX, int originZ, int cell, int width, int depth) {
        return new NavGrid(originX, originZ, cell, width, depth, new byte[width * depth], new short[width * depth]);
    }

    public int index(int cellX, int cellZ) {
        return cellZ * width + cellX;
    }

    public boolean contains(int cellX, int cellZ) {
        return cellX >= 0 && cellZ >= 0 && cellX < width && cellZ < depth;
    }

    public int cellX(double blockX) {
        return (int) Math.floor((blockX - originX) / cell);
    }

    public int cellZ(double blockZ) {
        return (int) Math.floor((blockZ - originZ) / cell);
    }

    public double centerX(int cellX) {
        return originX + cellX * (double) cell + cell / 2.0;
    }

    public double centerZ(int cellZ) {
        return originZ + cellZ * (double) cell + cell / 2.0;
    }

    public byte kind(int index) {
        return kinds[index];
    }

    public int height(int index) {
        return heights[index];
    }

    public void set(int cellX, int cellZ, byte kind, int height) {
        int index = index(cellX, cellZ);
        kinds[index] = kind;
        heights[index] = (short) height;
    }
}
