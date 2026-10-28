package com.creditsai.service;

import com.creditsai.dto.StorageCleanupReport;

/**
 * Service layer / facade for keeping the attachment files and the database rows consistent.
 */
public interface AttachmentStorageCleanupService {

    /**
     * Runs one cleanup: removes old hidden leftovers and reports (never deletes) files without a
     * row and rows without a file. A problem with one file or client is logged and the run goes on.
     *
     * @return the counts of the run
     */
    StorageCleanupReport cleanup();
}
