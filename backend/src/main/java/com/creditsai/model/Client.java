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
public class Client {

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

    /**
     * Returns the attachments as a read-only view; change them only with {@link #addAttachment}
     * and {@link #removeAttachment}.
     *
     * @return the attachments in no particular order
     */
    public List<ClientAttachment> getAttachments() {
        return Collections.unmodifiableList(attachments);
    }

    /**
     * Checks whether one more file may be attached, without changing anything. Lets callers
     * reject a file before doing expensive work on it.
     *
     * @param fileName the name of the new file, already sanitized (NFC, lower case)
     * @param sizeBytes the size of the new file
     * @param maxFiles the allowed number of files per client
     * @param maxTotalBytes the allowed total size of all files of a client
     * @throws BusinessRuleException if the name is taken, or the count or total size would be exceeded
     */
    public void ensureCanAttach(String fileName, long sizeBytes, int maxFiles, long maxTotalBytes) {
        if (attachments.stream().anyMatch(a -> a.getFileName().equals(fileName))) {
            throw new BusinessRuleException("A file named '" + fileName + "' already exists for this client.");
        }
        if (attachments.size() >= maxFiles) {
            throw new BusinessRuleException("A client can have at most " + maxFiles + " files.");
        }
        long total = attachments.stream().mapToLong(ClientAttachment::getSizeBytes).sum();
        if (total + sizeBytes > maxTotalBytes) {
            throw new BusinessRuleException("All files of a client together may take at most "
                    + megabytes(maxTotalBytes) + ". This client already uses " + megabytes(total) + ".");
        }
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
        return attachments.stream()
                .filter(a -> a.getId() != null && a.getId().equals(attachmentId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Attachment " + attachmentId + " was not found."));
    }

    /**
     * Formats a size for messages.
     *
     * @param bytes the size in bytes
     * @return whole megabytes when exact (e.g. "20 MB"), otherwise bytes
     */
    private static String megabytes(long bytes) {
        long mb = 1024L * 1024L;
        return bytes % mb == 0 ? (bytes / mb) + " MB" : bytes + " bytes";
    }

    /**
     * Archives this client (soft delete) and records when it happened.
     *
     * <p>Note: the rule "a client with an active credit cannot be archived" will be added here
     * when the Credit entity exists.
     *
     * @throws BusinessRuleException if the client is already archived
     */
    public void archive() {
        if (archived) {
            throw new BusinessRuleException("This client is already archived.");
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
