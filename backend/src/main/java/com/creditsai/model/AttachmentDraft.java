package com.creditsai.model;

/**
 * The metadata of a file that has just been stored, before it is attached to its owner.
 *
 * @param fileName the sanitized file name
 * @param contentType the detected content type
 * @param sizeBytes the size in bytes
 * @param sha256 the lower-case hex SHA-256 of the content
 * @param uploadedBy the username of the uploader
 */
public record AttachmentDraft(String fileName, String contentType, long sizeBytes, String sha256,
                              String uploadedBy) {
}
