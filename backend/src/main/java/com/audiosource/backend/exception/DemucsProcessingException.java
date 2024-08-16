package com.audiosource.backend.exception;

public class DemucsProcessingException extends RuntimeException {
    public DemucsProcessingException(String message) {
        super(message);
    }

    public DemucsProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
