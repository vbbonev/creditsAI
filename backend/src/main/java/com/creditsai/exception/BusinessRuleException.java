package com.creditsai.exception;

/**
 * Thrown when a request is valid but breaks a business rule, e.g. archiving a client that still
 * has an active credit. Mapped to HTTP 409. The message is shown to the user, so write it in plain
 * English.
 */
public class BusinessRuleException extends RuntimeException {

    /**
     * Creates the exception with a user-readable message.
     *
     * @param message the text shown to the user, explaining which rule was broken
     */
    public BusinessRuleException(String message) {
        super(message);
    }
}
