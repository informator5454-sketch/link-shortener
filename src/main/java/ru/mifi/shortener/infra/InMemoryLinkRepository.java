package ru.mifi.shortener.infra;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import ru.mifi.shortener.core.model.ShortLink;
import ru.mifi.shortener.core.repository.LinkRepository;

public class InMemoryLinkRepository implements LinkRepository {

    private final Map<String, ShortLink> links = new ConcurrentHashMap<>();

    @Override
    public void save(ShortLink link) {
        links.put(link.getCode(), link);
    }

    @Override
    public Optional<ShortLink> findByCode(String code) {
        return Optional.ofNullable(links.get(code));
    }

    @Override
    public List<ShortLink> findByOwner(UUID ownerId) {
        return links.values().stream()
            .filter(link -> link.isOwnedBy(ownerId))
            .toList();
    }

    @Override
    public List<ShortLink> findAll() {
        return new ArrayList<>(links.values());
    }

    @Override
    public boolean existsByCode(String code) {
        return links.containsKey(code);
    }

    @Override
    public void delete(String code) {
        links.remove(code);
    }
}
