package com.creditsai.storage;

import com.creditsai.exception.AttachmentStorageException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MimeTypes;
import org.springframework.stereotype.Component;

/**
 * Finds the real type of a file from its bytes (magic numbers and, for zip-based formats, the
 * package structure) with Apache Tika. The file name is deliberately not given to Tika, so the
 * extension can never influence the result.
 *
 * <p>Tika-core recognizes everything allowed here by its magic bytes, including ODT (its
 * {@code mimetype} entry), but reports DOCX and XLSX only as a generic zip; those are
 * told apart by {@link OoxmlInspector}.
 */
@Component
public class FileTypeDetector {

    /** What Tika reports for zip-based files it cannot tell apart (all OOXML files, any other zip). */
    private static final Set<String> ZIP_TYPES = Set.of("application/zip", "application/x-tika-ooxml");

    private final Detector detector = MimeTypes.getDefaultMimeTypes();

    /**
     * Detects the type of the content and maps it to an allowed type.
     *
     * @param content the file content; it is read but not closed
     * @return the allowed type, or empty if the content is of any other type
     * @throws AttachmentStorageException if the content cannot be read
     */
    public Optional<AllowedFileType> detect(InputStream content) {
        // Closing the TikaInputStream removes the temp copy Tika makes when a path is requested
        try (TikaInputStream stream = TikaInputStream.get(content)) {
            String mediaType = detector.detect(stream, new Metadata()).getBaseType().toString();
            if (ZIP_TYPES.contains(mediaType)) {
                return OoxmlInspector.inspect(stream.getPath());
            }
            return AllowedFileType.fromMediaType(mediaType);
        } catch (IOException e) {
            throw new AttachmentStorageException("Could not read an uploaded file for type detection.", e);
        }
    }
}
