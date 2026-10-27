package com.creditsai.service;

import com.creditsai.dto.ClientRequest;
import com.creditsai.dto.ClientResponse;
import com.creditsai.exception.BusinessRuleException;
import com.creditsai.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service layer / facade for client use cases. It knows nothing about security: callers decide
 * what a user may see.
 */
public interface ClientService {

    /**
     * Registers a new client.
     *
     * @param request the validated client data
     * @return the created client
     */
    ClientResponse create(ClientRequest request);

    /**
     * Lists clients by archive state.
     *
     * @param archived {@code true} for archived clients, {@code false} for active ones
     * @param pageable page, size and sort
     * @return the requested page
     */
    Page<ClientResponse> list(boolean archived, Pageable pageable);

    /**
     * Returns one client.
     *
     * @param id the client id
     * @param includeArchived whether an archived client may be returned
     * @return the client
     * @throws ResourceNotFoundException if it does not exist, or is archived and not allowed
     */
    ClientResponse get(Long id, boolean includeArchived);

    /**
     * Replaces all editable fields of a client, archived or not.
     *
     * @param id the client id
     * @param request the validated client data
     * @return the updated client
     * @throws ResourceNotFoundException if the client does not exist
     */
    ClientResponse update(Long id, ClientRequest request);

    /**
     * Archives a client (soft delete).
     *
     * @param id the client id
     * @throws ResourceNotFoundException if the client does not exist
     * @throws BusinessRuleException if the client cannot be archived
     */
    void archive(Long id);

    /**
     * Restores an archived client.
     *
     * @param id the client id
     * @return the restored client
     * @throws ResourceNotFoundException if the client does not exist
     * @throws BusinessRuleException if the client is not archived
     */
    ClientResponse restore(Long id);
}
