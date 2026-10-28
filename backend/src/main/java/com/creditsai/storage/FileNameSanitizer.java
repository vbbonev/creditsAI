package com.creditsai.storage;

import com.creditsai.exception.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Turns the file name sent by the client into a safe name for the file system. Only the last path
 * segment is kept, the name is NFC-normalized and lower-cased (so names are unique ignoring case), and names that could escape the client folder or
 * confuse the file system are rejected.
 */
public final class FileNameSanitizer {

    /** The longest file name most file systems accept, in UTF-8 bytes. */
    static final int MAX_NAME_BYTES = 255;

    private FileNameSanitizer() {
    }

    /**
     * Sanitizes an uploaded file name.
     *
     * @param rawName the name as sent by the browser, may contain a path
     * @return the safe file name, NFC-normalized and in lower case; this is the name that is stored
     * @throws InvalidRequestException if the name is missing, a dot name, contains control
     *         characters or is longer than 255 bytes in UTF-8
     */
    public static String sanitize(String rawName) {
        if (rawName == null) {
            throw new InvalidRequestException("An uploaded file has no name.");
        }
        int separator = Math.max(rawName.lastIndexOf('/'), rawName.lastIndexOf('\\'));
        // Lower case (locale independent) makes names unique ignoring case; NFC again, as lowering can
        // change the composition. The result is the stored name (database, disk, API).
        String name = Normalizer.normalize(
                Normalizer.normalize(rawName.substring(separator + 1), Normalizer.Form.NFC)
                        .toLowerCase(Locale.ROOT), Normalizer.Form.NFC).strip();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            throw new InvalidRequestException("An uploaded file has no valid name.");
        }
        if (name.chars().anyMatch(Character::isISOControl)) {
            throw new InvalidRequestException("A file name must not contain control characters.");
        }
        if (name.chars().anyMatch(c -> Character.getType(c) == Character.FORMAT
                || c == '\u2028' || c == '\u2029')) {
            throw new InvalidRequestException(
                    "A file name must not contain invisible or direction-changing characters.");
        }
        if (name.getBytes(StandardCharsets.UTF_8).length > MAX_NAME_BYTES) {
            throw new InvalidRequestException("A file name must be at most 255 bytes long.");
        }
        return name;
    }
}
