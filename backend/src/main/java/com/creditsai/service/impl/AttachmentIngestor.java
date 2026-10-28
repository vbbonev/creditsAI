package com.creditsai.service.impl;

import com.creditsai.config.AttachmentProperties;
import com.creditsai.dto.AttachmentUpload;
import com.creditsai.exception.AttachmentStorageException;
import com.creditsai.exception.InvalidRequestException;
import com.creditsai.exception.PayloadTooLargeException;
import com.creditsai.exception.UnsupportedFileTypeException;
import com.creditsai.model.Client;
import com.creditsai.model.ClientAttachment;
import com.creditsai.storage.AllowedFileType;
import com.creditsai.storage.AttachmentStorage;
import com.creditsai.storage.FileNameSanitizer;
import com.creditsai.storage.FileTypeDetector;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Shared upload flow of the service layer (used when a client is created with files and when an
 * administrator adds files): sanitize the name, check the size, detect the real type, check the
 * extension, store the bytes and attach the metadata to the client. Extracted so both use cases
 * apply exactly the same checks. It must run inside the caller's transaction, so storage is
 * undone on rollback.
 */
@Component
@RequiredArgsConstructor
public class AttachmentIngestor {

    private final AttachmentStorage storage;
    private final FileTypeDetector detector;
    private final AttachmentProperties properties;

    /**
     * Validates, stores and attaches every upload to the client, in order. The first problem
     * aborts the whole batch.
     *
     * @param client the saved client (with an id) that receives the files
     * @param uploads the received files
     * @param uploadedBy the username recorded as uploader
     * @return the attached files, in upload order
     * @throws InvalidRequestException if a file name is not acceptable or a file is empty
     * @throws PayloadTooLargeException if a file is larger than the allowed maximum
     * @throws UnsupportedFileTypeException if a file's content is not an allowed type or does not
     *         match its extension
     * @throws com.creditsai.exception.BusinessRuleException if a name is taken or the count or
     *         total size limit would be exceeded
     */
    public List<ClientAttachment> attach(Client client, List<AttachmentUpload> uploads, String uploadedBy) {
        List<ClientAttachment> attached = new ArrayList<>();
        for (AttachmentUpload upload : uploads) {
            attached.add(attachOne(client, upload, uploadedBy));
        }
        return attached;
    }

    /**
     * Runs the full flow for one file.
     *
     * @param client the client that receives the file
     * @param upload the received file
     * @param uploadedBy the username recorded as uploader
     * @return the attached file
     */
    private ClientAttachment attachOne(Client client, AttachmentUpload upload, String uploadedBy) {
        String fileName = FileNameSanitizer.sanitize(upload.fileName());
        long maxFile = properties.maxFileSize().toBytes();
        long maxTotal = properties.maxTotalSize().toBytes();
        if (upload.sizeBytes() == 0) {
            throw new InvalidRequestException("File '" + fileName + "' is empty.");
        }
        if (upload.sizeBytes() > maxFile) {
            throw new PayloadTooLargeException("File '" + fileName + "' is larger than the maximum allowed size of "
                    + properties.maxFileSize().toMegabytes() + " MB.");
        }
        client.ensureCanAttach(fileName, upload.sizeBytes(), properties.maxFiles(), maxTotal);
        AllowedFileType type = detectAndCheck(fileName, upload);

        MessageDigest digest = newDigest();
        long written;
        try (InputStream in = new DigestInputStream(upload.source().getInputStream(), digest)) {
            written = storage.store(client.getId(), fileName, in);
        } catch (IOException e) {
            throw new AttachmentStorageException("Could not read an uploaded file.", e);
        }
        ClientAttachment attachment = ClientAttachment.builder()
                .fileName(fileName)
                .contentType(type.mediaType())
                .sizeBytes(written)
                .sha256(HexFormat.of().formatHex(digest.digest()))
                .uploadedBy(uploadedBy)
                .build();
        client.addAttachment(attachment, properties.maxFiles(), maxTotal);
        return attachment;
    }

    /**
     * Detects the real type from the bytes and checks it against the allow-list and the extension.
     *
     * @param fileName the sanitized file name
     * @param upload the received file
     * @return the detected allowed type
     * @throws UnsupportedFileTypeException if the type is not allowed or the extension disagrees
     */
    private AllowedFileType detectAndCheck(String fileName, AttachmentUpload upload) {
        AllowedFileType type;
        try (InputStream in = upload.source().getInputStream()) {
            type = detector.detect(in).orElseThrow(() -> new UnsupportedFileTypeException(
                    "File '" + fileName + "' has an unsupported type. Allowed types: "
                            + AllowedFileType.allLabels() + "."));
        } catch (IOException e) {
            throw new AttachmentStorageException("Could not read an uploaded file.", e);
        }
        if (!type.acceptsFileName(fileName)) {
            String extension = AllowedFileType.extensionOf(fileName);
            throw new UnsupportedFileTypeException("The content of file '" + fileName + "' is a " + type.label()
                    + " file, which does not match its extension" + (extension.isEmpty() ? "" : " '." + extension + "'")
                    + ".");
        }
        return type;
    }

    /**
     * Creates a SHA-256 digest.
     *
     * @return a new digest
     */
    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }
}
