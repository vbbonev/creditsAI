package com.creditsai.dto;

import org.springframework.core.io.Resource;

/**
 * DTO pattern: a stored file ready to be streamed to the caller.
 *
 * @param fileName the file name to offer to the user
 * @param contentType the stored (detected) content type
 * @param sizeBytes the size in bytes
 * @param content the file content
 */
public record AttachmentDownload(String fileName, String contentType, long sizeBytes, Resource content) {
}
