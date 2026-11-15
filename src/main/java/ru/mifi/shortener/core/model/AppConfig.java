package ru.mifi.shortener.core.model;

import java.time.Duration;
import ru.mifi.shortener.core.exception.ValidationException;

public record AppConfig(
    String domain,
    int codeLength,
    Duration linkTtl,
    int defaultMaxClicks,
    int maxClicksLimit,
    Duration cleanupInterval) {

    public AppConfig {
        if (domain == null || domain.isBlank()) {
            throw new ValidationException("В настройках не задан link.domain.");
        }
        if (codeLength < 4 || codeLength > 16) {
            throw new ValidationException("link.code-length должен быть от 4 до 16.");
        }
        if (linkTtl == null || linkTtl.isNegative() || linkTtl.isZero()) {
            throw new ValidationException("link.ttl-minutes должен быть больше нуля.");
        }
        if (maxClicksLimit < 1) {
            throw new ValidationException("link.max-clicks-limit должен быть больше нуля.");
        }
        if (defaultMaxClicks < 1 || defaultMaxClicks > maxClicksLimit) {
            throw new ValidationException("link.default-max-clicks должен быть от 1 до link.max-clicks-limit.");
        }
        if (cleanupInterval == null || cleanupInterval.isNegative() || cleanupInterval.isZero()) {
            throw new ValidationException("cleanup.interval-seconds должен быть больше нуля.");
        }
        domain = domain.trim();
    }
}
