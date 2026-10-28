package com.creditsai.dto;

/**
 * DTO pattern: the counts of one attachment storage cleanup run.
 *
 * @param clientsChecked how many clients were checked
 * @param leftoversRemoved how many old tombstones and temporary upload files were deleted
 * @param orphanFiles how many files have no database row (reported only)
 * @param missingFiles how many database rows have no file (reported only)
 * @param errors how many files or clients could not be processed
 */
public record StorageCleanupReport(int clientsChecked, int leftoversRemoved, int orphanFiles, int missingFiles,
                                   int errors) {
}
