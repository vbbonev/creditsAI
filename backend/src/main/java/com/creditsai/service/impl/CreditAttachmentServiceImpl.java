package com.creditsai.service.impl;

import com.creditsai.dto.AttachmentDownload;
import com.creditsai.dto.AttachmentResponse;
import com.creditsai.dto.AttachmentUpload;
import com.creditsai.exception.ResourceNotFoundException;
import com.creditsai.mapper.AttachmentMapper;
import com.creditsai.model.Credit;
import com.creditsai.model.CreditAttachment;
import com.creditsai.repository.CreditRepository;
import com.creditsai.service.CreditAttachmentService;
import com.creditsai.storage.AttachmentStorage;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer implementation of {@link CreditAttachmentService}, using constructor Dependency
 * Injection. The rules live in {@link Credit}; this class loads (and locks) the credit and plugs it
 * into the shared flow of {@link AbstractAttachmentService}.
 */
@Service
@Transactional(readOnly = true)
public class CreditAttachmentServiceImpl extends AbstractAttachmentService<Credit, CreditAttachment>
        implements CreditAttachmentService {

    private final CreditRepository creditRepository;

    /**
     * Creates the service.
     *
     * @param creditRepository the credit repository
     * @param storage the file storage
     * @param ingestor the shared upload flow
     * @param attachmentMapper the attachment mapper
     */
    public CreditAttachmentServiceImpl(CreditRepository creditRepository, AttachmentStorage storage,
                                 AttachmentIngestor ingestor, AttachmentMapper attachmentMapper) {
        super(storage, ingestor, attachmentMapper);
        this.creditRepository = creditRepository;
    }

    /** {@inheritDoc} */
    @Override
    public List<AttachmentResponse> list(Long creditId, boolean includeArchived) {
        return super.list(creditId, includeArchived);
    }

    /** {@inheritDoc} */
    @Override
    public AttachmentDownload download(Long creditId, Long attachmentId, boolean includeArchived) {
        return super.download(creditId, attachmentId, includeArchived);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public List<AttachmentResponse> add(Long creditId, List<AttachmentUpload> uploads, String uploadedBy) {
        return super.add(creditId, uploads, uploadedBy);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(Long creditId, Long attachmentId) {
        super.delete(creditId, attachmentId);
    }

    /** {@inheritDoc} */
    @Override
    protected Credit load(Long creditId, boolean includeArchived) {
        return (includeArchived
                ? creditRepository.findById(creditId)
                : creditRepository.findByIdAndArchivedFalseAndClientArchivedFalse(creditId))
                .orElseThrow(() -> notFound(creditId));
    }

    /** {@inheritDoc} */
    @Override
    protected Credit lockOrThrow(Long creditId) {
        return creditRepository.findByIdForUpdate(creditId).orElseThrow(() -> notFound(creditId));
    }

    /** {@inheritDoc} */
    @Override
    protected void flush() {
        creditRepository.flush();
    }

    /** {@inheritDoc} */
    @Override
    protected List<CreditAttachment> attachmentsOf(Credit credit) {
        return credit.getAttachments();
    }

    /** {@inheritDoc} */
    @Override
    protected CreditAttachment findAttachment(Credit credit, Long attachmentId) {
        return credit.findAttachment(attachmentId);
    }

    /** {@inheritDoc} */
    @Override
    protected CreditAttachment removeAttachment(Credit credit, Long attachmentId) {
        return credit.removeAttachment(attachmentId);
    }

    /**
     * Builds the user-readable not-found exception.
     *
     * @param creditId the missing credit id
     * @return the exception to throw
     */
    private ResourceNotFoundException notFound(Long creditId) {
        return new ResourceNotFoundException("Credit " + creditId + " was not found.");
    }
}
