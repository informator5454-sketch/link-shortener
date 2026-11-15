package ru.mifi.shortener.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mifi.shortener.core.exception.ValidationException;
import ru.mifi.shortener.core.model.AppConfig;

class ConfigLoaderTest {

    @TempDir
    Path dir;

    @Test
    void readsAllParametersFromFile() throws Exception {
        Path file = dir.resolve("app.properties");
        Files.writeString(file, """
                link.domain=sho.rt
                link.code-length=8
                link.ttl-minutes=60
                link.default-max-clicks=5
                link.max-clicks-limit=50
                cleanup.interval-seconds=10
                """);

        AppConfig config = ConfigLoader.load(file);

        assertEquals("sho.rt", config.domain());
        assertEquals(8, config.codeLength());
        assertEquals(Duration.ofHours(1), config.linkTtl());
        assertEquals(5, config.defaultMaxClicks());
        assertEquals(50, config.maxClicksLimit());
        assertEquals(Duration.ofSeconds(10), config.cleanupInterval());
    }

    @Test
    void loadsBundledConfigWhenNoFileGiven() {
        AppConfig config = ConfigLoader.load(null);

        assertEquals(Duration.ofDays(1), config.linkTtl());
    }

    @Test
    void rejectsMissingOrInvalidValues() throws Exception {
        Path broken = dir.resolve("broken.properties");
        Files.writeString(broken, """
                link.domain=sho.rt
                link.code-length=six
                """);

        assertThrows(ValidationException.class, () -> ConfigLoader.load(broken));
        assertThrows(ValidationException.class, () -> ConfigLoader.load(dir.resolve("missing.properties")));
    }
}
