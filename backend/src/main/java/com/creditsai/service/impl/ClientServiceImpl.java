package com.creditsai.service.impl;

import com.creditsai.dto.AttachmentUpload;
import com.creditsai.dto.ClientRequest;
import com.creditsai.dto.ClientResponse;
import com.creditsai.exception.ResourceNotFoundException;
import com.creditsai.mapper.ClientMapper;
import com.creditsai.model.Client;
import com.creditsai.repository.ClientRepository;
import com.creditsai.service.ClientService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer implementation of {@link ClientService}, using constructor Dependency Injection.
 * It only orchestrates: loading, calling entity methods, saving and mapping.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;
    private final ClientMapper clientMapper;
    private final AttachmentIngestor attachmentIngestor;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ClientResponse create(ClientRequest request) {
        Client saved = clientRepository.save(clientMapper.toEntity(request));
        return clientMapper.toResponse(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The client is saved first to get its id (the folder name); a rejected file throws, and the
     * rollback removes the row and the files already stored.
     */
    @Override
    @Transactional
    public ClientResponse create(ClientRequest request, List<AttachmentUpload> uploads, String uploadedBy) {
        Client saved = clientRepository.save(clientMapper.toEntity(request));
        attachmentIngestor.attach(saved, uploads, uploadedBy);
        return clientMapper.toResponse(clientRepository.saveAndFlush(saved));
    }

    /** {@inheritDoc} */
    @Override
    public Page<ClientResponse> list(String search, boolean includeArchived, Pageable pageable) {
        return clientRepository.search(containsPattern(search), includeArchived, pageable)
                .map(clientMapper::toResponse);
    }

    /** {@inheritDoc} */
    @Override
    public ClientResponse get(Long id, boolean includeArchived) {
        Client client = includeArchived
                ? clientRepository.findById(id).orElseThrow(() -> notFound(id))
                : clientRepository.findByIdAndArchivedFalse(id).orElseThrow(() -> notFound(id));
        return clientMapper.toResponse(client);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ClientResponse update(Long id, ClientRequest request) {
        Client client = findOrThrow(id);
        clientMapper.updateEntity(request, client);
        return clientMapper.toResponse(clientRepository.save(client));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void archive(Long id) {
        Client client = findOrThrow(id);
        client.archive();
        clientRepository.save(client);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public ClientResponse restore(Long id) {
        Client client = findOrThrow(id);
        client.restore();
        return clientMapper.toResponse(clientRepository.save(client));
    }

    /**
     * Loads a client regardless of its archive state.
     *
     * @param id the client id
     * @return the client
     * @throws ResourceNotFoundException if it does not exist
     */
    private Client findOrThrow(Long id) {
        return clientRepository.findById(id).orElseThrow(() -> notFound(id));
    }

    /**
     * Turns the search text into a lower-case LIKE pattern that matches it anywhere. The LIKE
     * wildcards {@code %} and {@code _} (and the escape character) in the text are matched literally.
     *
     * @param search the search text, may be null or blank
     * @return the pattern; {@code %} (matches everything) when the text is null or blank
     */
    private static String containsPattern(String search) {
        if (search == null || search.isBlank()) {
            return "%";
        }
        String escaped = search.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /**
     * Builds the user-readable not-found exception.
     *
     * @param id the missing client id
     * @return the exception to throw
     */
    private ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Client " + id + " was not found.");
    }
}
