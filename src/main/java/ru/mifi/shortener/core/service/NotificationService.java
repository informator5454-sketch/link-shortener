package ru.mifi.shortener.core.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationService {

    private final Map<UUID, List<String>> inbox = new ConcurrentHashMap<>();

    public void notify(UUID userId, String message) {
        inbox.computeIfAbsent(userId, id -> new ArrayList<>()).add(message);
    }

    public List<String> pollAll(UUID userId) {
        List<String> messages = inbox.remove(userId);
        return messages == null ? List.of() : messages;
    }
}
