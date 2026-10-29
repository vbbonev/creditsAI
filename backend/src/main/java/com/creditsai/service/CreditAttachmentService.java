package com.creditsai.service;

import com.creditsai.dto.AttachmentDownload;
import com.creditsai.dto.AttachmentResponse;
import com.creditsai.dto.AttachmentUpload;
import com.creditsai.exception.BusinessRuleException;
import com.creditsai.exception.InvalidRequestException;
import com.creditsai.exception.PayloadTooLargeException;
import com.creditsai.exception.ResourceNotFoundException;
import com.creditsai.exception.UnsupportedFileTypeException;
import java.util.List;

/**
 * Service layer / facade for the files attached to an existing credit. It knows nothing about
 * security: callers decide who may do what.
 */
public interface CreditAttachmentService {

    /**
     * Lists the attachments of a credit sorted by file name.
     *
     * @param creditId the credit id
     * @param includeArchived whether an archived credit, or a credit of an archived client, may be used
     * @return the attachments
     * @throws ResourceNotFoundException if the credit does not exist, or is hidden (archived) and not allowed
     */
    List<AttachmentResponse> list(Long creditId, boolean includeArchived);

    /**
     * Opens the content of one attachment.
     *
     * @param creditId the credit id
     * @param attachmentId the attachment id
     * @param includeArchived whether an archived credit, or a credit of an archived client, may be used
     * @return the file ready to be streamed
     * @throws ResourceNotFoundException if the credit or attachment does not exist (or belongs to
     *         another credit), the credit is hidden (archived) and not allowed, or the file is missing on disk
     */
    AttachmentDownload download(Long creditId, Long attachmentId, boolean includeArchived);

    /**
     * Adds files to an existing credit, archived or not. All files are added or none.
     *
     * @param creditId the credit id
     * @param uploads the received files
     * @param uploadedBy the username recorded as uploader
     * @return the created attachments in upload order
     * @throws ResourceNotFoundException if the credit does not exist
     * @throws InvalidRequestException if a file name is not acceptable or a file is empty
     * @throws PayloadTooLargeException if a file is too large
     * @throws UnsupportedFileTypeException if a file's content type is not allowed or does not
     *         match its extension
     * @throws BusinessRuleException if a name is taken or the count or total size limit is exceeded
     */
    List<AttachmentResponse> add(Long creditId, List<AttachmentUpload> uploads, String uploadedBy);

    /**
     * Physically removes an attachment (row and file) - an approved exception to the archive rule.
     *
     * @param creditId the credit id
     * @param attachmentId the attachment id
     * @throws ResourceNotFoundException if the credit does not exist or has no such attachment
     */
    void delete(Long creditId, Long attachmentId);
}
