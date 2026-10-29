package com.creditsai.repository;

import com.creditsai.model.Credit;
import com.creditsai.model.CreditStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository pattern (Spring Data) for {@link Credit}; only the queries beyond the inherited
 * ones are declared here.
 */
public interface CreditRepository extends JpaRepository<Credit, Long> {

    /**
     * Lists credits whose lower-cased number, client name or client identifier matches a LIKE
     * pattern. JPQL because of the join, the optional filters and the three LIKE conditions; the
     * client is fetched in the same query to avoid one extra query per row.
     *
     * @param pattern a lower-case LIKE pattern using {@code \} as the escape character
     * @param filterByStatus {@code true} to keep only credits with the given status
     * @param status the status to keep, used only when {@code filterByStatus} is true
     * @param includeArchived {@code true} to include archived credits and credits of archived clients,
     *        {@code false} to hide both
     * @param pageable page, size and sort
     * @return the requested page of credits
     */
    @Query(value = """
            select cr from Credit cr join fetch cr.client c
            where (:includeArchived = true or (cr.archived = false and c.archived = false))
              and (:filterByStatus = false or cr.status = :status)
              and (lower(cr.creditNumber) like :pattern escape '\\'
                   or lower(c.name) like :pattern escape '\\'
                   or lower(c.identifier) like :pattern escape '\\')
            """,
            countQuery = """
            select count(cr) from Credit cr join cr.client c
            where (:includeArchived = true or (cr.archived = false and c.archived = false))
              and (:filterByStatus = false or cr.status = :status)
              and (lower(cr.creditNumber) like :pattern escape '\\'
                   or lower(c.name) like :pattern escape '\\'
                   or lower(c.identifier) like :pattern escape '\\')
            """)
    Page<Credit> search(@Param("pattern") String pattern, @Param("filterByStatus") boolean filterByStatus,
            @Param("status") CreditStatus status, @Param("includeArchived") boolean includeArchived,
            Pageable pageable);

    /**
     * Finds a credit that is not archived; an archived credit behaves like a missing one.
     *
     * @param id the credit id
     * @return the credit, or empty if missing or archived
     */
    Optional<Credit> findByIdAndArchivedFalse(Long id);

    /**
     * Finds a credit that is visible to a non-admin: neither the credit nor its client is archived.
     * Such a credit behaves like a missing one, so archived clients' data is not exposed.
     *
     * @param id the credit id
     * @return the credit, or empty if missing, archived, or owned by an archived client
     */
    Optional<Credit> findByIdAndArchivedFalseAndClientArchivedFalse(Long id);

    /**
     * Loads a credit (archived or not) and locks its row until the transaction ends, so parallel
     * changes to its attachments are applied one after the other and the limits hold.
     *
     * @param id the credit id
     * @return the locked credit, or empty if missing
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cr from Credit cr where cr.id = :id")
    Optional<Credit> findByIdForUpdate(@Param("id") Long id);
}
