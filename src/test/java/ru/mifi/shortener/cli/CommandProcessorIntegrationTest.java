package ru.mifi.shortener.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.mifi.shortener.core.MutableClock;
import ru.mifi.shortener.core.model.AppConfig;
import ru.mifi.shortener.core.service.CodeGenerator;
import ru.mifi.shortener.core.service.LinkService;
import ru.mifi.shortener.core.service.NotificationService;
import ru.mifi.shortener.core.service.UserRegistry;
import ru.mifi.shortener.infra.InMemoryLinkRepository;

/**
 * Сценарии из ТЗ через консольный интерфейс, с поддельным браузером и управляемыми часами.
 */
class CommandProcessorIntegrationTest {

    private static final Pattern SHORT_URL = Pattern.compile("Короткая ссылка: (clck\\.ru/\\w+)");
    private static final Pattern USER_ID = Pattern.compile("Ваш UUID: ([0-9a-f-]{36})");

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final List<String> openedUrls = new ArrayList<>();
    private MutableClock clock;
    private CommandProcessor app;

    @BeforeEach
    void setUp() {
        AppConfig config = new AppConfig("clck.ru", 6, Duration.ofHours(1), 2, 100, Duration.ofSeconds(60));
        clock = new MutableClock(Instant.parse("2026-11-15T10:00:00Z"));
        NotificationService notifications = new NotificationService();
        LinkService service = new LinkService(new InMemoryLinkRepository(), config, new CodeGenerator(6),
            notifications, clock);
        app = new CommandProcessor(service, new UserRegistry(), notifications, url -> openedUrls.add(url), config,
            new PrintStream(buffer, true, StandardCharsets.UTF_8));
    }

    private String run(String command) {
        buffer.reset();
        app.execute(command);
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static String find(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        assertTrue(matcher.find(), "не найдено в выводе: " + text);
        return matcher.group(1);
    }

    @Test
    void createOpenAndBlockAfterLimit() {
        String created = run("create https://www.baeldung.com/java-9-http-client");
        String shortUrl = find(SHORT_URL, created);
        assertTrue(created.contains("Создан новый пользователь"));

        assertTrue(run(shortUrl).contains("Открываю в браузере"));
        String second = run("open " + shortUrl);
        assertTrue(second.contains("🔔 Лимит переходов"));
        assertTrue(run(shortUrl).contains("Ошибка: Лимит переходов"));
        assertEquals(List.of("https://www.baeldung.com/java-9-http-client",
            "https://www.baeldung.com/java-9-http-client"), openedUrls);
    }

    @Test
    void expiredLinkIsDeletedAndOwnerNotified() {
        String shortUrl = find(SHORT_URL, run("create https://ya.ru 10"));

        clock.advance(Duration.ofHours(2));
        String output = run(shortUrl);

        assertTrue(output.contains("истёк"));
        assertTrue(output.contains("🔔"));
        assertTrue(run("list").contains("нет активных ссылок"));
    }

    @Test
    void otherUserCannotManageLinkButCanOpenIt() {
        String created = run("create https://ya.ru 5");
        String shortUrl = find(SHORT_URL, created);
        String ownerId = find(USER_ID, created);

        run("logout");
        run("create https://example.com");
        assertTrue(run("delete " + shortUrl).contains("только её создатель"));
        assertTrue(run("limit " + shortUrl + " 50").contains("только её создатель"));
        assertTrue(run(shortUrl).contains("Открываю"));

        run("login " + ownerId);
        assertTrue(run("limit " + shortUrl + " 50").contains("1 из 50"));
        assertTrue(run("delete " + shortUrl).contains("удалена"));
    }

    @Test
    void invalidInputGivesFriendlyErrors() {
        assertTrue(run("list").contains("Сначала создайте ссылку"));
        assertTrue(run("create not-a-url").contains("http://"));
        assertTrue(run("create https://ya.ru many").contains("целым числом"));
        assertTrue(run("login 123").contains("не похож на UUID"));
        assertTrue(run("jump").contains("Неизвестная команда"));
        assertTrue(run("help").contains("create <url>"));
        assertFalse(app.execute("exit"));
    }
}
