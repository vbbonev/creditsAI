package com.creditsai.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FileTypeDetectorTest {

    private final FileTypeDetector detector = new FileTypeDetector();

    private Optional<AllowedFileType> detect(byte[] bytes) {
        return detector.detect(new ByteArrayInputStream(bytes));
    }

    private static byte[] sample(AllowedFileType type) {
        return switch (type) {
            case PDF -> SampleFiles.pdf();
            case DOCX -> SampleFiles.docx();
            case XLSX -> SampleFiles.xlsx();
            case ODT -> SampleFiles.odt();
            case TXT -> SampleFiles.txt();
            case JPEG -> SampleFiles.jpeg();
            case PNG -> SampleFiles.png();
            case GIF -> SampleFiles.gif();
            case WEBP -> SampleFiles.webp();
            case HEIC -> SampleFiles.heic();
            case TIFF -> SampleFiles.tiff();
        };
    }

    @ParameterizedTest
    @EnumSource(AllowedFileType.class)
    void detect_sampleOfEachAllowedType_returnsThatType(AllowedFileType type) {
        // when / then
        assertThat(detect(sample(type))).contains(type);
    }

    @Test
    void detect_zipBasedUploads_leaveNoTempFilesBehind() throws Exception {
        // given
        java.nio.file.Path tmp = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"));
        long before = countTikaTemps(tmp);

        // when
        for (int i = 0; i < 5; i++) {
            assertThat(detect(SampleFiles.docx())).contains(AllowedFileType.DOCX);
            assertThat(detect(SampleFiles.xlsx())).contains(AllowedFileType.XLSX);
            assertThat(detect(SampleFiles.plainZip())).isEmpty();
        }

        // then
        assertThat(countTikaTemps(tmp)).isEqualTo(before);
    }

    private static long countTikaTemps(java.nio.file.Path tmp) throws java.io.IOException {
        try (var s = java.nio.file.Files.list(tmp)) {
            return s.filter(p -> p.getFileName().toString().startsWith("apache-tika-")).count();
        }
    }

    @Test
    void detect_plainZip_isRejected() {
        assertThat(detect(SampleFiles.plainZip())).isEmpty();
    }

    @Test
    void detect_html_isRejected() {
        assertThat(detect(SampleFiles.html())).isEmpty();
    }

    @Test
    void detect_svg_isRejected() {
        assertThat(detect(SampleFiles.svg())).isEmpty();
    }

    @Test
    void detect_executable_isRejected() {
        assertThat(detect(SampleFiles.exe())).isEmpty();
    }

    @Test
    void detect_pngBytes_areNotMistakenForPdfWhateverTheName() {
        // when / then: only the content counts
        assertThat(detect(SampleFiles.png())).contains(AllowedFileType.PNG);
    }

    @Test
    void acceptsFileName_extensionIsCaseInsensitiveAndMustMatch() {
        // when / then
        assertThat(AllowedFileType.JPEG.acceptsFileName("Photo.JPG")).isTrue();
        assertThat(AllowedFileType.JPEG.acceptsFileName("photo.jpeg")).isTrue();
        assertThat(AllowedFileType.PNG.acceptsFileName("photo.pdf")).isFalse();
        assertThat(AllowedFileType.PNG.acceptsFileName("noextension")).isFalse();
    }
}
