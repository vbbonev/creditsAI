package com.creditsai.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.creditsai.exception.BusinessRuleException;
import com.creditsai.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ClientAttachmentRulesTest {

    private static final long MB = 1024 * 1024;

    private static ClientAttachment file(String name, long size) {
        return ClientAttachment.builder().fileName(name).contentType("text/plain").sizeBytes(size)
                .sha256("0".repeat(64)).uploadedBy("admin1").build();
    }

    @Test
    void addAttachment_validFile_isAttachedAndLinkedToClient() {
        // given
        Client client = new Client();

        // when
        client.addAttachment(file("a.txt", 10), 30, 20 * MB);

        // then
        assertThat(client.getAttachments()).hasSize(1);
        assertThat(client.getAttachments().get(0).getClient()).isSameAs(client);
    }

    @Test
    void addAttachment_duplicateName_throwsReadableConflict() {
        // given
        Client client = new Client();
        client.addAttachment(file("a.txt", 10), 30, 20 * MB);

        // when / then
        assertThatThrownBy(() -> client.addAttachment(file("a.txt", 5), 30, 20 * MB))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A file named 'a.txt' already exists for this client.");
    }

    @Test
    void addAttachment_namesDifferingInCase_areDifferentNamesForTheEntity() {
        // given: lower-casing is the sanitizer's job; the entity compares plain names
        Client client = new Client();
        client.addAttachment(file("a.pdf", 10), 30, 20 * MB);

        // when
        client.addAttachment(file("A.pdf", 10), 30, 20 * MB);

        // then
        assertThat(client.getAttachments()).hasSize(2);
    }

    @Test
    void addAttachment_beyondMaxFiles_throwsReadableConflict() {
        // given
        Client client = new Client();
        client.addAttachment(file("1.txt", 1), 2, 20 * MB);
        client.addAttachment(file("2.txt", 1), 2, 20 * MB);

        // when / then
        assertThatThrownBy(() -> client.addAttachment(file("3.txt", 1), 2, 20 * MB))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A client can have at most 2 files.");
    }

    @Test
    void addAttachment_beyondTotalSize_throwsReadableConflict() {
        // given
        Client client = new Client();
        client.addAttachment(file("1.bin", 15 * MB), 30, 20 * MB);

        // when / then
        assertThatThrownBy(() -> client.addAttachment(file("2.bin", 6 * MB), 30, 20 * MB))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("All files of a client together may take at most 20 MB. This client already uses 15 MB.");
        assertThat(client.getAttachments()).hasSize(1);
    }

    @Test
    void addAttachment_exactlyAtTheLimits_isAccepted() {
        // given
        Client client = new Client();
        client.addAttachment(file("1.bin", 15 * MB), 2, 20 * MB);

        // when
        client.addAttachment(file("2.bin", 5 * MB), 2, 20 * MB);

        // then
        assertThat(client.getAttachments()).hasSize(2);
    }

    @Test
    void removeAttachment_existing_removesAndReturnsIt() {
        // given
        Client client = new Client();
        ClientAttachment a = file("a.txt", 1);
        ReflectionTestUtils.setField(a, "id", 5L);
        client.addAttachment(a, 30, 20 * MB);

        // when
        ClientAttachment removed = client.removeAttachment(5L);

        // then
        assertThat(removed).isSameAs(a);
        assertThat(client.getAttachments()).isEmpty();
    }

    @Test
    void removeAttachment_unknownId_throwsNotFound() {
        // given
        Client client = new Client();

        // when / then
        assertThatThrownBy(() -> client.removeAttachment(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Attachment 99 was not found.");
    }

    @Test
    void getAttachments_returnsReadOnlyView() {
        // given
        Client client = new Client();

        // when / then
        assertThatThrownBy(() -> client.getAttachments().add(file("a.txt", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void archive_clientWithAttachments_leavesAttachmentsUntouched() {
        // given
        Client client = new Client();
        client.addAttachment(file("a.txt", 1), 30, 20 * MB);

        // when
        client.archive();

        // then
        assertThat(client.getAttachments()).hasSize(1);
    }
}
