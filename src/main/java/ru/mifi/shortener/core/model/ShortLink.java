package ru.mifi.shortener.core.model;

import java.time.Instant;
import java.util.UUID;

public class ShortLink {

    private final String code;
    private final String originalUrl;
    private final UUID ownerId;
    private final Instant createdAt;
    private final Instant expiresAt;
    private int maxClicks;
    private int clicks;

    public ShortLink(String code, String originalUrl, UUID ownerId, Instant createdAt, Instant expiresAt,
                     int maxClicks) {
        this.code = code;
        this.originalUrl = originalUrl;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.maxClicks = maxClicks;
    }

    public String getCode() {
        return code;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getMaxClicks() {
        return maxClicks;
    }

    public int getClicks() {
        return clicks;
    }

    public void setMaxClicks(int maxClicks) {
        this.maxClicks = maxClicks;
    }

    public void registerClick() {
        clicks++;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isLimitReached() {
        return clicks >= maxClicks;
    }

    public int remainingClicks() {
        return Math.max(0, maxClicks - clicks);
    }
}
