package ru.mifi.shortener.core.service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import ru.mifi.shortener.core.exception.ValidationException;

public class UserRegistry {

    private final Set<UUID> users = ConcurrentHashMap.newKeySet();

    public UUID registerNew() {
        UUID id = UUID.randomUUID();
        users.add(id);
        return id;
    }

    public boolean exists(UUID id) {
        return users.contains(id);
    }

    public UUID requireExisting(String raw) {
        UUID id;
        try {
            id = UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(
                "«" + raw + "» не похож на UUID. Пример: 3f2b8c1e-5d4a-4c3b-9e7f-1a2b3c4d5e6f");
        }
        if (!exists(id)) {
            throw new ValidationException("Пользователь " + id + " не найден.");
        }
        return id;
    }
}
