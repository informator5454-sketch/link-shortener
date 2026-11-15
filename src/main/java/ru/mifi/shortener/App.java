package ru.mifi.shortener;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Scanner;
import ru.mifi.shortener.cli.CommandProcessor;
import ru.mifi.shortener.core.exception.ShortenerException;
import ru.mifi.shortener.core.model.AppConfig;
import ru.mifi.shortener.core.service.CodeGenerator;
import ru.mifi.shortener.core.service.LinkService;
import ru.mifi.shortener.core.service.NotificationService;
import ru.mifi.shortener.core.service.UserRegistry;
import ru.mifi.shortener.infra.ConfigLoader;
import ru.mifi.shortener.infra.DesktopBrowserOpener;
import ru.mifi.shortener.infra.ExpiredLinkCleaner;
import ru.mifi.shortener.infra.InMemoryLinkRepository;

public final class App {

    private App() {
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        AppConfig config;
        try {
            config = ConfigLoader.load(args.length > 0 ? Path.of(args[0]) : null);
        } catch (ShortenerException e) {
            out.println("Ошибка настроек: " + e.getMessage());
            return;
        }

        NotificationService notifications = new NotificationService();
        LinkService linkService = new LinkService(new InMemoryLinkRepository(), config,
            new CodeGenerator(config.codeLength()), notifications, Clock.systemDefaultZone());
        CommandProcessor processor = new CommandProcessor(linkService, new UserRegistry(), notifications,
            new DesktopBrowserOpener(), config, out);

        try (ExpiredLinkCleaner cleaner = new ExpiredLinkCleaner(linkService, config.cleanupInterval().toSeconds())) {
            out.println("Сервис коротких ссылок. Введите help для списка команд.");
            Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
            boolean running = true;
            while (running) {
                out.print("> ");
                if (!scanner.hasNextLine()) {
                    break;
                }
                running = processor.execute(scanner.nextLine());
            }
        }
    }
}
