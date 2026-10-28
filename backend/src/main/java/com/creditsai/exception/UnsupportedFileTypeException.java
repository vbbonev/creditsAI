package com.creditsai.exception;

/**
 * Thrown when the content of an uploaded file is not one of the allowed types, or does not agree
 * with its file extension. Mapped to HTTP 415. The message is shown to the user, so write it in
 * plain English.
 */
public class UnsupportedFileTypeException extends RuntimeException {

    /**
     * Creates the exception with a user-readable message.
     *
     * @param message the text shown to the user, naming the file and the allowed types
     */
    public UnsupportedFileTypeException(String message) {
        super(message);
    }
}
