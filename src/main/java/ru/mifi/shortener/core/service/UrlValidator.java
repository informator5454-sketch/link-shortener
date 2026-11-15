package ru.mifi.shortener.core.service;

import java.net.URI;
import java.net.URISyntaxException;
import ru.mifi.shortener.core.exception.ValidationException;

public final class UrlValidator {

    private static final int MAX_LENGTH = 2048;

    private UrlValidator() {
    }

    public static String validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("Не указана ссылка.");
        }
        String url = raw.trim();
        if (url.length() > MAX_LENGTH) {
            throw new ValidationException("Ссылка длиннее " + MAX_LENGTH + " символов.");
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new ValidationException("Ссылка должна начинаться с http:// или https://");
            }
            if (uri.getHost() == null || !uri.getHost().contains(".")) {
                throw new ValidationException("В ссылке не указан корректный адрес сайта: " + url);
            }
            return url;
        } catch (URISyntaxException e) {
            throw new ValidationException("Некорректная ссылка: " + url);
        }
    }
}
