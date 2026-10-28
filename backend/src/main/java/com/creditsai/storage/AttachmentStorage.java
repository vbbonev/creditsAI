package com.creditsai.storage;

import com.creditsai.exception.AttachmentStorageException;
import java.io.InputStream;
import java.util.List;
import org.springframework.core.io.Resource;

/**
 * Where the content of attachments is kept. The service layer depends on this interface only, so
 * the file system can be replaced (and faked in tests). Implementations keep the files consistent
 * with the database transaction that is running when they are called.
 */
public interface AttachmentStorage {

    /**
     * Stores the content of a new file for a client. If the surrounding transaction rolls back,
     * the file is removed again.
     *
     * @param clientId the owner of the file
     * @param fileName the sanitized file name, unique within the client
     * @param content the bytes to store; read to the end but not closed
     * @return the number of bytes written
     * @throws com.creditsai.exception.BusinessRuleException if a file with that name already exists
     * @throws AttachmentStorageException if the file cannot be written
     */
    long store(long clientId, String fileName, InputStream content);

    /**
     * Opens a stored file for reading.
     *
     * @param clientId the owner of the file
     * @param fileName the sanitized file name
     * @return a resource for the file; {@code exists()} is {@code false} if the file is missing
     * @throws AttachmentStorageException if the name would leave the client folder
     */
    Resource load(long clientId, String fileName);

    /**
     * Removes a stored file. When a transaction is running the file is set aside at once and
     * removed for good only after it commits; a rollback puts it back.
     *
     * @param clientId the owner of the file
     * @param fileName the sanitized file name
     * @throws AttachmentStorageException if the name would leave the client folder
     */
    void delete(long clientId, String fileName);

    /**
     * Lists the clients that have a folder in the storage. Only real folders named by a number
     * count; anything else (other names, symbolic links, files) is ignored.
     *
     * @return the client ids in ascending order, empty if the storage does not exist yet
     * @throws AttachmentStorageException if the storage cannot be read
     */
    List<Long> listClientIds();

    /**
     * Lists the regular files in a client's folder, including hidden leftovers. Symbolic links and
     * sub-folders are skipped; file content is not read.
     *
     * @param clientId the client
     * @return the files, empty if the client has no folder
     * @throws AttachmentStorageException if the folder cannot be read
     */
    List<StoredFile> listFiles(long clientId);

    /**
     * Removes a hidden leftover ({@code .deleted-*} or {@code .upload-*.tmp}) for good.
     *
     * @param clientId the client
     * @param fileName the leftover's file name
     * @throws AttachmentStorageException if the name is not a leftover name or the file cannot be removed
     */
    void deleteLeftover(long clientId, String fileName);
}
