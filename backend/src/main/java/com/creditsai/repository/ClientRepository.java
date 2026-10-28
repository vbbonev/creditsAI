package com.creditsai.repository;

import com.creditsai.model.Client;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository pattern (Spring Data) for {@link Client}; only the queries beyond the inherited
 * ones are declared here.
 */
public interface ClientRepository extends JpaRepository<Client, Long> {

    /**
     * Lists clients whose lower-cased name or identifier matches a LIKE pattern. JPQL because the
     * optional archive filter and the two LIKE conditions would make a derived name unreadable.
     *
     * @param pattern a lower-case LIKE pattern using {@code \} as the escape character
     * @param includeArchived {@code true} to include archived clients, {@code false} for active ones only
     * @param pageable page, size and sort
     * @return the requested page of clients
     */
    @Query("""
            select c from Client c
            where (:includeArchived = true or c.archived = false)
              and (lower(c.name) like :pattern escape '\\'
                   or lower(c.identifier) like :pattern escape '\\')
            """)
    Page<Client> search(@Param("pattern") String pattern, @Param("includeArchived") boolean includeArchived,
            Pageable pageable);

    /**
     * Finds a client that is not archived; an archived client behaves like a missing one.
     *
     * @param id the client id
     * @return the active client, or empty if missing or archived
     */
    Optional<Client> findByIdAndArchivedFalse(Long id);

    /**
     * Loads a client (archived or not) and locks its row until the transaction ends, so parallel
     * changes to its attachments are applied one after the other and the limits hold.
     *
     * @param id the client id
     * @return the locked client, or empty if missing
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Client c where c.id = :id")
    Optional<Client> findByIdForUpdate(@Param("id") Long id);
}
