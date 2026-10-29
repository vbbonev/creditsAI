package com.creditsai.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.creditsai.exception.BusinessRuleException;
import com.creditsai.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CreditAttachmentRulesTest {

    private static final long MB = 1024 * 1024;

    private static CreditAttachment file(String name, long size) {
        return CreditAttachment.builder().fileName(name).contentType("text/plain").sizeBytes(size)
                .sha256("0".repeat(64)).uploadedBy("admin1").build();
    }

    private static AttachmentDraft draft(String name, long size) {
        return new AttachmentDraft(name, "text/plain", size, "0".repeat(64), "admin1");
    }

    @Test
    void attach_validFile_isAttachedAndLinkedToCredit() {
        // given
        Credit credit = new Credit();

        // when
        credit.attach(draft("a.txt", 10), 30, 20 * MB);

        // then
        assertThat(credit.getAttachments()).hasSize(1);
        assertThat(credit.getAttachments().get(0).getCredit()).isSameAs(credit);
    }

    @Test
    void attach_duplicateName_throwsReadableConflict() {
        // given
        Credit credit = new Credit();
        credit.attach(draft("a.txt", 10), 30, 20 * MB);

        // when / then
        assertThatThrownBy(() -> credit.attach(draft("a.txt", 5), 30, 20 * MB))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A file named 'a.txt' already exists for this credit.");
    }

    @Test
    void attach_namesDifferingInCase_areDifferentNamesForTheEntity() {
        // given: lower-casing is the sanitizer's job; the entity compares plain names
        Credit credit = new Credit();
        credit.attach(draft("a.pdf", 10), 30, 20 * MB);

        // when
        credit.attach(draft("A.pdf", 10), 30, 20 * MB);

        // then
        assertThat(credit.getAttachments()).hasSize(2);
    }

    @Test
    void attach_beyondMaxFiles_throwsReadableConflict() {
        // given
        Credit credit = new Credit();
        credit.attach(draft("1.txt", 1), 2, 20 * MB);
        credit.attach(draft("2.txt", 1), 2, 20 * MB);

        // when / then
        assertThatThrownBy(() -> credit.attach(draft("3.txt", 1), 2, 20 * MB))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A credit can have at most 2 files.");
    }

    @Test
    void attach_beyondTotalSize_throwsReadableConflict() {
        // given
        Credit credit = new Credit();
        credit.attach(draft("1.bin", 15 * MB), 30, 20 * MB);

        // when / then
        assertThatThrownBy(() -> credit.attach(draft("2.bin", 6 * MB), 30, 20 * MB))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("All files of a credit together may take at most 20 MB. This credit already uses 15 MB.");
        assertThat(credit.getAttachments()).hasSize(1);
    }

    @Test
    void attach_exactlyAtTheLimits_isAccepted() {
        // given
        Credit credit = new Credit();
        credit.attach(draft("1.bin", 15 * MB), 2, 20 * MB);

        // when
        credit.attach(draft("2.bin", 5 * MB), 2, 20 * MB);

        // then
        assertThat(credit.getAttachments()).hasSize(2);
    }

    @Test
    void removeAttachment_existing_removesAndReturnsIt() {
        // given
        Credit credit = new Credit();
        CreditAttachment a = credit.attach(draft("a.txt", 1), 30, 20 * MB);
        ReflectionTestUtils.setField(a, "id", 5L);

        // when
        CreditAttachment removed = credit.removeAttachment(5L);

        // then
        assertThat(removed).isSameAs(a);
        assertThat(credit.getAttachments()).isEmpty();
    }

    @Test
    void removeAttachment_unknownId_throwsNotFound() {
        // given
        Credit credit = new Credit();

        // when / then
        assertThatThrownBy(() -> credit.removeAttachment(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Attachment 99 was not found.");
    }

    @Test
    void getAttachments_returnsReadOnlyView() {
        // given
        Credit credit = new Credit();

        // when / then
        assertThatThrownBy(() -> credit.getAttachments().add(file("a.txt", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void archive_creditWithAttachments_leavesAttachmentsUntouched() {
        // given
        Credit credit = new Credit();
        credit.attach(draft("a.txt", 1), 30, 20 * MB);

        // when
        credit.changeStatus(CreditStatus.INACTIVE);
        credit.archive();

        // then
        assertThat(credit.getAttachments()).hasSize(1);
    }
}
