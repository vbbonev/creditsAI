package com.creditsai.model;

import com.creditsai.exception.BusinessRuleException;
import com.creditsai.exception.ResourceNotFoundException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.CascadeType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A person or company registered in the system. Nothing is physically deleted: a client is
 * archived instead. The archive/restore rules live here (encapsulation), so the service only
 * loads the entity and calls the method.
 *
 * <p>Only the user-editable fields have setters; the archive state and the audit timestamps can
 * change only through {@link #archive()}, {@link #restore()} and Hibernate. The timestamps are set
 * by Hibernate's {@code @CreationTimestamp} / {@code @UpdateTimestamp}.
 */
@Entity
@Table(name = "clients")
@Getter
@NoArgsConstructor
public class Client implements AttachmentOwner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, length = 100)
    private String name;

    /** Free-text identifier (personal or company number, Bulgarian or foreign); not unique. */
    @Setter
    @Column(nullable = false, length = 50)
    private String identifier;

    @Setter
    @Column(nullable = false, length = 200)
    private String address;

    @Setter
    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Files attached to this client. Unlike clients, attachments are physically deleted
     * (orphan removal) - an approved exception to the soft-delete rule.
     */
    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ClientAttachment> attachments = new ArrayList<>();

    /** The credits of this client; read here only to check the archive rule, changed through {@link Credit}. */
    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "client")
    private List<Credit> credits = new ArrayList<>();

    /**
     * Returns the attachments as a read-only view; change them only with {@link #addAttachment}
     * and {@link #removeAttachment}.
     *
     * @return the attachments in no particular order
     */
    public List<ClientAttachment> getAttachments() {
        return Collections.unmodifiableList(attachments);
    }

    /** {@inheritDoc} */
    @Override
    public AttachmentOwnerType attachmentOwnerType() {
        return AttachmentOwnerType.CLIENT;
    }

    /** {@inheritDoc} */
    @Override
    public void ensureCanAttach(String fileName, long sizeBytes, int maxFiles, long maxTotalBytes) {
        AttachmentRules.ensureCanAttach(attachments, AttachmentOwnerType.CLIENT, fileName, sizeBytes, maxFiles,
                maxTotalBytes);
    }

    /** {@inheritDoc} */
    @Override
    public ClientAttachment attach(AttachmentDraft draft, int maxFiles, long maxTotalBytes) {
        ClientAttachment attachment = ClientAttachment.builder()
                .fileName(draft.fileName())
                .contentType(draft.contentType())
                .sizeBytes(draft.sizeBytes())
                .sha256(draft.sha256())
                .uploadedBy(draft.uploadedBy())
                .build();
        addAttachment(attachment, maxFiles, maxTotalBytes);
        return attachment;
    }

    /**
     * Attaches a file after checking the rules (name unique, file count, total size).
     *
     * @param attachment the metadata of the stored file
     * @param maxFiles the allowed number of files per client
     * @param maxTotalBytes the allowed total size of all files of a client
     * @throws BusinessRuleException if the name is taken, or the count or total size would be exceeded
     */
    public void addAttachment(ClientAttachment attachment, int maxFiles, long maxTotalBytes) {
        ensureCanAttach(attachment.getFileName(), attachment.getSizeBytes(), maxFiles, maxTotalBytes);
        attachment.assignTo(this);
        attachments.add(attachment);
    }

    /**
     * Removes an attachment; its row is deleted when the transaction commits.
     *
     * @param attachmentId the id of the attachment
     * @return the removed attachment, so the caller can delete its file
     * @throws ResourceNotFoundException if this client has no attachment with that id
     */
    public ClientAttachment removeAttachment(Long attachmentId) {
        ClientAttachment found = findAttachment(attachmentId);
        attachments.remove(found);
        return found;
    }

    /**
     * Finds one of this client's attachments.
     *
     * @param attachmentId the id of the attachment
     * @return the attachment
     * @throws ResourceNotFoundException if this client has no attachment with that id (it may belong
     *         to another client)
     */
    public ClientAttachment findAttachment(Long attachmentId) {
        return AttachmentRules.find(attachments, attachmentId);
    }

    /**
     * Archives this client (soft delete) and records when it happened.
     *
     * @throws BusinessRuleException if the client is already archived or has an active credit
     */
    public void archive() {
        if (archived) {
            throw new BusinessRuleException("This client is already archived.");
        }
        if (credits.stream().anyMatch(c -> !c.isArchived() && c.getStatus() == CreditStatus.ACTIVE)) {
            throw new BusinessRuleException("Client has active credits and cannot be archived.");
        }
        archived = true;
        archivedAt = Instant.now();
    }

    /**
     * Brings an archived client back to the active state.
     *
     * @throws BusinessRuleException if the client is not archived
     */
    public void restore() {
        if (!archived) {
            throw new BusinessRuleException("This client is not archived.");
        }
        archived = false;
        archivedAt = null;
    }
}
