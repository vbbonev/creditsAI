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
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer implementation of {@link AttachmentService}, using constructor Dependency
 * Injection. The rules live in {@link Client}; this class loads (and locks) the client and plugs it
 * into the shared flow of {@link AbstractAttachmentService}.
 */
@Service
@Transactional(readOnly = true)
public class AttachmentServiceImpl extends AbstractAttachmentService<Client, ClientAttachment>
        implements AttachmentService {

    private final ClientRepository clientRepository;

    /**
     * Creates the service.
     *
     * @param clientRepository the client repository
     * @param storage the file storage
     * @param ingestor the shared upload flow
     * @param attachmentMapper the attachment mapper
     */
    public AttachmentServiceImpl(ClientRepository clientRepository, AttachmentStorage storage,
                                 AttachmentIngestor ingestor, AttachmentMapper attachmentMapper) {
        super(storage, ingestor, attachmentMapper);
        this.clientRepository = clientRepository;
    }

    /** {@inheritDoc} */
    @Override
    public List<AttachmentResponse> list(Long clientId, boolean includeArchived) {
        return super.list(clientId, includeArchived);
    }

    /** {@inheritDoc} */
    @Override
    public AttachmentDownload download(Long clientId, Long attachmentId, boolean includeArchived) {
        return super.download(clientId, attachmentId, includeArchived);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public List<AttachmentResponse> add(Long clientId, List<AttachmentUpload> uploads, String uploadedBy) {
        return super.add(clientId, uploads, uploadedBy);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(Long clientId, Long attachmentId) {
        super.delete(clientId, attachmentId);
    }

    /** {@inheritDoc} */
    @Override
    protected Client load(Long clientId, boolean includeArchived) {
        return (includeArchived
                ? clientRepository.findById(clientId)
                : clientRepository.findByIdAndArchivedFalse(clientId))
                .orElseThrow(() -> notFound(clientId));
    }

    /** {@inheritDoc} */
    @Override
    protected Client lockOrThrow(Long clientId) {
        return clientRepository.findByIdForUpdate(clientId).orElseThrow(() -> notFound(clientId));
    }

    /** {@inheritDoc} */
    @Override
    protected void flush() {
        clientRepository.flush();
    }

    /** {@inheritDoc} */
    @Override
    protected List<ClientAttachment> attachmentsOf(Client client) {
        return client.getAttachments();
    }

    /** {@inheritDoc} */
    @Override
    protected ClientAttachment findAttachment(Client client, Long attachmentId) {
        return client.findAttachment(attachmentId);
    }

    /** {@inheritDoc} */
    @Override
    protected ClientAttachment removeAttachment(Client client, Long attachmentId) {
        return client.removeAttachment(attachmentId);
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
