package com.creditsai.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Tells DOCX and XLSX apart from any other zip file by reading the package's
 * {@code [Content_Types].xml}: a real Word document declares {@code /word/document.xml} with the
 * Word main content type, a real Excel workbook declares {@code /xl/workbook.xml} with the Excel
 * main content type. Macro-enabled and template variants declare other types and are therefore
 * not accepted. Tika's detector needs the whole Apache POI stack for this; this class needs only
 * the JDK. Only the central directory and that one small entry are read, never the other entries.
 */
final class OoxmlInspector {

    private static final String CONTENT_TYPES_ENTRY = "[Content_Types].xml";
    private static final int MAX_CONTENT_TYPES_BYTES = 256 * 1024;
    private static final String WORD_PART = "/word/document.xml";
    private static final String WORD_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    private static final String EXCEL_PART = "/xl/workbook.xml";
    private static final String EXCEL_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml";

    private OoxmlInspector() {
    }

    /**
     * Inspects a zip file.
     *
     * @param zipFile the file on disk
     * @return {@link AllowedFileType#DOCX} or {@link AllowedFileType#XLSX}, or empty for any other zip
     *         (or a damaged one)
     */
    static Optional<AllowedFileType> inspect(Path zipFile) {
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            ZipEntry types = zip.getEntry(CONTENT_TYPES_ENTRY);
            if (types == null) {
                return Optional.empty();
            }
            try (InputStream in = zip.getInputStream(types)) {
                byte[] xml = in.readNBytes(MAX_CONTENT_TYPES_BYTES + 1);
                if (xml.length > MAX_CONTENT_TYPES_BYTES) {
                    return Optional.empty();
                }
                return parse(xml, zip);
            }
        } catch (IOException | XMLStreamException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Reads the Override declarations of the content types file with a parser that refuses DTDs
     * and external entities.
     *
     * @param xml the bytes of {@code [Content_Types].xml}
     * @param zip the package, to check that the declared main part exists
     * @return the matching type, or empty
     * @throws XMLStreamException if the XML is malformed
     */
    private static Optional<AllowedFileType> parse(byte[] xml, ZipFile zip) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader reader = factory.createXMLStreamReader(new java.io.ByteArrayInputStream(xml));
        try {
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT && "Override".equals(reader.getLocalName())) {
                    String part = reader.getAttributeValue(null, "PartName");
                    String type = reader.getAttributeValue(null, "ContentType");
                    if (WORD_PART.equals(part) && WORD_TYPE.equals(type) && hasPart(zip, part)) {
                        return Optional.of(AllowedFileType.DOCX);
                    }
                    if (EXCEL_PART.equals(part) && EXCEL_TYPE.equals(type) && hasPart(zip, part)) {
                        return Optional.of(AllowedFileType.XLSX);
                    }
                }
            }
        } finally {
            reader.close();
        }
        return Optional.empty();
    }

    /**
     * Checks that the package really contains the declared part.
     *
     * @param zip the package
     * @param partName the part name starting with a slash
     * @return {@code true} if the entry exists
     */
    private static boolean hasPart(ZipFile zip, String partName) {
        return zip.getEntry(partName.substring(1)) != null;
    }
}
