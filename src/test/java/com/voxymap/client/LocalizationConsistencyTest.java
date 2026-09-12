package com.voxymap.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationConsistencyTest {
    private static final Path LANG_DIR = Path.of("src/main/resources/assets/voxymap/lang");
    private static final Set<String> REQUIRED_LOCALES = Set.of("en_us", "ru_ru", "es_es", "es_mx");

    @Test
    void requiredLocalesContainTheSameKeys() throws IOException {
        JsonObject reference = readLocale("en_us");
        for (String locale : REQUIRED_LOCALES) {
            assertEquals(reference.keySet(), readLocale(locale).keySet(), locale + " translation keys differ");
        }
    }

    @Test
    void localeValuesAreNonBlankStrings() throws IOException {
        try (var files = Files.list(LANG_DIR)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                for (var entry : readJson(file).entrySet()) {
                    JsonElement value = entry.getValue();
                    assertTrue(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(), entry.getKey());
                    assertTrue(!value.getAsString().isBlank(), entry.getKey());
                }
            }
        }
    }

    private static JsonObject readLocale(String locale) throws IOException {
        return readJson(LANG_DIR.resolve(locale + ".json"));
    }

    private static JsonObject readJson(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
