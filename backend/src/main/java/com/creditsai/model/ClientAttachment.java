package com.creditsai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Metadata of a file attached to a {@link Client}; the bytes themselves live in the file storage.
 * It is created and removed only through {@link Client#addAttachment} and
 * {@link Client#removeAttachment}, which enforce the rules of the whole collection.
 */
@Entity
@Table(name = "client_attachments")
@Getter
@NoArgsConstructor
@ToString(exclude = "client")
public class ClientAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false, updatable = false)
    private Client client;

    /** The sanitized name (NFC, lower case), also the name on disk; unique per client. */
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
     * Creates the metadata of a stored file (Builder pattern, as the fields are many and all
     * required).
     *
     * @param fileName the sanitized file name
     * @param contentType the detected content type
     * @param sizeBytes the size in bytes
     * @param sha256 the lower-case hex SHA-256 of the content
     * @param uploadedBy the username of the uploader
     */
    @Builder
    public ClientAttachment(String fileName, String contentType, long sizeBytes, String sha256, String uploadedBy) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.uploadedBy = uploadedBy;
    }

    /**
     * Links this attachment to its owner; called only by {@link Client#addAttachment}.
     *
     * @param owner the client that owns the attachment
     */
    void assignTo(Client owner) {
        this.client = owner;
    }
}
