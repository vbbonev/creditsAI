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
 * Metadata of a file attached to a {@link Credit}; the bytes themselves live in the file storage.
 * It is created and removed only through {@link Credit#addAttachment} and
 * {@link Credit#removeAttachment}, which enforce the rules of the whole collection.
 */
@Entity
@Table(name = "credit_attachments")
@Getter
@NoArgsConstructor
@ToString(exclude = "credit")
public class CreditAttachment extends AttachmentBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_id", nullable = false, updatable = false)
    private Credit credit;

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
    public CreditAttachment(String fileName, String contentType, long sizeBytes, String sha256, String uploadedBy) {
        super(fileName, contentType, sizeBytes, sha256, uploadedBy);
    }

    /**
     * Links this attachment to its owner; called only by {@link Credit#addAttachment}.
     *
     * @param owner the credit that owns the attachment
     */
    void assignTo(Credit owner) {
        this.credit = owner;
    }
}
