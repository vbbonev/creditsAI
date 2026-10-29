package com.creditsai.model;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * The metadata every attachment has, whoever owns it; the bytes themselves live in the file
 * storage. The owner link is mapped by the subclasses ({@link ClientAttachment},
 * {@link CreditAttachment}), which are created and removed only through their owner.
 */
@MappedSuperclass
@Getter
@NoArgsConstructor
public abstract class AttachmentBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The sanitized name (NFC, lower case), also the name on disk; unique per owner. */
    @Column(name = "file_name", nullable = false, length = 255, updatable = false)
    private String fileName;

    /** The content type found by content detection, not the one declared by the uploader. */
    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    /** SHA-256 of the content, lower-case hex. */
    @Column(nullable = false, length = 64, updatable = false)
    private String sha256;

    /** The {@code preferred_username} of the uploader. */
    @Column(name = "uploaded_by", nullable = false, length = 255, updatable = false)
    private String uploadedBy;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    /**
     * Sets the metadata of a stored file.
     *
     * @param fileName the sanitized file name
     * @param contentType the detected content type
     * @param sizeBytes the size in bytes
     * @param sha256 the lower-case hex SHA-256 of the content
     * @param uploadedBy the username of the uploader
     */
    protected AttachmentBase(String fileName, String contentType, long sizeBytes, String sha256, String uploadedBy) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.uploadedBy = uploadedBy;
    }
}
