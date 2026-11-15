package ru.mifi.shortener.infra;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import ru.mifi.shortener.core.exception.ValidationException;
import ru.mifi.shortener.core.model.AppConfig;

public final class ConfigLoader {

    public static final String FILE_NAME = "application.properties";

    private ConfigLoader() {
    }

    public static AppConfig load(Path explicitPath) {
        Path path = explicitPath != null ? explicitPath : Path.of(FILE_NAME);
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                return fromReader(reader);
            } catch (IOException e) {
                throw new ValidationException("Не удалось прочитать файл настроек " + path);
            }
        }
        if (explicitPath != null) {
            throw new ValidationException("Файл настроек не найден: " + explicitPath);
        }
        try (InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream(FILE_NAME)) {
            if (in == null) {
                throw new ValidationException("Не найден файл настроек " + FILE_NAME);
            }
            return fromReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ValidationException("Не удалось прочитать встроенный файл настроек");
        }
    }

    static AppConfig fromReader(Reader reader) throws IOException {
        Properties props = new Properties();
        props.load(reader);
        return new AppConfig(
            required(props, "link.domain"),
            intValue(props, "link.code-length"),
            Duration.ofMinutes(intValue(props, "link.ttl-minutes")),
            intValue(props, "link.default-max-clicks"),
            intValue(props, "link.max-clicks-limit"),
            Duration.ofSeconds(intValue(props, "cleanup.interval-seconds")));
    }

    private static String required(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new ValidationException("В настройках не задан параметр " + key);
        }
        return value.trim();
    }

    private static int intValue(Properties props, String key) {
        String value = required(props, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ValidationException("Параметр " + key + " должен быть целым числом, сейчас: " + value);
        }
    }
}
