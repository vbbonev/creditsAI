package com.creditsai.exception;

/**
 * Thrown when an uploaded file is larger than the allowed maximum. Mapped to HTTP 413. The message
 * is shown to the user, so write it in plain English.
 */
public class PayloadTooLargeException extends RuntimeException {

    /**
     * Creates the exception with a user-readable message.
     *
     * @param message the text shown to the user, naming the file and the allowed size
     */
    public PayloadTooLargeException(String message) {
        super(message);
    }
}
