package com.creditsai.storage;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The only file types that may be attached to a client. Each constant knows its detected media
 * type, a label for messages and the file extensions that are acceptable for it, so the type check
 * is data-driven and adding a type does not need any {@code if/else}.
 */
public enum AllowedFileType {

    PDF("application/pdf", "PDF", "pdf"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "DOCX", "docx"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "XLSX", "xlsx"),
    ODT("application/vnd.oasis.opendocument.text", "ODT", "odt"),
    TXT("text/plain", "TXT", "txt"),
    JPEG("image/jpeg", "JPEG", "jpg", "jpeg"),
    PNG("image/png", "PNG", "png"),
    GIF("image/gif", "GIF", "gif"),
    WEBP("image/webp", "WEBP", "webp"),
    HEIC("image/heic", "HEIC", "heic"),
    TIFF("image/tiff", "TIFF", "tif", "tiff");

    private final String mediaType;
    private final String label;
    private final List<String> extensions;

    AllowedFileType(String mediaType, String label, String... extensions) {
        this.mediaType = mediaType;
        this.label = label;
        this.extensions = List.of(extensions);
    }

    /**
     * Returns the media type stored for files of this type.
     *
     * @return the media type, e.g. {@code application/pdf}
     */
    public String mediaType() {
        return mediaType;
    }

    /**
     * Returns the short name used in user messages.
     *
     * @return the label, e.g. {@code PDF}
     */
    public String label() {
        return label;
    }

    /**
     * Finds the allowed type for a detected media type.
     *
     * @param detectedMediaType the media type found by content detection
     * @return the matching type, or empty if the media type is not allowed
     */
    public static Optional<AllowedFileType> fromMediaType(String detectedMediaType) {
        return Arrays.stream(values()).filter(t -> t.mediaType.equals(detectedMediaType)).findFirst();
    }

    /**
     * Checks whether a file name ends with one of the extensions of this type.
     *
     * @param fileName the sanitized file name
     * @return {@code true} if the extension (case-insensitive) is acceptable for this type
     */
    public boolean acceptsFileName(String fileName) {
        return extensions.contains(extensionOf(fileName));
    }

    /**
     * Returns the extension of a file name in lower case.
     *
     * @param fileName the file name
     * @return the text after the last dot, or an empty string if there is none
     */
    public static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Lists the labels of all allowed types for user messages.
     *
     * @return the labels joined by commas
     */
    public static String allLabels() {
        return String.join(", ", Arrays.stream(values()).map(AllowedFileType::label).toList());
    }
}
