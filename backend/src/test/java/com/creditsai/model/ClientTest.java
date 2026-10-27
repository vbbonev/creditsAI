package com.creditsai.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.creditsai.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

class ClientTest {

    @Test
    void archive_activeClient_setsArchivedAndTimestamp() {
        // given
        Client client = new Client();

        // when
        client.archive();

        // then
        assertThat(client.isArchived()).isTrue();
        assertThat(client.getArchivedAt()).isNotNull();
    }

    @Test
    void archive_alreadyArchived_throwsBusinessRuleException() {
        // given
        Client client = new Client();
        client.archive();

        // when / then
        assertThatThrownBy(client::archive)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This client is already archived.");
    }

    @Test
    void restore_archivedClient_clearsArchivedAndTimestamp() {
        // given
        Client client = new Client();
        client.archive();

        // when
        client.restore();

        // then
        assertThat(client.isArchived()).isFalse();
        assertThat(client.getArchivedAt()).isNull();
    }

    @Test
    void restore_activeClient_throwsBusinessRuleException() {
        // given
        Client client = new Client();

        // when / then
        assertThatThrownBy(client::restore)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This client is not archived.");
    }
}
