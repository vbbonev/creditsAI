package com.creditsai.model;

import com.creditsai.exception.BusinessRuleException;
import com.creditsai.exception.ResourceNotFoundException;
import java.util.List;

/**
 * The rules of an attachment collection (unique name, file count, total size, lookup by id),
 * shared by {@link Client} and {@link Credit} so both apply exactly the same limits. Stateless;
 * only static methods.
 */
final class AttachmentRules {

    private AttachmentRules() {
    }

    /**
     * Checks whether one more file may be attached, without changing anything.
     *
     * @param existing the files the owner already has
     * @param ownerType the kind of owner, for the messages
     * @param fileName the name of the new file, already sanitized
     * @param sizeBytes the size of the new file
     * @param maxFiles the allowed number of files per owner
     * @param maxTotalBytes the allowed total size of all files of an owner
     * @throws BusinessRuleException if the name is taken, or the count or total size would be exceeded
     */
    static void ensureCanAttach(List<? extends AttachmentBase> existing, AttachmentOwnerType ownerType,
                                String fileName, long sizeBytes, int maxFiles, long maxTotalBytes) {
        String label = ownerType.getLabel();
        if (existing.stream().anyMatch(a -> a.getFileName().equals(fileName))) {
            throw new BusinessRuleException("A file named '" + fileName + "' already exists for this " + label + ".");
        }
        if (existing.size() >= maxFiles) {
            throw new BusinessRuleException("A " + label + " can have at most " + maxFiles + " files.");
        }
        long total = existing.stream().mapToLong(AttachmentBase::getSizeBytes).sum();
        if (total + sizeBytes > maxTotalBytes) {
            throw new BusinessRuleException("All files of a " + label + " together may take at most "
                    + megabytes(maxTotalBytes) + ". This " + label + " already uses " + megabytes(total) + ".");
        }
    }

    /**
     * Finds one attachment by id.
     *
     * @param <A> the attachment type
     * @param existing the files the owner has
     * @param attachmentId the id of the attachment
     * @return the attachment
     * @throws ResourceNotFoundException if the owner has no attachment with that id (it may belong
     *         to another owner)
     */
    static <A extends AttachmentBase> A find(List<A> existing, Long attachmentId) {
        return existing.stream()
                .filter(a -> a.getId() != null && a.getId().equals(attachmentId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Attachment " + attachmentId + " was not found."));
    }

    /**
     * Formats a size for messages.
     *
     * @param bytes the size in bytes
     * @return whole megabytes when exact (e.g. "20 MB"), otherwise bytes
     */
    private static String megabytes(long bytes) {
        long mb = 1024L * 1024L;
        return bytes % mb == 0 ? (bytes / mb) + " MB" : bytes + " bytes";
    }
}
