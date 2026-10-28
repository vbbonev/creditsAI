package com.creditsai.repository;

import com.creditsai.model.ClientAttachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository pattern (Spring Data) for {@link ClientAttachment}. Attachments are changed only
 * through {@code Client}; this repository exists for read-only projections that must not load the
 * whole client, such as the storage reconciliation.
 */
public interface ClientAttachmentRepository extends JpaRepository<ClientAttachment, Long> {

    /**
     * Lists the ids of all clients that have at least one attachment row.
     *
     * @return the distinct client ids
     */
    @Query("select distinct a.client.id from ClientAttachment a")
    List<Long> findClientIdsWithAttachments();

    /**
     * Lists the file names of one client's attachment rows.
     *
     * @param clientId the client id
     * @return the file names
     */
    @Query("select a.fileName from ClientAttachment a where a.client.id = :clientId")
    List<String> findFileNamesByClientId(@Param("clientId") Long clientId);
}
