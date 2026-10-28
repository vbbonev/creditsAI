package com.creditsai.dto;

import org.springframework.core.io.InputStreamSource;

/**
 * DTO pattern: one file received by the controller, handed to the service without exposing the web
 * layer's multipart types. The source can be opened more than once.
 *
 * @param fileName the file name as sent by the browser (not yet sanitized)
 * @param sizeBytes the size in bytes
 * @param source gives access to the content
 */
public record AttachmentUpload(String fileName, long sizeBytes, InputStreamSource source) {
}
