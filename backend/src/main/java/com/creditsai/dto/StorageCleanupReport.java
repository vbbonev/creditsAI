package com.creditsai.dto;

/**
 * DTO pattern: the counts of one attachment storage cleanup run, over the files of clients and credits.
 *
 * @param clientsChecked how many clients were checked
 * @param creditsChecked how many credits were checked
 * @param leftoversRemoved how many old tombstones and temporary upload files were deleted
 * @param orphanFiles how many files have no database row (reported only)
 * @param missingFiles how many database rows have no file (reported only)
 * @param errors how many files, clients or credits could not be processed
 */
public record StorageCleanupReport(int clientsChecked, int creditsChecked, int leftoversRemoved, int orphanFiles,
                                   int missingFiles, int errors) {
}
