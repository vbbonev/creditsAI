package com.creditsai.exception;

/**
 * Thrown when reading or writing an attachment file fails for a technical reason (disk full,
 * permissions, ...). Mapped to the generic HTTP 500 answer. The message is only logged, so it must
 * never contain an absolute path.
 */
public class AttachmentStorageException extends RuntimeException {

    /**
     * Creates the exception without a cause.
     *
     * @param message a log message without absolute paths
     */
    public AttachmentStorageException(String message) {
        super(message);
    }

    /**
     * Creates the exception wrapping the underlying I/O failure.
     *
     * @param message a log message without absolute paths
     * @param cause the underlying failure
     */
    public AttachmentStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
