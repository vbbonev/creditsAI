package com.creditsai.storage;

import java.time.Instant;

/**
 * A file found in a client's storage folder.
 *
 * @param name the file name
 * @param lastModified when the file was last changed
 */
public record StoredFile(String name, Instant lastModified) {

    /** Prefix of the tombstone a file gets while its deletion is not yet committed. */
    public static final String TOMBSTONE_PREFIX = ".deleted-";

    /** Prefix of the temporary file an upload is written to before it gets its real name. */
    public static final String UPLOAD_PREFIX = ".upload-";

    /**
     * Tells whether a file name is one of the hidden leftovers the storage itself creates.
     *
     * @param fileName the file name
     * @return {@code true} for {@code .deleted-*} and {@code .upload-*.tmp}
     */
    public static boolean isLeftoverName(String fileName) {
        return isTombstoneName(fileName) || (fileName.startsWith(UPLOAD_PREFIX) && fileName.endsWith(".tmp"));
    }

    /**
     * Tells whether a file name is a deletion tombstone.
     *
     * @param fileName the file name
     * @return {@code true} for {@code .deleted-*}
     */
    public static boolean isTombstoneName(String fileName) {
        return fileName.startsWith(TOMBSTONE_PREFIX);
    }

    /**
     * Tells whether this file is a hidden leftover of the storage.
     *
     * @return {@code true} for a tombstone or a temporary upload file
     */
    public boolean isLeftover() {
        return isLeftoverName(name);
    }
}
