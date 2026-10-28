package com.creditsai.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.creditsai.exception.BusinessRuleException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CreditTest {

    private static Client client(long id, boolean archived) {
        Client c = new Client();
        ReflectionTestUtils.setField(c, "id", id);
        if (archived) {
            c.archive();
        }
        return c;
    }

    private static Credit credit(Client client) {
        Credit credit = new Credit();
        credit.assignTo(client);
        return credit;
    }

    private static CreditPayment row(int n) {
        return CreditPayment.builder().paymentNumber(n).paymentDate(LocalDate.of(2026, n, 1))
                .payment(BigDecimal.TEN).principal(BigDecimal.TEN).interest(BigDecimal.ZERO)
                .remainingBalance(BigDecimal.ZERO).build();
    }

    // ---- defaults ----

    @Test
    void newCredit_defaults_isActiveAndNotArchived() {
        // when
        Credit credit = new Credit();

        // then
        assertThat(credit.getStatus()).isEqualTo(CreditStatus.ACTIVE);
        assertThat(credit.isArchived()).isFalse();
        assertThat(credit.getArchivedAt()).isNull();
    }

    // ---- assignTo ----

    @Test
    void assignTo_activeClient_setsClient() {
        // given
        Client client = client(1, false);

        // when
        Credit credit = credit(client);

        // then
        assertThat(credit.getClient()).isSameAs(client);
    }

    @Test
    void assignTo_archivedClientOnNewCredit_throwsBusinessRuleException() {
        // given
        Credit credit = new Credit();

        // when / then
        assertThatThrownBy(() -> credit.assignTo(client(1, true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("archived");
        assertThat(credit.getClient()).isNull();
    }

    @Test
    void assignTo_otherArchivedClient_throwsBusinessRuleException() {
        // given
        Credit credit = credit(client(1, false));

        // when / then
        assertThatThrownBy(() -> credit.assignTo(client(2, true))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void assignTo_sameClientThatIsNowArchived_isAllowed() {
        // given: the client is archived after the credit was created
        Client client = client(1, false);
        Credit credit = credit(client);
        client.archive();

        // when
        credit.assignTo(client);

        // then
        assertThat(credit.getClient()).isSameAs(client);
    }

    // ---- applyCollateral ----

    @Test
    void applyCollateral_withCollateral_storesValueAndDescription() {
        // given
        Credit credit = new Credit();

        // when
        credit.applyCollateral(true, new BigDecimal("5000.00"), "Flat");

        // then
        assertThat(credit.isHasCollateral()).isTrue();
        assertThat(credit.getCollateralValue()).isEqualByComparingTo("5000");
        assertThat(credit.getCollateralDescription()).isEqualTo("Flat");
    }

    @Test
    void applyCollateral_withoutCollateral_clearsValueAndDescription() {
        // given
        Credit credit = new Credit();
        credit.applyCollateral(true, BigDecimal.ONE, "Flat");

        // when
        credit.applyCollateral(false, BigDecimal.TEN, "ignored");

        // then
        assertThat(credit.isHasCollateral()).isFalse();
        assertThat(credit.getCollateralValue()).isNull();
        assertThat(credit.getCollateralDescription()).isNull();
    }

    @Test
    void applyCollateral_missingValue_throwsBusinessRuleException() {
        // when / then
        assertThatThrownBy(() -> new Credit().applyCollateral(true, null, "Flat"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void applyCollateral_blankDescription_throwsBusinessRuleException() {
        // when / then
        assertThatThrownBy(() -> new Credit().applyCollateral(true, BigDecimal.ONE, "  "))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ---- payments ----

    @Test
    void replacePayments_newRows_replacesAllAndSetsTermAndInstalment() {
        // given
        Credit credit = new Credit();
        credit.replacePayments(1, BigDecimal.ONE, List.of(row(1)));

        // when
        credit.replacePayments(2, BigDecimal.TEN, List.of(row(1), row(2)));

        // then
        assertThat(credit.getPayments()).hasSize(2);
        assertThat(credit.getTermMonths()).isEqualTo(2);
        assertThat(credit.getMonthlyPayment()).isEqualByComparingTo("10");
        assertThat(credit.getPayments()).allSatisfy(p -> assertThat(p.getCredit()).isSameAs(credit));
    }

    @Test
    void getPayments_result_isReadOnly() {
        // given
        Credit credit = new Credit();

        // when / then
        List<CreditPayment> payments = credit.getPayments();
        assertThatThrownBy(() -> payments.add(row(1))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void clearPayments_existingRows_removesThem() {
        // given
        Credit credit = new Credit();
        credit.replacePayments(1, BigDecimal.ONE, List.of(row(1)));

        // when
        credit.clearPayments();

        // then
        assertThat(credit.getPayments()).isEmpty();
    }

    // ---- changeStatus ----

    @Test
    void changeStatus_deactivate_setsInactive() {
        // given
        Credit credit = credit(client(1, false));

        // when
        credit.changeStatus(CreditStatus.INACTIVE);

        // then
        assertThat(credit.getStatus()).isEqualTo(CreditStatus.INACTIVE);
    }

    @Test
    void changeStatus_activateInactiveCredit_setsActive() {
        // given
        Credit credit = credit(client(1, false));
        credit.changeStatus(CreditStatus.INACTIVE);

        // when
        credit.changeStatus(CreditStatus.ACTIVE);

        // then
        assertThat(credit.getStatus()).isEqualTo(CreditStatus.ACTIVE);
    }

    @Test
    void changeStatus_activateArchivedCredit_throwsBusinessRuleException() {
        // given
        Credit credit = credit(client(1, false));
        credit.changeStatus(CreditStatus.INACTIVE);
        credit.archive();

        // when / then
        assertThatThrownBy(() -> credit.changeStatus(CreditStatus.ACTIVE))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("An archived credit cannot be activated. Restore it first.");
        assertThat(credit.getStatus()).isEqualTo(CreditStatus.INACTIVE);
    }

    @Test
    void changeStatus_activateCreditOfArchivedClient_throwsBusinessRuleException() {
        // given
        Client client = client(1, false);
        Credit credit = credit(client);
        credit.changeStatus(CreditStatus.INACTIVE);
        client.archive();

        // when / then
        assertThatThrownBy(() -> credit.changeStatus(CreditStatus.ACTIVE))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("client of this credit is archived");
    }

    @Test
    void changeStatus_deactivateCreditOfArchivedClient_isAllowed() {
        // given
        Client client = client(1, false);
        Credit credit = credit(client);
        client.archive();

        // when
        credit.changeStatus(CreditStatus.INACTIVE);

        // then
        assertThat(credit.getStatus()).isEqualTo(CreditStatus.INACTIVE);
    }

    // ---- archive / restore ----

    @Test
    void archive_inactiveCredit_setsArchivedAndTimestamp() {
        // given
        Credit credit = credit(client(1, false));
        credit.changeStatus(CreditStatus.INACTIVE);

        // when
        credit.archive();

        // then
        assertThat(credit.isArchived()).isTrue();
        assertThat(credit.getArchivedAt()).isNotNull();
    }

    @Test
    void archive_activeCredit_throwsBusinessRuleException() {
        // given
        Credit credit = credit(client(1, false));

        // when / then
        assertThatThrownBy(credit::archive)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("An active credit cannot be archived. Deactivate it first.");
        assertThat(credit.isArchived()).isFalse();
    }

    @Test
    void archive_alreadyArchived_throwsBusinessRuleException() {
        // given
        Credit credit = credit(client(1, false));
        credit.changeStatus(CreditStatus.INACTIVE);
        credit.archive();

        // when / then
        assertThatThrownBy(credit::archive)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This credit is already archived.");
    }

    @Test
    void restore_archivedCredit_clearsArchivedAndStaysInactive() {
        // given
        Credit credit = credit(client(1, false));
        credit.changeStatus(CreditStatus.INACTIVE);
        credit.archive();

        // when
        credit.restore();

        // then
        assertThat(credit.isArchived()).isFalse();
        assertThat(credit.getArchivedAt()).isNull();
        assertThat(credit.getStatus()).isEqualTo(CreditStatus.INACTIVE);
    }

    @Test
    void restore_notArchivedCredit_throwsBusinessRuleException() {
        // when / then
        assertThatThrownBy(() -> credit(client(1, false)).restore())
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This credit is not archived.");
    }
}
