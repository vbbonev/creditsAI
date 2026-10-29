package com.creditsai.service;

import com.creditsai.dto.StorageCleanupReport;

/**
 * Service layer / facade for keeping the attachment files and the database rows consistent.
 */
public interface AttachmentStorageCleanupService {

    /**
     * Moves the client folders of the old disk layout ({@code {storageDir}/{clientId}}) into
     * {@code {storageDir}/clients/}. Idempotent and safe: nothing is overwritten, a folder whose
     * target exists is skipped and logged. Meant to run once at startup, before the first cleanup.
     *
     * @return the number of folders moved
     */
    int migrateLegacyLayout();

    /**
     * Runs one cleanup over the files of clients and credits: removes old hidden leftovers and
     * reports (never deletes) files without a row and rows without a file. A problem with one file
     * or owner is logged and the run goes on.
     *
     * @return the counts of the run
     */
    StorageCleanupReport cleanup();
}
