package com.voxymap.client.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicPropertiesWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void coalescesSnapshotsAndKeepsThePreviousValidFileAsBackup() throws Exception {
        Path target = tempDir.resolve("config.properties");
        Path backup = tempDir.resolve("config.properties.bak");
        Path temporary = tempDir.resolve("config.properties.tmp");

        AtomicPropertiesWriter.save(target, backup, temporary, properties("value", "first"), "test");
        AtomicPropertiesWriter.flush();
        assertEquals("first", load(target).getProperty("value"));
        assertFalse(Files.exists(backup));

        AtomicPropertiesWriter.save(target, backup, temporary, properties("value", "obsolete"), "test");
        AtomicPropertiesWriter.save(target, backup, temporary, properties("value", "latest"), "test");
        AtomicPropertiesWriter.flush();

        assertEquals("latest", load(target).getProperty("value"));
        assertEquals("first", load(backup).getProperty("value"));
        assertFalse(Files.exists(temporary));
    }

    private static Properties properties(String key, String value) {
        Properties properties = new Properties();
        properties.setProperty(key, value);
        return properties;
    }

    private static Properties load(Path path) throws Exception {
        assertTrue(Files.isRegularFile(path));
        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
