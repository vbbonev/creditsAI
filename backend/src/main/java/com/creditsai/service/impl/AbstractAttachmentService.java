package com.creditsai.service.impl;

import com.creditsai.dto.AttachmentDownload;
import com.creditsai.dto.AttachmentResponse;
import com.creditsai.dto.AttachmentUpload;
import com.creditsai.exception.ResourceNotFoundException;
import com.creditsai.mapper.AttachmentMapper;
import com.creditsai.model.AttachmentBase;
import com.creditsai.model.AttachmentOwner;
import com.creditsai.storage.AttachmentStorage;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Template Method shared by the attachment services of clients and credits: the flow of listing,
 * downloading, adding and removing files is the same, only loading (and locking) the owner and
 * reaching its attachment collection differ. It avoids copying the whole flow per owner kind.
 * The transaction settings are repeated here because Spring reads them from the class that
 * declares the method.
 *
 * @param <O> the owner type (client or credit)
 * @param <A> the attachment type of that owner
 */
@RequiredArgsConstructor
@Transactional(readOnly = true)
abstract class AbstractAttachmentService<O extends AttachmentOwner, A extends AttachmentBase> {

    private static final Comparator<AttachmentResponse> BY_FILE_NAME =
            Comparator.comparing(AttachmentResponse::fileName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(AttachmentResponse::fileName);

    private final AttachmentStorage storage;
    private final AttachmentIngestor ingestor;
    private final AttachmentMapper attachmentMapper;

    /**
     * Lists the attachments of an owner sorted by file name.
     *
     * @param ownerId the owner id
     * @param includeArchived whether an archived (or hidden) owner may be used
     * @return the attachments
     * @throws ResourceNotFoundException if the owner does not exist, or is hidden and not allowed
     */
    public List<AttachmentResponse> list(Long ownerId, boolean includeArchived) {
        return attachmentsOf(load(ownerId, includeArchived)).stream()
                .map(attachmentMapper::toResponse)
                .sorted(BY_FILE_NAME)
                .toList();
    }

    /**
     * Opens the content of one attachment.
     *
     * @param ownerId the owner id
     * @param attachmentId the attachment id
     * @param includeArchived whether an archived (or hidden) owner may be used
     * @return the file ready to be streamed
     * @throws ResourceNotFoundException if the owner or attachment does not exist (or belongs to
     *         another owner), the owner is hidden and not allowed, or the file is missing on disk
     */
    public AttachmentDownload download(Long ownerId, Long attachmentId, boolean includeArchived) {
        O owner = load(ownerId, includeArchived);
        A attachment = findAttachment(owner, attachmentId);
        Resource content = storage.load(owner.attachmentOwnerType(), ownerId, attachment.getFileName());
        if (!content.exists()) {
            throw new ResourceNotFoundException("The content of file '" + attachment.getFileName()
                    + "' is not available.");
        }
        return new AttachmentDownload(attachment.getFileName(), attachment.getContentType(),
                attachment.getSizeBytes(), content);
    }

    /**
     * Adds files to an existing owner, archived or not, under the owner's row lock. All files are
     * added or none.
     *
     * @param ownerId the owner id
     * @param uploads the received files
     * @param uploadedBy the username recorded as uploader
     * @return the created attachments in upload order
     * @throws ResourceNotFoundException if the owner does not exist
     */
    @Transactional
    public List<AttachmentResponse> add(Long ownerId, List<AttachmentUpload> uploads, String uploadedBy) {
        O owner = lockOrThrow(ownerId);
        List<AttachmentBase> attached = ingestor.attach(owner, uploads, uploadedBy);
        flush();
        return attached.stream().map(attachmentMapper::toResponse).toList();
    }

    /**
     * Physically removes an attachment (row and file) under the owner's row lock.
     *
     * @param ownerId the owner id
     * @param attachmentId the attachment id
     * @throws ResourceNotFoundException if the owner does not exist or has no such attachment
     */
    @Transactional
    public void delete(Long ownerId, Long attachmentId) {
        O owner = lockOrThrow(ownerId);
        A removed = removeAttachment(owner, attachmentId);
        storage.delete(owner.attachmentOwnerType(), ownerId, removed.getFileName());
    }

    /**
     * Loads an owner for reading.
     *
     * @param ownerId the owner id
     * @param includeArchived whether an archived (or hidden) owner may be returned
     * @return the owner
     * @throws ResourceNotFoundException if it does not exist, or is hidden and not allowed
     */
    protected abstract O load(Long ownerId, boolean includeArchived);

    /**
     * Loads an owner (archived or not) and locks its row until the transaction ends.
     *
     * @param ownerId the owner id
     * @return the locked owner
     * @throws ResourceNotFoundException if it does not exist
     */
    protected abstract O lockOrThrow(Long ownerId);

    /**
     * Writes pending changes to the database, so constraint violations surface inside the request.
     */
    protected abstract void flush();

    /**
     * Returns the attachments of an owner.
     *
     * @param owner the owner
     * @return the attachments
     */
    protected abstract List<A> attachmentsOf(O owner);

    /**
     * Finds one attachment of an owner.
     *
     * @param owner the owner
     * @param attachmentId the attachment id
     * @return the attachment
     * @throws ResourceNotFoundException if the owner has no such attachment
     */
    protected abstract A findAttachment(O owner, Long attachmentId);

    /**
     * Removes one attachment from an owner.
     *
     * @param owner the owner
     * @param attachmentId the attachment id
     * @return the removed attachment
     * @throws ResourceNotFoundException if the owner has no such attachment
     */
    protected abstract A removeAttachment(O owner, Long attachmentId);
}
