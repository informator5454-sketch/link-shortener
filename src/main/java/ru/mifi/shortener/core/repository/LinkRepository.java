package ru.mifi.shortener.core.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ru.mifi.shortener.core.model.ShortLink;

public interface LinkRepository {

    void save(ShortLink link);

    Optional<ShortLink> findByCode(String code);

    List<ShortLink> findByOwner(UUID ownerId);

    List<ShortLink> findAll();

    boolean existsByCode(String code);

    void delete(String code);
}
