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
 * Service layer / facade for the files attached to an existing client. It knows nothing about
 * security: callers decide who may do what.
 */
public interface AttachmentService {

    /**
     * Lists the attachments of a client sorted by file name.
     *
     * @param clientId the client id
     * @param includeArchived whether an archived client may be used
     * @return the attachments
     * @throws ResourceNotFoundException if the client does not exist, or is archived and not allowed
     */
    List<AttachmentResponse> list(Long clientId, boolean includeArchived);

    /**
     * Opens the content of one attachment.
     *
     * @param clientId the client id
     * @param attachmentId the attachment id
     * @param includeArchived whether an archived client may be used
     * @return the file ready to be streamed
     * @throws ResourceNotFoundException if the client or attachment does not exist (or belongs to
     *         another client), the client is archived and not allowed, or the file is missing on disk
     */
    AttachmentDownload download(Long clientId, Long attachmentId, boolean includeArchived);

    /**
     * Adds files to an existing client, archived or not. All files are added or none.
     *
     * @param clientId the client id
     * @param uploads the received files
     * @param uploadedBy the username recorded as uploader
     * @return the created attachments in upload order
     * @throws ResourceNotFoundException if the client does not exist
     * @throws InvalidRequestException if a file name is not acceptable or a file is empty
     * @throws PayloadTooLargeException if a file is too large
     * @throws UnsupportedFileTypeException if a file's content type is not allowed or does not
     *         match its extension
     * @throws BusinessRuleException if a name is taken or the count or total size limit is exceeded
     */
    List<AttachmentResponse> add(Long clientId, List<AttachmentUpload> uploads, String uploadedBy);

    /**
     * Physically removes an attachment (row and file) - an approved exception to the archive rule.
     *
     * @param clientId the client id
     * @param attachmentId the attachment id
     * @throws ResourceNotFoundException if the client does not exist or has no such attachment
     */
    void delete(Long clientId, Long attachmentId);
}
