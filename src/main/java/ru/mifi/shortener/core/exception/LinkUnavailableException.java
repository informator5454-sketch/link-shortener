package ru.mifi.shortener.core.exception;

public class LinkUnavailableException extends ShortenerException {

    public LinkUnavailableException(String message) {
        super(message);
    }
}
