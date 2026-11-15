package ru.mifi.shortener.core.exception;

public class AccessDeniedException extends ShortenerException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
