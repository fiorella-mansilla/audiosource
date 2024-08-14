package com.audiosource.backend.exception;

public class S3UploadException extends Exception {

    public S3UploadException(String message) {
        super(message);
    }

    public S3UploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
