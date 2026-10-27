package com.creditsai.model;

import com.creditsai.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
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
