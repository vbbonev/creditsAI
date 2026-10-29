package com.creditsai.repository;

import com.creditsai.model.CreditAttachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository pattern (Spring Data) for {@link CreditAttachment}. Attachments are changed only
 * through {@code Credit}; this repository exists for read-only projections that must not load the
 * whole credit, such as the storage reconciliation.
 */
public interface CreditAttachmentRepository extends JpaRepository<CreditAttachment, Long> {

    /**
     * Lists the ids of all credits that have at least one attachment row.
     *
     * @return the distinct credit ids
     */
    @Query("select distinct a.credit.id from CreditAttachment a")
    List<Long> findCreditIdsWithAttachments();

    /**
     * Lists the file names of one credit's attachment rows.
     *
     * @param creditId the credit id
     * @return the file names
     */
    @Query("select a.fileName from CreditAttachment a where a.credit.id = :creditId")
    List<String> findFileNamesByCreditId(@Param("creditId") Long creditId);
}
