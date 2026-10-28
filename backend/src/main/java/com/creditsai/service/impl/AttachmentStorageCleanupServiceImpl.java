package com.creditsai.service.impl;

import com.creditsai.config.AttachmentCleanupProperties;
import com.creditsai.dto.StorageCleanupReport;
import com.creditsai.repository.ClientAttachmentRepository;
import com.creditsai.service.AttachmentStorageCleanupService;
import com.creditsai.storage.AttachmentStorage;
import com.creditsai.storage.StoredFile;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service layer implementation of {@link AttachmentStorageCleanupService}. Only files older than
 * the configured minimum age are touched or reported, so uploads that are not yet committed and
 * deletes that are not yet committed are left alone. Log lines name the client id and the file
 * name, never an absolute path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentStorageCleanupServiceImpl implements AttachmentStorageCleanupService {

    private final AttachmentStorage storage;
    private final ClientAttachmentRepository attachmentRepository;
    private final AttachmentCleanupProperties properties;

    /** Mutable counters of one run. */
    private static final class Counts {
        private int clients;
        private int removed;
        private int orphans;
        private int missing;
        private int errors;
    }

    /** {@inheritDoc} */
    @Override
    public StorageCleanupReport cleanup() {
        Instant threshold = Instant.now().minus(properties.minAge());
        Counts counts = new Counts();
        Set<Long> clientIds = new TreeSet<>();
        try {
            clientIds.addAll(storage.listClientIds());
        } catch (RuntimeException e) {
            counts.errors++;
            log.error("Attachment cleanup: could not list the storage folders: {}", e.getMessage());
        }
        try {
            clientIds.addAll(attachmentRepository.findClientIdsWithAttachments());
        } catch (RuntimeException e) {
            counts.errors++;
            log.error("Attachment cleanup: could not read the attachment rows: {}", e.getMessage());
        }
        for (Long clientId : clientIds) {
            try {
                counts.clients++;
                checkClient(clientId, threshold, counts);
            } catch (RuntimeException e) {
                counts.errors++;
                log.error("Attachment cleanup: client {} could not be checked: {}", clientId, e.getMessage());
            }
        }
        StorageCleanupReport report = new StorageCleanupReport(counts.clients, counts.removed, counts.orphans,
                counts.missing, counts.errors);
        log.info("Attachment cleanup finished: {} clients checked, {} leftovers removed, {} files without a row, "
                        + "{} rows without a file, {} errors",
                report.clientsChecked(), report.leftoversRemoved(), report.orphanFiles(), report.missingFiles(),
                report.errors());
        return report;
    }

    /**
     * Cleans and checks one client: deletes old leftovers, reports old files without a row, and
     * reports rows without a file.
     *
     * @param clientId the client
     * @param threshold files modified after this instant are too young to be touched
     * @param counts the counters to update
     */
    private void checkClient(Long clientId, Instant threshold, Counts counts) {
        List<StoredFile> files = storage.listFiles(clientId);
        Set<String> rowNames = Set.copyOf(attachmentRepository.findFileNamesByClientId(clientId));
        Set<String> realNames = new TreeSet<>();
        boolean deletePending = false;
        for (StoredFile file : files) {
            boolean old = file.lastModified().isBefore(threshold);
            if (file.isLeftover()) {
                if (old) {
                    removeLeftover(clientId, file, counts);
                } else if (StoredFile.isTombstoneName(file.name())) {
                    deletePending = true;
                }
                continue;
            }
            realNames.add(file.name());
            if (old && !rowNames.contains(file.name())) {
                counts.orphans++;
                log.warn("Attachment cleanup: file '{}' of client {} has no database row (not deleted)",
                        file.name(), clientId);
            }
        }
        if (deletePending) {
            // A delete that is not committed yet has set its file aside, so a missing file is expected
            return;
        }
        for (String rowName : new TreeSet<>(rowNames)) {
            if (!realNames.contains(rowName)) {
                counts.missing++;
                log.warn("Attachment cleanup: database row for file '{}' of client {} has no file", rowName, clientId);
            }
        }
    }

    /**
     * Deletes one old leftover; a failure is logged and counted, not thrown.
     *
     * @param clientId the client
     * @param file the leftover
     * @param counts the counters to update
     */
    private void removeLeftover(Long clientId, StoredFile file, Counts counts) {
        try {
            storage.deleteLeftover(clientId, file.name());
            counts.removed++;
            log.info("Attachment cleanup: removed leftover file '{}' of client {}", file.name(), clientId);
        } catch (RuntimeException e) {
            counts.errors++;
            log.error("Attachment cleanup: could not remove leftover file '{}' of client {}: {}", file.name(),
                    clientId, e.getMessage());
        }
    }
}
