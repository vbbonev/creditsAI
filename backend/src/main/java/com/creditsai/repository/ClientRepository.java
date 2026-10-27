package com.creditsai.repository;

import com.creditsai.model.Client;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository pattern (Spring Data) for {@link Client}; only the queries beyond the inherited
 * ones are declared here.
 */
public interface ClientRepository extends JpaRepository<Client, Long> {

    /**
     * Lists clients by archive state.
     *
     * @param archived {@code true} for archived clients, {@code false} for active ones
     * @param pageable page, size and sort
     * @return the requested page of clients
     */
    Page<Client> findByArchived(boolean archived, Pageable pageable);

    /**
     * Finds a client that is not archived; an archived client behaves like a missing one.
     *
     * @param id the client id
     * @return the active client, or empty if missing or archived
     */
    Optional<Client> findByIdAndArchivedFalse(Long id);
}
