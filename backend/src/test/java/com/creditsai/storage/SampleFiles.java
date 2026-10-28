package com.creditsai.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Minimal real file contents for every allowed type, plus a few that must be rejected. */
public final class SampleFiles {

    private SampleFiles() {
    }

    public static byte[] pdf() {
        return ("%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[]/Count 0>>endobj\n"
                + "trailer<</Root 1 0 R>>\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] png() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    }

    public static byte[] jpeg() {
        return Base64.getDecoder().decode(
                "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////"
                        + "wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=");
    }

    public static byte[] gif() {
        return Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");
    }

    public static byte[] webp() {
        return Base64.getDecoder().decode(
                "UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");
    }

    public static byte[] heic() {
        byte[] ftyp = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0,
                'm', 'i', 'f', '1', 'h', 'e', 'i', 'c'};
        byte[] filler = new byte[64];
        byte[] all = new byte[ftyp.length + filler.length];
        System.arraycopy(ftyp, 0, all, 0, ftyp.length);
        return all;
    }

    public static byte[] tiff() {
        return new byte[] {'I', 'I', 42, 0, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    }

    public static byte[] txt() {
        return "Just some plain text.\nSecond line.\n".getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] html() {
        return "<!DOCTYPE html><html><body><script>alert(1)</script></body></html>".getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] svg() {
        return ("<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>")
                .getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] exe() {
        return new byte[] {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0, (byte) 0xff, (byte) 0xff, 0, 0};
    }

    /** A zip that is not an office document. */
    public static byte[] plainZip() {
        return zip(entries("hello.txt", "hello"));
    }

    public static byte[] docx() {
        return zip(entries(
                "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                        + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                        + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                        + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                        + "</Types>",
                "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                        + "</Relationships>",
                "word/document.xml", "<?xml version=\"1.0\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body/></w:document>"));
    }

    public static byte[] xlsx() {
        return zip(entries(
                "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                        + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                        + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                        + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                        + "</Types>",
                "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                        + "</Relationships>",
                "xl/workbook.xml", "<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheets/></workbook>"));
    }

    /** An ODT: the first entry is the uncompressed {@code mimetype}, as the OpenDocument spec requires. */
    public static byte[] odt() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                byte[] mime = "application/vnd.oasis.opendocument.text".getBytes(StandardCharsets.US_ASCII);
                ZipEntry entry = new ZipEntry("mimetype");
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(mime.length);
                entry.setCompressedSize(mime.length);
                CRC32 crc = new CRC32();
                crc.update(mime);
                entry.setCrc(crc.getValue());
                zip.putNextEntry(entry);
                zip.write(mime);
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry("content.xml"));
                zip.write("<office:document-content/>".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every allowed type: sample file name -> content. */
    public static Map<String, byte[]> allAllowed() {
        Map<String, byte[]> all = new LinkedHashMap<>();
        all.put("a.pdf", pdf());
        all.put("a.docx", docx());
        all.put("a.xlsx", xlsx());
        all.put("a.odt", odt());
        all.put("a.txt", txt());
        all.put("a.jpg", jpeg());
        all.put("a.png", png());
        all.put("a.gif", gif());
        all.put("a.webp", webp());
        all.put("a.heic", heic());
        all.put("a.tiff", tiff());
        return all;
    }

    private static String[] entries(String... pairs) {
        return pairs;
    }

    private static byte[] zip(String[] pairs) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                for (int i = 0; i < pairs.length; i += 2) {
                    zip.putNextEntry(new ZipEntry(pairs[i]));
                    zip.write(pairs[i + 1].getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
