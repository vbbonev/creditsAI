package com.creditsai.service.impl;

import com.creditsai.config.AttachmentCleanupProperties;
import com.creditsai.dto.StorageCleanupReport;
import com.creditsai.model.AttachmentOwnerType;
import com.creditsai.repository.ClientAttachmentRepository;
import com.creditsai.repository.CreditAttachmentRepository;
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
 * deletes that are not yet committed are left alone. Log lines name the owner id and the file
 * name, never an absolute path. Clients and credits are handled by the same code, which reads the
 * rows of each kind through the matching repository.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentStorageCleanupServiceImpl implements AttachmentStorageCleanupService {

    private final AttachmentStorage storage;
    private final ClientAttachmentRepository clientAttachmentRepository;
    private final CreditAttachmentRepository creditAttachmentRepository;
    private final AttachmentCleanupProperties properties;

    /** Mutable counters of one run. */
    private static final class Counts {
        private int clients;
        private int credits;
        private int removed;
        private int orphans;
        private int missing;
        private int errors;
    }

    /** {@inheritDoc} */
    @Override
    public int migrateLegacyLayout() {
        return storage.migrateLegacyClientFolders();
    }

    /** {@inheritDoc} */
    @Override
    public StorageCleanupReport cleanup() {
        Instant threshold = Instant.now().minus(properties.minAge());
        Counts counts = new Counts();
        cleanupType(AttachmentOwnerType.CLIENT, threshold, counts);
        cleanupType(AttachmentOwnerType.CREDIT, threshold, counts);
        StorageCleanupReport report = new StorageCleanupReport(counts.clients, counts.credits, counts.removed,
                counts.orphans, counts.missing, counts.errors);
        log.info("Attachment cleanup finished: {} clients and {} credits checked, {} leftovers removed, "
                        + "{} files without a row, {} rows without a file, {} errors",
                report.clientsChecked(), report.creditsChecked(), report.leftoversRemoved(), report.orphanFiles(),
                report.missingFiles(), report.errors());
        return report;
    }

    /**
     * Cleans and checks every owner of one kind that has a folder or a row.
     *
     * @param type the kind of owner
     * @param threshold files modified after this instant are too young to be touched
     * @param counts the counters to update
     */
    private void cleanupType(AttachmentOwnerType type, Instant threshold, Counts counts) {
        Set<Long> ownerIds = new TreeSet<>();
        try {
            ownerIds.addAll(storage.listOwnerIds(type));
        } catch (RuntimeException e) {
            counts.errors++;
            log.error("Attachment cleanup: could not list the {} storage folders: {}", type.getLabel(),
                    e.getMessage());
        }
        try {
            ownerIds.addAll(idsWithRows(type));
        } catch (RuntimeException e) {
            counts.errors++;
            log.error("Attachment cleanup: could not read the {} attachment rows: {}", type.getLabel(),
                    e.getMessage());
        }
        for (Long ownerId : ownerIds) {
            try {
                if (type == AttachmentOwnerType.CLIENT) {
                    counts.clients++;
                } else {
                    counts.credits++;
                }
                checkOwner(type, ownerId, threshold, counts);
            } catch (RuntimeException e) {
                counts.errors++;
                log.error("Attachment cleanup: {} {} could not be checked: {}", type.getLabel(), ownerId,
                        e.getMessage());
            }
        }
    }

    /**
     * Reads the ids of the owners of one kind that have attachment rows.
     *
     * @param type the kind of owner
     * @return the owner ids
     */
    private List<Long> idsWithRows(AttachmentOwnerType type) {
        return type == AttachmentOwnerType.CLIENT
                ? clientAttachmentRepository.findClientIdsWithAttachments()
                : creditAttachmentRepository.findCreditIdsWithAttachments();
    }

    /**
     * Reads the file names in the rows of one owner.
     *
     * @param type the kind of owner
     * @param ownerId the owner id
     * @return the file names
     */
    private List<String> namesWithRows(AttachmentOwnerType type, Long ownerId) {
        return type == AttachmentOwnerType.CLIENT
                ? clientAttachmentRepository.findFileNamesByClientId(ownerId)
                : creditAttachmentRepository.findFileNamesByCreditId(ownerId);
    }

    /**
     * Cleans and checks one owner: deletes old leftovers, reports old files without a row, and
     * reports rows without a file.
     *
     * @param type the kind of owner
     * @param ownerId the owner
     * @param threshold files modified after this instant are too young to be touched
     * @param counts the counters to update
     */
    private void checkOwner(AttachmentOwnerType type, Long ownerId, Instant threshold, Counts counts) {
        List<StoredFile> files = storage.listFiles(type, ownerId);
        Set<String> rowNames = Set.copyOf(namesWithRows(type, ownerId));
        Set<String> realNames = new TreeSet<>();
        boolean deletePending = false;
        for (StoredFile file : files) {
            boolean old = file.lastModified().isBefore(threshold);
            if (file.isLeftover()) {
                if (old) {
                    removeLeftover(type, ownerId, file, counts);
                } else if (StoredFile.isTombstoneName(file.name())) {
                    deletePending = true;
                }
                continue;
            }
            realNames.add(file.name());
            if (old && !rowNames.contains(file.name())) {
                counts.orphans++;
                log.warn("Attachment cleanup: file '{}' of {} {} has no database row (not deleted)",
                        file.name(), type.getLabel(), ownerId);
            }
        }
        if (deletePending) {
            // A delete that is not committed yet has set its file aside, so a missing file is expected
            return;
        }
        for (String rowName : new TreeSet<>(rowNames)) {
            if (!realNames.contains(rowName)) {
                counts.missing++;
                log.warn("Attachment cleanup: database row for file '{}' of {} {} has no file", rowName,
                        type.getLabel(), ownerId);
            }
        }
    }

    /**
     * Deletes one old leftover; a failure is logged and counted, not thrown.
     *
     * @param type the kind of owner
     * @param ownerId the owner
     * @param file the leftover
     * @param counts the counters to update
     */
    private void removeLeftover(AttachmentOwnerType type, Long ownerId, StoredFile file, Counts counts) {
        try {
            storage.deleteLeftover(type, ownerId, file.name());
            counts.removed++;
            log.info("Attachment cleanup: removed leftover file '{}' of {} {}", file.name(), type.getLabel(),
                    ownerId);
        } catch (RuntimeException e) {
            counts.errors++;
            log.error("Attachment cleanup: could not remove leftover file '{}' of {} {}: {}", file.name(),
                    type.getLabel(), ownerId, e.getMessage());
        }
    }
}
