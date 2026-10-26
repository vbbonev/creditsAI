package com.creditsai.exception;

/**
 * Thrown when a requested record does not exist or is archived. Mapped to HTTP 404.
 * The message is shown to the user, so write it in plain English.
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Creates the exception with a user-readable message.
     *
     * @param message the text shown to the user, e.g. "Client 42 was not found."
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
