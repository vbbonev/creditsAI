package com.creditsai.exception;

/**
 * Thrown when a request parameter is not acceptable, e.g. sorting by a field that is not on the
 * allow-list. Mapped to HTTP 400. The message is shown to the user, so write it in plain English.
 */
public class InvalidRequestException extends RuntimeException {

    /**
     * Creates the exception with a user-readable message.
     *
     * @param message the text shown to the user, explaining what is wrong with the request
     */
    public InvalidRequestException(String message) {
        super(message);
    }
}
