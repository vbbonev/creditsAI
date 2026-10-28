package com.creditsai.service.impl;

import com.creditsai.dto.AttachmentDownload;
import com.creditsai.dto.AttachmentResponse;
import com.creditsai.dto.AttachmentUpload;
import com.creditsai.exception.ResourceNotFoundException;
import com.creditsai.mapper.AttachmentMapper;
import com.creditsai.model.Client;
import com.creditsai.model.ClientAttachment;
import com.creditsai.repository.ClientRepository;
import com.creditsai.service.AttachmentService;
import com.creditsai.storage.AttachmentStorage;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer implementation of {@link AttachmentService}, using constructor Dependency
 * Injection. The rules live in {@link Client}; this class loads (and locks) the client, calls the
 * entity and the storage, and maps.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentServiceImpl implements AttachmentService {

    private static final Comparator<AttachmentResponse> BY_FILE_NAME =
            Comparator.comparing(AttachmentResponse::fileName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(AttachmentResponse::fileName);

    private final ClientRepository clientRepository;
    private final AttachmentStorage storage;
    private final AttachmentIngestor ingestor;
    private final AttachmentMapper attachmentMapper;

    /** {@inheritDoc} */
    @Override
    public List<AttachmentResponse> list(Long clientId, boolean includeArchived) {
        return load(clientId, includeArchived).getAttachments().stream()
                .map(attachmentMapper::toResponse)
                .sorted(BY_FILE_NAME)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public AttachmentDownload download(Long clientId, Long attachmentId, boolean includeArchived) {
        ClientAttachment attachment = load(clientId, includeArchived).findAttachment(attachmentId);
        Resource content = storage.load(clientId, attachment.getFileName());
        if (!content.exists()) {
            throw new ResourceNotFoundException("The content of file '" + attachment.getFileName()
                    + "' is not available.");
        }
        return new AttachmentDownload(attachment.getFileName(), attachment.getContentType(),
                attachment.getSizeBytes(), content);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public List<AttachmentResponse> add(Long clientId, List<AttachmentUpload> uploads, String uploadedBy) {
        Client client = lockOrThrow(clientId);
        List<ClientAttachment> attached = ingestor.attach(client, uploads, uploadedBy);
        clientRepository.flush();
        return attached.stream().map(attachmentMapper::toResponse).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(Long clientId, Long attachmentId) {
        Client client = lockOrThrow(clientId);
        ClientAttachment removed = client.removeAttachment(attachmentId);
        storage.delete(clientId, removed.getFileName());
    }

    /**
     * Loads a client for reading.
     *
     * @param clientId the client id
     * @param includeArchived whether an archived client may be returned
     * @return the client
     * @throws ResourceNotFoundException if it does not exist, or is archived and not allowed
     */
    private Client load(Long clientId, boolean includeArchived) {
        return (includeArchived
                ? clientRepository.findById(clientId)
                : clientRepository.findByIdAndArchivedFalse(clientId))
                .orElseThrow(() -> notFound(clientId));
    }

    /**
     * Loads a client (archived or not) and locks its row until the transaction ends.
     *
     * @param clientId the client id
     * @return the locked client
     * @throws ResourceNotFoundException if it does not exist
     */
    private Client lockOrThrow(Long clientId) {
        return clientRepository.findByIdForUpdate(clientId).orElseThrow(() -> notFound(clientId));
    }

    /**
     * Builds the user-readable not-found exception.
     *
     * @param clientId the missing client id
     * @return the exception to throw
     */
    private ResourceNotFoundException notFound(Long clientId) {
        return new ResourceNotFoundException("Client " + clientId + " was not found.");
    }
}
