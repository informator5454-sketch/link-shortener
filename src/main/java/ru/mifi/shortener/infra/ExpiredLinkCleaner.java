package ru.mifi.shortener.infra;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import ru.mifi.shortener.core.service.LinkService;

public class ExpiredLinkCleaner implements AutoCloseable {

    private final ScheduledExecutorService executor;

    public ExpiredLinkCleaner(LinkService linkService, long intervalSeconds) {
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "expired-link-cleaner");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(linkService::removeExpired, intervalSeconds, intervalSeconds,
            TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
