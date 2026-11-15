package ru.mifi.shortener.core.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import ru.mifi.shortener.core.exception.AccessDeniedException;
import ru.mifi.shortener.core.exception.LinkUnavailableException;
import ru.mifi.shortener.core.exception.ValidationException;
import ru.mifi.shortener.core.model.AppConfig;
import ru.mifi.shortener.core.model.ShortLink;
import ru.mifi.shortener.core.repository.LinkRepository;

public class LinkService {

    private static final int MAX_GENERATION_ATTEMPTS = 100;

    private final LinkRepository repository;
    private final AppConfig config;
    private final CodeGenerator generator;
    private final NotificationService notifications;
    private final Clock clock;

    public LinkService(LinkRepository repository, AppConfig config, CodeGenerator generator,
                       NotificationService notifications, Clock clock) {
        this.repository = repository;
        this.config = config;
        this.generator = generator;
        this.notifications = notifications;
        this.clock = clock;
    }

    public synchronized ShortLink create(UUID ownerId, String url, Integer maxClicks) {
        String validUrl = UrlValidator.validate(url);
        int limit = maxClicks == null ? config.defaultMaxClicks() : validateLimit(maxClicks);
        Instant now = clock.instant();
        ShortLink link = new ShortLink(generateUniqueCode(), validUrl, ownerId, now, now.plus(config.linkTtl()),
            limit);
        repository.save(link);
        return link;
    }

    /**
     * Переход по ссылке: проверяет срок и лимит, учитывает переход и возвращает исходный URL.
     */
    public synchronized String follow(String shortLinkOrCode) {
        String code = extractCode(shortLinkOrCode);
        ShortLink link = repository.findByCode(code)
            .orElseThrow(() -> new LinkUnavailableException("Ссылка " + toShortUrl(code) + " не найдена."));

        if (link.isExpired(clock.instant())) {
            expire(link);
            throw new LinkUnavailableException("Срок действия ссылки " + toShortUrl(code)
                + " истёк, она удалена. Создайте новую: create <url>");
        }
        if (link.isLimitReached()) {
            throw new LinkUnavailableException("Лимит переходов по ссылке " + toShortUrl(code)
                + " исчерпан (" + link.getMaxClicks() + "). Создайте новую: create <url>");
        }

        link.registerClick();
        if (link.isLimitReached()) {
            notifications.notify(link.getOwnerId(), "Лимит переходов по ссылке " + toShortUrl(code)
                + " исчерпан (" + link.getMaxClicks() + " из " + link.getMaxClicks()
                + "). Ссылка больше недоступна, создайте новую или увеличьте лимит: limit "
                + toShortUrl(code) + " <N>");
        }
        return link.getOriginalUrl();
    }

    public synchronized List<ShortLink> listByOwner(UUID ownerId) {
        removeExpired();
        return repository.findByOwner(ownerId).stream()
            .sorted(Comparator.comparing(ShortLink::getCreatedAt))
            .toList();
    }

    public synchronized ShortLink info(String shortLinkOrCode) {
        return findActive(extractCode(shortLinkOrCode));
    }

    public synchronized ShortLink updateLimit(UUID requesterId, String shortLinkOrCode, int newLimit) {
        ShortLink link = findActive(extractCode(shortLinkOrCode));
        requireOwner(link, requesterId);
        validateLimit(newLimit);
        if (newLimit < link.getClicks()) {
            throw new ValidationException("Новый лимит (" + newLimit + ") меньше уже совершённых переходов ("
                + link.getClicks() + ").");
        }
        link.setMaxClicks(newLimit);
        return link;
    }

    public synchronized void delete(UUID requesterId, String shortLinkOrCode) {
        ShortLink link = findActive(extractCode(shortLinkOrCode));
        requireOwner(link, requesterId);
        repository.delete(link.getCode());
    }

    public synchronized int removeExpired() {
        Instant now = clock.instant();
        List<ShortLink> expired = repository.findAll().stream()
            .filter(link -> link.isExpired(now))
            .toList();
        expired.forEach(this::expire);
        return expired.size();
    }

    public String toShortUrl(ShortLink link) {
        return toShortUrl(link.getCode());
    }

    public String toShortUrl(String code) {
        return config.domain() + "/" + code;
    }

    public boolean isShortUrl(String text) {
        String value = stripScheme(text.trim());
        return value.startsWith(config.domain() + "/") && value.length() > config.domain().length() + 1;
    }

    private void expire(ShortLink link) {
        repository.delete(link.getCode());
        notifications.notify(link.getOwnerId(), "Срок действия ссылки " + toShortUrl(link)
            + " (" + link.getOriginalUrl() + ") истёк, ссылка удалена. Создайте новую: create <url>");
    }

    private ShortLink findActive(String code) {
        ShortLink link = repository.findByCode(code)
            .orElseThrow(() -> new LinkUnavailableException("Ссылка " + toShortUrl(code) + " не найдена."));
        if (link.isExpired(clock.instant())) {
            expire(link);
            throw new LinkUnavailableException("Срок действия ссылки " + toShortUrl(code) + " истёк, она удалена.");
        }
        return link;
    }

    private void requireOwner(ShortLink link, UUID requesterId) {
        if (requesterId == null || !link.isOwnedBy(requesterId)) {
            throw new AccessDeniedException("Изменять и удалять ссылку " + toShortUrl(link)
                + " может только её создатель.");
        }
    }

    private int validateLimit(int limit) {
        if (limit < 1 || limit > config.maxClicksLimit()) {
            throw new ValidationException("Лимит переходов должен быть от 1 до " + config.maxClicksLimit() + ".");
        }
        return limit;
    }

    private String generateUniqueCode() {
        for (int i = 0; i < MAX_GENERATION_ATTEMPTS; i++) {
            String code = generator.next();
            if (!repository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Не удалось сгенерировать уникальный код ссылки.");
    }

    private String extractCode(String shortLinkOrCode) {
        if (shortLinkOrCode == null || shortLinkOrCode.isBlank()) {
            throw new ValidationException("Не указана короткая ссылка.");
        }
        String value = stripScheme(shortLinkOrCode.trim());
        String prefix = config.domain() + "/";
        return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }

    private static String stripScheme(String value) {
        if (value.startsWith("https://")) {
            return value.substring("https://".length());
        }
        if (value.startsWith("http://")) {
            return value.substring("http://".length());
        }
        return value;
    }
}
