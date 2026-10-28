package com.creditsai.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.creditsai.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FileNameSanitizerTest {

    @Test
    void sanitize_plainName_isUnchanged() {
        assertThat(FileNameSanitizer.sanitize("contract 2026.pdf")).isEqualTo("contract 2026.pdf");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../../etc/passwd", "C:\\Users\\x\\passwd", "a/b/passwd", "..\\..\\passwd"})
    void sanitize_nameWithPath_keepsOnlyLastSegment(String raw) {
        assertThat(FileNameSanitizer.sanitize(raw)).isEqualTo("passwd");
    }

    @Test
    void sanitize_decomposedUnicode_isNfcNormalized() {
        // given: "e" + combining acute accent
        assertThat(FileNameSanitizer.sanitize("cafe\u0301.txt")).isEqualTo("caf\u00e9.txt");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", ".", "..", "a/", "a\\", "/", "dir/..", "x/."})
    void sanitize_emptyOrDotName_isRejected(String raw) {
        assertThatThrownBy(() -> FileNameSanitizer.sanitize(raw)).isInstanceOf(InvalidRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\u0000b.txt", "a\nb.txt", "a\tb.txt", "a\u007fb.txt"})
    void sanitize_controlCharacters_areRejected(String raw) {
        assertThatThrownBy(() -> FileNameSanitizer.sanitize(raw))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("A file name must not contain control characters.");
    }

    @Test
    void sanitize_mixedCase_isLowerCased() {
        assertThat(FileNameSanitizer.sanitize("Report.PDF")).isEqualTo("report.pdf");
        assertThat(FileNameSanitizer.sanitize("\u00c4.PDF")).isEqualTo("\u00e4.pdf");
        assertThat(FileNameSanitizer.sanitize("TITLE I.TXT")).isEqualTo("title i.txt");
    }

    @Test
    void sanitize_decomposedUppercase_becomesComposedLowerCase() {
        // given: "A" + combining diaeresis
        assertThat(FileNameSanitizer.sanitize("A\u0308.pdf")).isEqualTo("\u00e4.pdf");
    }

    @Test
    void sanitize_lengthIsCheckedAfterLowerCasing() {
        // given: U+0130 (I with dot) lowers to "i" + U+0307, growing from 2 to 3 bytes; 85 x 3 = 255 fits, 86 x 3 does not
        assertThat(FileNameSanitizer.sanitize("\u0130".repeat(85))).hasSize(170);
        assertThatThrownBy(() -> FileNameSanitizer.sanitize("\u0130".repeat(86)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("A file name must be at most 255 bytes long.");
    }

    @Test
    void sanitize_tooLongInUtf8Bytes_isRejected() {
        // given: 128 two-byte characters = 256 bytes, though only 128 chars
        String name = "\u00e9".repeat(128);

        // when / then
        assertThatThrownBy(() -> FileNameSanitizer.sanitize(name))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("A file name must be at most 255 bytes long.");
        assertThat(FileNameSanitizer.sanitize("a".repeat(255))).hasSize(255);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\u202Etxt.exe", "a\u202Db.txt", "a\u2066b.txt", "a\u2069b.txt", "a\u200Bb.txt",
            "a\u200Cb.txt", "a\u200Db.txt", "\uFEFFa.txt", "a\u2028b.txt", "a\u2029b.txt"})
    void sanitize_formatAndLineSeparatorCharacters_areRejected(String raw) {
        assertThatThrownBy(() -> FileNameSanitizer.sanitize(raw))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("A file name must not contain invisible or direction-changing characters.");
    }
}
