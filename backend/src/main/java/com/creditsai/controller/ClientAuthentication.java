package com.creditsai.controller;

import com.creditsai.dto.AttachmentDownload;
import com.creditsai.dto.AttachmentUpload;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.multipart.MultipartFile;

/**
 * Small helpers shared by the controllers: who the caller is and how a multipart file becomes a
 * web-independent DTO. Not a bean; only static methods.
 */
final class ClientAuthentication {

    private ClientAuthentication() {
    }

    /**
     * Tells whether the caller has the ADMIN role.
     *
     * @param authentication the caller
     * @return {@code true} for ADMIN
     */
    static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    /**
     * Returns the caller's username: the JWT {@code preferred_username}, or the authentication
     * name when the claim is absent.
     *
     * @param authentication the caller
     * @return the username
     */
    static String username(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String preferred = jwt.getClaimAsString("preferred_username");
            if (preferred != null && !preferred.isBlank()) {
                return preferred;
            }
        }
        return authentication.getName();
    }

    /**
     * Converts a multipart file to the service-layer DTO.
     *
     * @param file the uploaded file
     * @return the DTO
     */
    static AttachmentUpload toUpload(MultipartFile file) {
        return new AttachmentUpload(file.getOriginalFilename(), file.getSize(), file);
    }

    /**
     * Builds the download response of an attachment, with headers that stop browsers from
     * interpreting the file (attachment disposition, {@code nosniff}, sandboxing CSP, no caching).
     *
     * @param file the file to send
     * @return the response streaming the file
     */
    static ResponseEntity<Resource> toDownloadResponse(AttachmentDownload file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(file.content());
    }
}
