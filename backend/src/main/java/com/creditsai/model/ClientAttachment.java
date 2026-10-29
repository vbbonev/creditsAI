package com.creditsai.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

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
public class ClientAttachment extends AttachmentBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false, updatable = false)
    private Client client;

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
        super(fileName, contentType, sizeBytes, sha256, uploadedBy);
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
