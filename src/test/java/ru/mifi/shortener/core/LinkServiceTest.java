package ru.mifi.shortener.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.mifi.shortener.core.exception.AccessDeniedException;
import ru.mifi.shortener.core.exception.LinkUnavailableException;
import ru.mifi.shortener.core.exception.ValidationException;
import ru.mifi.shortener.core.model.AppConfig;
import ru.mifi.shortener.core.model.ShortLink;
import ru.mifi.shortener.core.service.CodeGenerator;
import ru.mifi.shortener.core.service.LinkService;
import ru.mifi.shortener.core.service.NotificationService;
import ru.mifi.shortener.infra.InMemoryLinkRepository;

class LinkServiceTest {

    private static final String URL = "https://www.baeldung.com/java-9-http-client";

    private MutableClock clock;
    private NotificationService notifications;
    private LinkService service;
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        setUpWithTtl(Duration.ofDays(1));
    }

    private void setUpWithTtl(Duration ttl) {
        AppConfig config = new AppConfig("clck.ru", 6, ttl, 3, 100, Duration.ofSeconds(60));
        clock = new MutableClock(Instant.parse("2026-11-15T10:00:00Z"));
        notifications = new NotificationService();
        service = new LinkService(new InMemoryLinkRepository(), config, new CodeGenerator(6), notifications, clock);
    }

    @Test
    void sameUrlGivesDifferentLinksForDifferentUsers() {
        ShortLink first = service.create(alice, URL, null);
        ShortLink second = service.create(bob, URL, null);

        assertNotEquals(first.getCode(), second.getCode());
        assertEquals(URL, service.follow(service.toShortUrl(first)));
        assertEquals(URL, service.follow(service.toShortUrl(second)));
    }

    @Test
    void generatedCodesAreUniqueAndUseConfiguredLength() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            ShortLink link = service.create(alice, URL, null);
            assertEquals(6, link.getCode().length());
            assertTrue(link.getCode().matches("[a-zA-Z0-9]+"));
            codes.add(link.getCode());
        }
        assertEquals(500, codes.size());
    }

    @Test
    void userCanHaveSeveralLinksAndSeesOnlyOwn() {
        service.create(alice, URL, null);
        service.create(alice, "https://ya.ru", null);
        service.create(bob, "https://example.com", null);

        assertEquals(2, service.listByOwner(alice).size());
        assertEquals(1, service.listByOwner(bob).size());
    }

    @Test
    void defaultLimitIsTakenFromConfig() {
        assertEquals(3, service.create(alice, URL, null).getMaxClicks());
    }

    @Test
    void linkIsBlockedWhenClickLimitReached() {
        ShortLink link = service.create(alice, URL, 2);
        String shortUrl = service.toShortUrl(link);

        service.follow(shortUrl);
        service.follow(shortUrl);

        LinkUnavailableException error = assertThrows(LinkUnavailableException.class, () -> service.follow(shortUrl));
        assertTrue(error.getMessage().contains("Лимит переходов"));
        assertEquals(2, link.getClicks());
    }

    @Test
    void ownerIsNotifiedWhenLimitReached() {
        ShortLink link = service.create(alice, URL, 1);

        service.follow(link.getCode());

        List<String> messages = notifications.pollAll(alice);
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("исчерпан"));
        assertTrue(notifications.pollAll(alice).isEmpty());
    }

    @Test
    void linkExpiresAfterOneDay() {
        ShortLink link = service.create(alice, URL, null);

        clock.advance(Duration.ofHours(23));
        assertEquals(URL, service.follow(link.getCode()));

        clock.advance(Duration.ofHours(1));
        assertThrows(LinkUnavailableException.class, () -> service.follow(link.getCode()));
        assertTrue(service.listByOwner(alice).isEmpty());
    }

    @Test
    void linkExpiresAfterOneHourWhenConfigured() {
        setUpWithTtl(Duration.ofHours(1));
        ShortLink link = service.create(alice, URL, null);

        clock.advance(Duration.ofMinutes(59));
        assertEquals(URL, service.follow(link.getCode()));

        clock.advance(Duration.ofMinutes(1));
        assertThrows(LinkUnavailableException.class, () -> service.follow(link.getCode()));
    }

    @Test
    void cleanupRemovesExpiredLinksAndNotifiesOwner() {
        service.create(alice, URL, null);
        service.create(bob, URL, null);
        clock.advance(Duration.ofHours(12));
        service.create(alice, "https://ya.ru", null);

        clock.advance(Duration.ofHours(12));
        int removed = service.removeExpired();

        assertEquals(2, removed);
        assertEquals(1, service.listByOwner(alice).size());
        assertTrue(notifications.pollAll(alice).get(0).contains("истёк"));
        assertTrue(notifications.pollAll(bob).get(0).contains("истёк"));
    }

    @Test
    void onlyOwnerCanChangeLimitOrDelete() {
        ShortLink link = service.create(alice, URL, null);

        assertThrows(AccessDeniedException.class, () -> service.updateLimit(bob, link.getCode(), 50));
        assertThrows(AccessDeniedException.class, () -> service.delete(bob, link.getCode()));

        service.updateLimit(alice, link.getCode(), 50);
        assertEquals(50, link.getMaxClicks());
        service.delete(alice, link.getCode());
        assertThrows(LinkUnavailableException.class, () -> service.follow(link.getCode()));
    }

    @Test
    void increasingLimitUnblocksLink() {
        ShortLink link = service.create(alice, URL, 1);
        service.follow(link.getCode());
        assertTrue(link.isLimitReached());

        service.updateLimit(alice, link.getCode(), 2);

        assertFalse(link.isLimitReached());
        assertEquals(URL, service.follow(link.getCode()));
    }

    @Test
    void limitValidation() {
        ShortLink link = service.create(alice, URL, 5);
        service.follow(link.getCode());
        service.follow(link.getCode());

        assertThrows(ValidationException.class, () -> service.create(alice, URL, 0));
        assertThrows(ValidationException.class, () -> service.create(alice, URL, 101));
        assertThrows(ValidationException.class, () -> service.updateLimit(alice, link.getCode(), 1));
    }

    @Test
    void invalidUrlsAreRejected() {
        assertThrows(ValidationException.class, () -> service.create(alice, "", null));
        assertThrows(ValidationException.class, () -> service.create(alice, "baeldung.com", null));
        assertThrows(ValidationException.class, () -> service.create(alice, "ftp://files.example.com", null));
        assertThrows(ValidationException.class, () -> service.create(alice, "https://localhost", null));
        assertThrows(ValidationException.class, () -> service.create(alice, "https://exa mple.com", null));
    }

    @Test
    void unknownLinkIsReported() {
        assertThrows(LinkUnavailableException.class, () -> service.follow("clck.ru/nothing"));
    }

    @Test
    void recognizesShortUrlsWithAndWithoutScheme() {
        assertTrue(service.isShortUrl("clck.ru/AbC123"));
        assertTrue(service.isShortUrl("https://clck.ru/AbC123"));
        assertFalse(service.isShortUrl("clck.ru/"));
        assertFalse(service.isShortUrl("create https://ya.ru"));
    }
}
