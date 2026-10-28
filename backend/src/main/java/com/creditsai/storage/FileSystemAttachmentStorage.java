package com.creditsai.storage;

import com.creditsai.config.AttachmentProperties;
import com.creditsai.exception.AttachmentStorageException;
import com.creditsai.exception.BusinessRuleException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link AttachmentStorage} on the local file system: {@code {storageDir}/{clientId}/{fileName}}.
 * A file is first written to a temporary file in the client folder and then moved into place, so
 * a reader never sees a half-written file. Folders get permissions 0750 and files 0640 when the
 * file system supports POSIX. Writes are undone if the running transaction rolls back, and
 * deletes happen only after it commits. Empty client folders are left in place.
 */
@Slf4j
@Component
public class FileSystemAttachmentStorage implements AttachmentStorage {

    private static final boolean POSIX =
            FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    private final Path baseDir;

    /**
     * Creates the storage on the configured base directory; the directory is created lazily on
     * the first write.
     *
     * @param properties the attachment settings
     */
    public FileSystemAttachmentStorage(AttachmentProperties properties) {
        this.baseDir = properties.storageDir().toAbsolutePath().normalize();
    }

    /** {@inheritDoc} */
    @Override
    public long store(long clientId, String fileName, InputStream content) {
        Path clientDir = clientDir(clientId);
        Path target = resolveInside(clientDir, fileName);
        Path temp = null;
        try {
            createDirectories(clientDir);
            if (Files.exists(target)) {
                throw duplicate(fileName);
            }
            temp = Files.createTempFile(clientDir, StoredFile.UPLOAD_PREFIX, ".tmp", filePermissions());
            long written;
            // Not Files.copy: it would replace the temp file and lose its restrictive permissions
            try (OutputStream out = Files.newOutputStream(temp)) {
                written = content.transferTo(out);
            }
            // No ATOMIC_MOVE and no REPLACE_EXISTING: on Linux an atomic move would silently replace
            // an existing target, but a collision must fail
            Files.move(temp, target);
            temp = null;
            removeOnRollback(target);
            return written;
        } catch (FileAlreadyExistsException e) {
            throw duplicate(fileName);
        } catch (IOException e) {
            throw new AttachmentStorageException("Could not store an attachment of client " + clientId + ".", e);
        } finally {
            if (temp != null) {
                deleteQuietly(temp);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public Resource load(long clientId, String fileName) {
        return new FileSystemResource(resolveInside(clientDir(clientId), fileName));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Inside a transaction the file is renamed at once (still under the caller's client row
     * lock) to a hidden tombstone, so the real name is free for a parallel upload. The tombstone is
     * removed after commit and renamed back after a rollback.
     */
    @Override
    public void delete(long clientId, String fileName) {
        Path target = resolveInside(clientDir(clientId), fileName);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(target);
            return;
        }
        Path tombstone = target.resolveSibling(StoredFile.TOMBSTONE_PREFIX + UUID.randomUUID());
        try {
            Files.move(target, tombstone, StandardCopyOption.ATOMIC_MOVE);
        } catch (NoSuchFileException e) {
            return;
        } catch (IOException e) {
            throw new AttachmentStorageException("Could not remove an attachment of client " + clientId + ".", e);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /** {@inheritDoc} */
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    deleteQuietly(tombstone);
                } else {
                    try {
                        Files.move(tombstone, target);
                    } catch (IOException e) {
                        log.error("Could not restore a file after rollback: {}", e.getClass().getSimpleName());
                    }
                }
            }
        });
    }

    /**
     * Builds the user-readable conflict for a name that is already taken.
     *
     * @param fileName the file name
     * @return the exception to throw
     */
    private static BusinessRuleException duplicate(String fileName) {
        return new BusinessRuleException("A file named '" + fileName + "' already exists for this client.");
    }

    /** {@inheritDoc} */
    @Override
    public List<Long> listClientIds() {
        if (!Files.isDirectory(baseDir, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(baseDir)) {
            return entries
                    .filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS))
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.matches("[0-9]{1,18}"))
                    .map(Long::valueOf)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new AttachmentStorageException("Could not list the attachment folders.", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<StoredFile> listFiles(long clientId) {
        Path dir = clientDir(clientId);
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        List<StoredFile> files = new ArrayList<>();
        try (Stream<Path> entries = Files.list(dir)) {
            for (Path entry : entries.toList()) {
                try {
                    BasicFileAttributes attributes =
                            Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (attributes.isRegularFile()) {
                        files.add(new StoredFile(entry.getFileName().toString(),
                                attributes.lastModifiedTime().toInstant()));
                    }
                } catch (IOException e) {
                    log.warn("Could not inspect a file of client {}: {}", clientId, e.getClass().getSimpleName());
                }
            }
        } catch (IOException e) {
            throw new AttachmentStorageException("Could not list the files of client " + clientId + ".", e);
        }
        return files;
    }

    /** {@inheritDoc} */
    @Override
    public void deleteLeftover(long clientId, String fileName) {
        if (!StoredFile.isLeftoverName(fileName)) {
            throw new AttachmentStorageException("Only leftover files may be removed this way.");
        }
        try {
            Files.deleteIfExists(resolveInside(clientDir(clientId), fileName));
        } catch (IOException e) {
            throw new AttachmentStorageException("Could not remove a leftover file of client " + clientId + ".", e);
        }
    }

    /**
     * Returns the folder of a client.
     *
     * @param clientId the client id
     * @return the folder path (it may not exist yet)
     */
    private Path clientDir(long clientId) {
        return baseDir.resolve(Long.toString(clientId));
    }

    /**
     * Resolves a file in a client folder and verifies the result stays inside it.
     *
     * @param clientDir the client folder
     * @param fileName the file name
     * @return the normalized path of the file
     * @throws AttachmentStorageException if the path would leave the folder
     */
    private Path resolveInside(Path clientDir, String fileName) {
        Path resolved = clientDir.resolve(fileName).normalize();
        if (!resolved.startsWith(clientDir) || resolved.equals(clientDir)
                || !clientDir.equals(resolved.getParent())) {
            throw new AttachmentStorageException("A file name resolved outside of the client folder.");
        }
        return resolved;
    }

    /**
     * Creates the client folder (and the base folder) with restrictive permissions if missing.
     *
     * @param dir the folder to create
     * @throws IOException if it cannot be created
     */
    private void createDirectories(Path dir) throws IOException {
        if (Files.isDirectory(dir)) {
            return;
        }
        if (POSIX) {
            Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rwxr-x---")));
        } else {
            Files.createDirectories(dir);
        }
    }

    /**
     * Returns the attributes for new files: 0640 on POSIX file systems, none otherwise.
     *
     * @return the file attributes to pass when creating a file
     */
    private FileAttribute<?>[] filePermissions() {
        if (POSIX) {
            return new FileAttribute<?>[] {
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r-----")) };
        }
        return new FileAttribute<?>[0];
    }

    /**
     * Registers the undo of a write: if the transaction does not commit, the file is removed. The
     * client folder stays (possibly empty), because removing it could race with a parallel upload.
     *
     * @param file the file just written
     */
    private void removeOnRollback(Path file) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /** {@inheritDoc} */
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(file);
                }
            }
        });
    }

    /**
     * Deletes a file, logging instead of failing; a leftover file must not turn
     * a finished request into an error.
     *
     * @param path the file
     */
    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not remove file {}: {}", path.getFileName(), e.getClass().getSimpleName());
        }
    }
}
