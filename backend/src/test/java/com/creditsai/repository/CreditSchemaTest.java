package com.creditsai.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.creditsai.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Checks the constraints of the Liquibase changesets 005 and 006 directly with SQL. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CreditSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long clientId;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from credit_payments");
        jdbc.update("delete from credits");
        jdbc.update("delete from client_attachments");
        jdbc.update("delete from clients");
        clientId = jdbc.queryForObject(
                "insert into clients (name, identifier, address) values ('A', '1', 'S') returning id", Long.class);
    }

    private long insertCredit(String amount, String collateral, String hasCollateral, String start, String end,
            String rate, String status) {
        return jdbc.queryForObject("""
                insert into credits (client_id, type, amount, currency, interest_rate, start_date, end_date,
                    term_months, monthly_payment, has_collateral, collateral_value, collateral_description, status)
                values (?, 'CONSUMER', %s, 'EUR', %s, date '%s', date '%s', 1, 1, %s, %s, %s, '%s') returning id
                """.formatted(amount, rate, start, end, hasCollateral,
                        collateral == null ? "null" : collateral,
                        collateral == null ? "null" : "'x'", status), Long.class, clientId);
    }

    private long validCredit() {
        return insertCredit("1000", null, "false", "2026-01-01", "2026-02-01", "5", "ACTIVE");
    }

    @Test
    void insert_validCredit_getsNumberStatusAndArchivedDefaults() {
        // when
        long id = jdbc.queryForObject("""
                insert into credits (client_id, type, amount, currency, interest_rate, start_date, end_date,
                    term_months, monthly_payment, has_collateral)
                values (?, 'AUTO', 10, 'USD', 0, date '2026-01-01', date '2026-02-01', 1, 10, false) returning id
                """, Long.class, clientId);

        // then
        Map<String, Object> row = jdbc.queryForMap("select credit_number, status, archived from credits where id = ?", id);
        assertThat((String) row.get("credit_number")).matches("CR-\\d{4}-\\d{6}");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("archived")).isEqualTo(false);
    }

    @Test
    void insert_nonPositiveAmount_isRejected() {
        assertThatThrownBy(() -> insertCredit("0", null, "false", "2026-01-01", "2026-02-01", "5", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void insert_negativeRate_isRejected() {
        assertThatThrownBy(() -> insertCredit("1000", null, "false", "2026-01-01", "2026-02-01", "-1", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void insert_endNotAfterStart_isRejected() {
        assertThatThrownBy(() -> insertCredit("1000", null, "false", "2026-02-01", "2026-02-01", "5", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void insert_unknownStatus_isRejected() {
        assertThatThrownBy(() -> insertCredit("1000", null, "false", "2026-01-01", "2026-02-01", "5", "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void insert_collateralFlagWithoutValue_isRejected() {
        assertThatThrownBy(() -> insertCredit("1000", null, "true", "2026-01-01", "2026-02-01", "5", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void insert_collateralValueWithoutFlag_isRejected() {
        assertThatThrownBy(() -> insertCredit("1000", "100", "false", "2026-01-01", "2026-02-01", "5", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void insert_collateralWithValueAndDescription_isAccepted() {
        assertThat(insertCredit("1000", "100", "true", "2026-01-01", "2026-02-01", "5", "ACTIVE")).isPositive();
    }

    @Test
    void insert_unknownClient_isRejected() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into credits (client_id, type, amount, currency, interest_rate, start_date, end_date,
                    term_months, monthly_payment, has_collateral)
                values (-1, 'AUTO', 10, 'USD', 0, date '2026-01-01', date '2026-02-01', 1, 10, false)
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void delete_clientWithCredit_isRejectedByForeignKey() {
        // given
        validCredit();

        // when / then
        assertThatThrownBy(() -> jdbc.update("delete from clients where id = ?", clientId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void creditNumbers_areUnique() {
        // given
        long a = validCredit();

        // when / then
        assertThatThrownBy(() -> jdbc.update("""
                update credits set credit_number = (select credit_number from credits where id = ?) where id = ?
                """, a, validCredit())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void payments_duplicateNumberPerCredit_isRejected() {
        // given
        long id = validCredit();
        String sql = "insert into credit_payments (credit_id, payment_number, payment_date, payment, principal, interest,"
                + " remaining_balance) values (?, ?, date '2026-02-01', 1, 1, 0, 0)";
        jdbc.update(sql, id, 1);
        jdbc.update(sql, id, 2);
        List<Integer> numbers = jdbc.queryForList(
                "select payment_number from credit_payments where credit_id = ? order by 1", Integer.class, id);
        assertThat(numbers).containsExactly(1, 2);

        // when / then
        assertThatThrownBy(() -> jdbc.update(sql, id, 1)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void payments_numberBelowOne_isRejected() {
        // given
        long id = validCredit();

        // when / then
        assertThatThrownBy(() -> jdbc.update("""
                insert into credit_payments (credit_id, payment_number, payment_date, payment, principal, interest,
                    remaining_balance) values (?, 0, date '2026-02-01', 1, 1, 0, 0)
                """, id)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void payments_unknownCredit_isRejected() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into credit_payments (credit_id, payment_number, payment_date, payment, principal, interest,
                    remaining_balance) values (-1, 1, date '2026-02-01', 1, 1, 0, 0)
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "credits, amount, 12, 2",
            "credits, monthly_payment, 12, 2",
            "credits, collateral_value, 12, 2",
            "credits, interest_rate, 7, 4",
            "credit_payments, payment, 12, 2",
            "credit_payments, principal, 12, 2",
            "credit_payments, interest, 12, 2",
            "credit_payments, remaining_balance, 12, 2"
    })
    void columns_numericColumns_haveExpectedPrecisionAndScale(String table, String column, int precision, int scale) {
        // when
        Map<String, Object> row = jdbc.queryForMap(
                "select numeric_precision, numeric_scale from information_schema.columns "
                        + "where table_name = ? and column_name = ?", table, column);

        // then
        assertThat(((Number) row.get("numeric_precision")).intValue()).isEqualTo(precision);
        assertThat(((Number) row.get("numeric_scale")).intValue()).isEqualTo(scale);
    }

    @Test
    void insert_amountWithElevenIntegerDigits_isRejected() {
        assertThatThrownBy(() -> insertCredit("10000000000", null, "false", "2026-01-01", "2026-02-01", "5", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
