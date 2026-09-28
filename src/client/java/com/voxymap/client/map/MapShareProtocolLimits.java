package com.voxymap.client.map;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

final class MapShareProtocolLimits {
    static final int MAX_MAPPING_ROWS = 65_536;
    static final int MAX_MAPPING_ROW_BYTES = 1 << 20;
    static final long MAX_MAPPING_BYTES = 64L << 20;
    static final int MAX_CHUNK_SECTIONS = 2_000_000;
    static final int MAX_ABS_SECTION_XZ = 2_000_000;
    static final int MAX_ABS_SECTION_Y = 131_072;
    static final long MAX_DECOMPRESSED_BYTES = 70L << 30;
    static final long MAX_ESTIMATED_BYTES = 1L << 40;

    private MapShareProtocolLimits() {
    }

    static int mappingCount(int value) throws IOException {
        if (value < 0 || value > MAX_MAPPING_ROWS) {
            throw new IOException("Invalid map-share mapping row count: " + value);
        }
        return value;
    }

    static int mappingRowLength(int value, long accumulatedBytes) throws IOException {
        if (value < 0 || value > MAX_MAPPING_ROW_BYTES || accumulatedBytes + value > MAX_MAPPING_BYTES) {
            throw new IOException("Invalid map-share mapping row length: " + value);
        }
        return value;
    }

    static int declaredSectionCount(int value) throws IOException {
        if (value < 0 || value > MAX_CHUNK_SECTIONS) {
            throw new IOException("Invalid map-share section count: " + value);
        }
        return value;
    }

    static long estimatedBytes(String value) throws IOException {
        try {
            return estimatedBytes(Long.parseLong(value));
        } catch (NumberFormatException malformed) {
            throw new IOException("Invalid map-share size estimate", malformed);
        }
    }

    static long estimatedBytes(long value) throws IOException {
        if (value < 0 || value > MAX_ESTIMATED_BYTES) {
            throw new IOException("Invalid map-share size estimate: " + value);
        }
        return value;
    }

    static void sectionIndex(int index, int declaredCount) throws IOException {
        if (index >= MAX_CHUNK_SECTIONS || (declaredCount > 0 && index >= declaredCount)) {
            throw new IOException("Map-share stream contains more sections than declared or allowed");
        }
    }

    static void coordinates(int x, int y, int z) throws IOException {
        if (Math.abs((long) x) > MAX_ABS_SECTION_XZ
                || Math.abs((long) z) > MAX_ABS_SECTION_XZ
                || Math.abs((long) y) > MAX_ABS_SECTION_Y) {
            throw new IOException("Map-share section coordinates are outside supported bounds");
        }
    }

    static InputStream limitDecompressed(InputStream input) {
        return limitDecompressed(input, MAX_DECOMPRESSED_BYTES);
    }

    static InputStream limitDecompressed(InputStream input, long limit) {
        return new LimitedInputStream(input, limit);
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private final long limit;
        private long consumed;

        private LimitedInputStream(InputStream input, long limit) {
            super(input);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) add(1);
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0) add(read);
            return read;
        }

        private void add(int bytes) throws IOException {
            consumed += bytes;
            if (consumed > limit) {
                throw new IOException("Map-share payload exceeds the decompressed size limit");
            }
        }
    }
}
