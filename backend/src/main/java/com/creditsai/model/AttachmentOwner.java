package com.creditsai.model;

import com.creditsai.exception.BusinessRuleException;

/**
 * An aggregate root that owns attachments ({@link Client}, {@link Credit}). The shared upload flow
 * depends on this interface only (dependency inversion), so both kinds of owner use exactly the
 * same checks and storage code.
 */
public interface AttachmentOwner {

    /**
     * Returns the id of the owner (the folder name in the storage).
     *
     * @return the id, {@code null} while the owner is not saved
     */
    Long getId();

    /**
     * Tells which kind of owner this is.
     *
     * @return the kind
     */
    AttachmentOwnerType attachmentOwnerType();

    /**
     * Checks whether one more file may be attached, without changing anything.
     *
     * @param fileName the name of the new file, already sanitized (NFC, lower case)
     * @param sizeBytes the size of the new file
     * @param maxFiles the allowed number of files per owner
     * @param maxTotalBytes the allowed total size of all files of an owner
     * @throws BusinessRuleException if the name is taken, or the count or total size would be exceeded
     */
    void ensureCanAttach(String fileName, long sizeBytes, int maxFiles, long maxTotalBytes);

    /**
     * Creates the attachment row for a stored file and attaches it after checking the rules.
     *
     * @param draft the metadata of the stored file
     * @param maxFiles the allowed number of files per owner
     * @param maxTotalBytes the allowed total size of all files of an owner
     * @return the attached file
     * @throws BusinessRuleException if the name is taken, or the count or total size would be exceeded
     */
    AttachmentBase attach(AttachmentDraft draft, int maxFiles, long maxTotalBytes);
}
