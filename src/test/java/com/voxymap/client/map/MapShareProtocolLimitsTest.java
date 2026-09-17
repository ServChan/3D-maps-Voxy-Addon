package com.voxymap.client.map;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapShareProtocolLimitsTest {
    @Test
    void mappingCountsAndLengthsRejectNegativeAndOversizedValues() {
        assertThrows(IOException.class, () -> MapShareProtocolLimits.mappingCount(-1));
        assertThrows(IOException.class, () -> MapShareProtocolLimits.mappingCount(MapShareProtocolLimits.MAX_MAPPING_ROWS + 1));
        assertThrows(IOException.class, () -> MapShareProtocolLimits.mappingRowLength(-1, 0));
        assertThrows(IOException.class, () -> MapShareProtocolLimits.mappingRowLength(
                MapShareProtocolLimits.MAX_MAPPING_ROW_BYTES + 1, 0));
        assertThrows(IOException.class, () -> MapShareProtocolLimits.mappingRowLength(1, MapShareProtocolLimits.MAX_MAPPING_BYTES));
        assertThrows(IOException.class, () -> MapShareProtocolLimits.estimatedBytes("-1"));
        assertThrows(IOException.class, () -> MapShareProtocolLimits.estimatedBytes("not-a-number"));
        assertThrows(IOException.class, () -> MapShareProtocolLimits.estimatedBytes(
                Long.toString(MapShareProtocolLimits.MAX_ESTIMATED_BYTES + 1)));
        assertThrows(IOException.class, () -> MapShareProtocolLimits.estimatedBytes(-1L));
    }

    @Test
    void sectionCountsCoordinatesAndDeclaredLengthAreBounded() {
        assertThrows(IOException.class, () -> MapShareProtocolLimits.declaredSectionCount(-1));
        assertThrows(IOException.class, () -> MapShareProtocolLimits.declaredSectionCount(MapShareProtocolLimits.MAX_CHUNK_SECTIONS + 1));
        assertThrows(IOException.class, () -> MapShareProtocolLimits.sectionIndex(4, 4));
        assertThrows(IOException.class, () -> MapShareProtocolLimits.coordinates(
                MapShareProtocolLimits.MAX_ABS_SECTION_XZ + 1, 0, 0));
        assertDoesNotThrow(() -> MapShareProtocolLimits.coordinates(
                MapShareProtocolLimits.MAX_ABS_SECTION_XZ, MapShareProtocolLimits.MAX_ABS_SECTION_Y, 0));
    }

    @Test
    void decompressedStreamStopsAtTheConfiguredLimit() throws Exception {
        long limit = 1024;
        InputStream source = new RepeatingInputStream(limit + 1);
        InputStream limited = MapShareProtocolLimits.limitDecompressed(source, limit);
        byte[] buffer = new byte[128];
        long read = 0;
        IOException failure = null;
        try {
            while (true) {
                int count = limited.read(buffer);
                if (count < 0) break;
                read += count;
            }
        } catch (IOException expected) {
            failure = expected;
        }
        assertEquals(limit, read);
        if (failure == null) throw new AssertionError("Expected the decompressed byte limit to fail");
    }

    private static final class RepeatingInputStream extends InputStream {
        private long remaining;

        private RepeatingInputStream(long remaining) {
            this.remaining = remaining;
        }

        @Override
        public int read() {
            if (remaining-- <= 0) return -1;
            return 0;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            if (remaining <= 0) return -1;
            int count = (int) Math.min(length, remaining);
            remaining -= count;
            return count;
        }
    }
}
