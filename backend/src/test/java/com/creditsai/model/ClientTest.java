package com.creditsai.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.creditsai.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

    private static void addCredit(Client client, CreditStatus status, boolean archived) {
        Credit credit = new Credit();
        credit.assignTo(client);
        credit.changeStatus(status);
        if (archived) {
            credit.changeStatus(CreditStatus.INACTIVE);
            credit.archive();
        }
        @SuppressWarnings("unchecked")
        java.util.List<Credit> credits = (java.util.List<Credit>) ReflectionTestUtils.getField(client, "credits");
        credits.add(credit);
    }

    @Test
    void archive_withActiveCredit_throwsBusinessRuleExceptionAndStaysUnarchived() {
        // given
        Client client = new Client();
        addCredit(client, CreditStatus.ACTIVE, false);

        // when / then
        assertThatThrownBy(client::archive)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Client has active credits and cannot be archived.");
        assertThat(client.isArchived()).isFalse();
        assertThat(client.getArchivedAt()).isNull();
    }

    @Test
    void archive_withOnlyInactiveCredit_succeeds() {
        // given
        Client client = new Client();
        addCredit(client, CreditStatus.INACTIVE, false);

        // when
        client.archive();

        // then
        assertThat(client.isArchived()).isTrue();
    }

    @Test
    void archive_withOnlyArchivedCredit_succeeds() {
        // given
        Client client = new Client();
        addCredit(client, CreditStatus.INACTIVE, true);

        // when
        client.archive();

        // then
        assertThat(client.isArchived()).isTrue();
    }

    @Test
    void archive_withInactiveAndActiveCredits_throwsBusinessRuleException() {
        // given
        Client client = new Client();
        addCredit(client, CreditStatus.INACTIVE, false);
        addCredit(client, CreditStatus.ACTIVE, false);

        // when / then
        assertThatThrownBy(client::archive).isInstanceOf(BusinessRuleException.class);
    }
}
