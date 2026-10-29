package com.creditsai.storage;

import com.creditsai.exception.AttachmentStorageException;
import com.creditsai.model.AttachmentOwnerType;
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
     * Stores the content of a new file for an owner. If the surrounding transaction rolls back,
     * the file is removed again.
     *
     * @param ownerType the kind of owner of the file
     * @param ownerId the id of the owner of the file
     * @param fileName the sanitized file name, unique within the owner
     * @param content the bytes to store; read to the end but not closed
     * @return the number of bytes written
     * @throws com.creditsai.exception.BusinessRuleException if a file with that name already exists
     * @throws AttachmentStorageException if the file cannot be written
     */
    long store(AttachmentOwnerType ownerType, long ownerId, String fileName, InputStream content);

    /**
     * Opens a stored file for reading.
     *
     * @param ownerType the kind of owner of the file
     * @param ownerId the id of the owner of the file
     * @param fileName the sanitized file name
     * @return a resource for the file; {@code exists()} is {@code false} if the file is missing
     * @throws AttachmentStorageException if the name would leave the owner folder
     */
    Resource load(AttachmentOwnerType ownerType, long ownerId, String fileName);

    /**
     * Removes a stored file. When a transaction is running the file is set aside at once and
     * removed for good only after it commits; a rollback puts it back.
     *
     * @param ownerType the kind of owner of the file
     * @param ownerId the id of the owner of the file
     * @param fileName the sanitized file name
     * @throws AttachmentStorageException if the name would leave the owner folder
     */
    void delete(AttachmentOwnerType ownerType, long ownerId, String fileName);

    /**
     * Lists the owners of one kind that have a folder in the storage. Only real folders named by a
     * number count; anything else (other names, symbolic links, files) is ignored.
     *
     * @param ownerType the kind of owner
     * @return the owner ids in ascending order, empty if the storage does not exist yet
     * @throws AttachmentStorageException if the storage cannot be read
     */
    List<Long> listOwnerIds(AttachmentOwnerType ownerType);

    /**
     * Moves the client folders of the old layout ({@code {storageDir}/{clientId}}) into
     * {@code {storageDir}/clients/{clientId}}. Safe to repeat: a folder whose target already exists
     * is left where it is and logged, nothing is overwritten or merged.
     *
     * @return the number of folders moved
     * @throws AttachmentStorageException if the storage cannot be read
     */
    int migrateLegacyClientFolders();

    /**
     * Lists the regular files in an owner's folder, including hidden leftovers. Symbolic links and
     * sub-folders are skipped; file content is not read.
     *
     * @param ownerType the kind of owner
     * @param ownerId the id of the owner
     * @return the files, empty if the owner has no folder
     * @throws AttachmentStorageException if the folder cannot be read
     */
    List<StoredFile> listFiles(AttachmentOwnerType ownerType, long ownerId);

    /**
     * Removes a hidden leftover ({@code .deleted-*} or {@code .upload-*.tmp}) for good.
     *
     * @param ownerType the kind of owner
     * @param ownerId the id of the owner
     * @param fileName the leftover's file name
     * @throws AttachmentStorageException if the name is not a leftover name or the file cannot be removed
     */
    void deleteLeftover(AttachmentOwnerType ownerType, long ownerId, String fileName);
}
