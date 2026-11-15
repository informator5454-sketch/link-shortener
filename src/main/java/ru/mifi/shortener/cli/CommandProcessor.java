package ru.mifi.shortener.cli;

import java.io.PrintStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import ru.mifi.shortener.core.exception.ShortenerException;
import ru.mifi.shortener.core.exception.ValidationException;
import ru.mifi.shortener.core.model.AppConfig;
import ru.mifi.shortener.core.model.ShortLink;
import ru.mifi.shortener.core.service.BrowserOpener;
import ru.mifi.shortener.core.service.LinkService;
import ru.mifi.shortener.core.service.NotificationService;
import ru.mifi.shortener.core.service.UserRegistry;

public class CommandProcessor {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        .withZone(ZoneId.systemDefault());

    private final LinkService linkService;
    private final UserRegistry users;
    private final NotificationService notifications;
    private final BrowserOpener browser;
    private final AppConfig config;
    private final PrintStream out;

    private UUID currentUser;

    public CommandProcessor(LinkService linkService, UserRegistry users, NotificationService notifications,
                            BrowserOpener browser, AppConfig config, PrintStream out) {
        this.linkService = linkService;
        this.users = users;
        this.notifications = notifications;
        this.browser = browser;
        this.config = config;
        this.out = out;
    }

    public boolean execute(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        try {
            if (linkService.isShortUrl(trimmed)) {
                open(trimmed);
            } else if (!dispatch(trimmed)) {
                return false;
            }
        } catch (ShortenerException e) {
            out.println("Ошибка: " + e.getMessage());
        }
        printNotifications();
        return true;
    }

    public UUID currentUser() {
        return currentUser;
    }

    private boolean dispatch(String line) {
        List<String> args = Arrays.asList(line.split("\\s+"));
        String command = args.get(0).toLowerCase(Locale.ROOT);
        List<String> rest = args.subList(1, args.size());
        switch (command) {
            case "exit", "quit" -> {
                out.println("До свидания!");
                return false;
            }
            case "help", "?" -> out.print(HelpText.TEXT);
            case "create" -> create(rest);
            case "open" -> open(requireArg(rest, 0, "open <короткая ссылка>"));
            case "list" -> list();
            case "info" -> info(requireArg(rest, 0, "info <короткая ссылка>"));
            case "limit" -> updateLimit(rest);
            case "delete" -> delete(requireArg(rest, 0, "delete <короткая ссылка>"));
            case "whoami" -> out.println(currentUser == null
                ? "Вы ещё не создавали ссылок. UUID появится при первом create."
                : "Ваш UUID: " + currentUser);
            case "login" -> login(requireArg(rest, 0, "login <UUID>"));
            case "logout" -> {
                currentUser = null;
                out.println("Вы вышли. При следующем create будет создан новый пользователь.");
            }
            case "config" -> printConfig();
            default -> out.println("Неизвестная команда «" + args.get(0) + "». Введите help для списка команд.");
        }
        return true;
    }

    private void create(List<String> args) {
        String url = requireArg(args, 0, "create <url> [лимит]");
        Integer limit = args.size() > 1 ? parseLimit(args.get(1)) : null;
        boolean newUser = currentUser == null;
        UUID owner = newUser ? users.registerNew() : currentUser;
        ShortLink link = linkService.create(owner, url, limit);
        currentUser = owner;
        if (newUser) {
            out.println("Создан новый пользователь. Ваш UUID: " + owner);
            out.println("Сохраните его, чтобы потом управлять ссылками: login " + owner);
        }
        out.println("Короткая ссылка: " + linkService.toShortUrl(link));
        out.println("Лимит переходов: " + link.getMaxClicks() + ", действует до "
            + DATE_TIME.format(link.getExpiresAt()));
    }

    private void open(String shortUrl) {
        String target = linkService.follow(shortUrl);
        if (browser.open(target)) {
            out.println("Открываю в браузере: " + target);
        } else {
            out.println("Не удалось открыть браузер. Исходная ссылка: " + target);
        }
    }

    private void list() {
        requireUser();
        List<ShortLink> links = linkService.listByOwner(currentUser);
        if (links.isEmpty()) {
            out.println("У вас нет активных ссылок. Создайте: create <url> [лимит]");
            return;
        }
        out.println(String.format("%-22s %-11s %-17s %s", "Короткая ссылка", "Переходы", "Действует до",
            "Исходная ссылка"));
        for (ShortLink link : links) {
            String clicks = link.getClicks() + "/" + link.getMaxClicks();
            String status = link.isLimitReached() ? "  (лимит исчерпан)" : "";
            out.println(String.format("%-22s %-11s %-17s %s%s", linkService.toShortUrl(link), clicks,
                DATE_TIME.format(link.getExpiresAt()), link.getOriginalUrl(), status));
        }
    }

    private void info(String shortUrl) {
        ShortLink link = linkService.info(shortUrl);
        out.println("Короткая ссылка: " + linkService.toShortUrl(link));
        out.println("Исходная ссылка: " + link.getOriginalUrl());
        out.println("Переходы: " + link.getClicks() + " из " + link.getMaxClicks()
            + (link.isLimitReached() ? " (лимит исчерпан)" : ", осталось " + link.remainingClicks()));
        out.println("Создана: " + DATE_TIME.format(link.getCreatedAt()));
        out.println("Действует до: " + DATE_TIME.format(link.getExpiresAt()));
        out.println("Владелец: " + (link.isOwnedBy(currentUser) ? "вы" : "другой пользователь"));
    }

    private void updateLimit(List<String> args) {
        requireUser();
        String shortUrl = requireArg(args, 0, "limit <короткая ссылка> <N>");
        int limit = parseLimit(requireArg(args, 1, "limit <короткая ссылка> <N>"));
        ShortLink link = linkService.updateLimit(currentUser, shortUrl, limit);
        out.println("Лимит для " + linkService.toShortUrl(link) + " изменён: " + link.getClicks() + " из "
            + link.getMaxClicks() + " переходов.");
    }

    private void delete(String shortUrl) {
        requireUser();
        linkService.delete(currentUser, shortUrl);
        out.println("Ссылка удалена.");
    }

    private void login(String rawUuid) {
        currentUser = users.requireExisting(rawUuid);
        out.println("Вы вошли как " + currentUser + ". Ваших ссылок: "
            + linkService.listByOwner(currentUser).size());
    }

    private void printConfig() {
        out.println("Домен: " + config.domain());
        out.println("Длина кода: " + config.codeLength());
        out.println("Время жизни ссылки: " + config.linkTtl().toMinutes() + " мин.");
        out.println("Лимит переходов по умолчанию: " + config.defaultMaxClicks());
        out.println("Максимальный лимит: " + config.maxClicksLimit());
        out.println("Интервал очистки: " + config.cleanupInterval().toSeconds() + " сек.");
    }

    private void printNotifications() {
        if (currentUser == null) {
            return;
        }
        for (String message : notifications.pollAll(currentUser)) {
            out.println("🔔 " + message);
        }
    }

    private void requireUser() {
        if (currentUser == null) {
            throw new ValidationException("Сначала создайте ссылку (create <url>) или войдите: login <UUID>");
        }
    }

    private static int parseLimit(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new ValidationException("Лимит переходов должен быть целым числом, а не «" + raw + "».");
        }
    }

    private static String requireArg(List<String> args, int index, String usage) {
        if (args.size() <= index) {
            throw new ValidationException("Недостаточно аргументов. Использование: " + usage);
        }
        return args.get(index);
    }
}
