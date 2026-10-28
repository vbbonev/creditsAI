package com.creditsai.dto;

import java.time.Instant;

/**
 * DTO pattern: the metadata of a client's attachment as returned by the API.
 *
 * @param id the attachment id
 * @param fileName the file name
 * @param contentType the content type found by content detection
 * @param sizeBytes the size in bytes
 * @param uploadedBy the username of the uploader
 * @param uploadedAt when the file was uploaded
 */
public record AttachmentResponse(
        Long id,
        String fileName,
        String contentType,
        long sizeBytes,
        String uploadedBy,
        Instant uploadedAt) {
}
