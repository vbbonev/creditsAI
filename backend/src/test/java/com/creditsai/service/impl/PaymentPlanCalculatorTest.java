package com.creditsai.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.creditsai.exception.InvalidRequestException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PaymentPlanCalculatorTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static void assertConsistent(PaymentPlan plan, BigDecimal amount) {
        BigDecimal balance = amount.setScale(2);
        BigDecimal interestSum = BigDecimal.ZERO;
        BigDecimal paidSum = BigDecimal.ZERO;
        BigDecimal principalSum = BigDecimal.ZERO;
        for (PaymentPlan.Row row : plan.rows()) {
            assertThat(row.payment()).isEqualByComparingTo(row.principal().add(row.interest()));
            assertThat(row.remainingBalance()).isEqualByComparingTo(balance.subtract(row.principal()));
            balance = row.remainingBalance();
            interestSum = interestSum.add(row.interest());
            paidSum = paidSum.add(row.payment());
            principalSum = principalSum.add(row.principal());
        }
        assertThat(balance).isEqualByComparingTo("0.00");
        assertThat(principalSum).isEqualByComparingTo(amount);
        assertThat(plan.totalInterest()).isEqualByComparingTo(interestSum);
        assertThat(plan.totalPaid()).isEqualByComparingTo(paidSum);
        assertThat(plan.totalPaid()).isEqualByComparingTo(amount.add(interestSum));
    }

    @Test
    void calculate_tenThousandAtTwelvePercentFor12Months_hasKnownInstalment() {
        // when
        PaymentPlan plan = PaymentPlanCalculator.calculate(bd("10000"), bd("12"),
                LocalDate.of(2026, 1, 15), LocalDate.of(2027, 1, 15));

        // then: 10000 * 0.01 / (1 - 1.01^-12) = 888.4878...
        assertThat(plan.termMonths()).isEqualTo(12);
        assertThat(plan.monthlyPayment()).isEqualTo(bd("888.49"));
        assertThat(plan.rows()).hasSize(12);
        PaymentPlan.Row first = plan.rows().get(0);
        assertThat(first.number()).isEqualTo(1);
        assertThat(first.interest()).isEqualTo(bd("100.00"));
        assertThat(first.principal()).isEqualTo(bd("788.49"));
        assertThat(first.payment()).isEqualTo(bd("888.49"));
        assertThat(first.remainingBalance()).isEqualTo(bd("9211.51"));
        assertConsistent(plan, bd("10000"));
    }

    @Test
    void calculate_anyPlan_lastRowEndsAtExactlyZero() {
        // when
        PaymentPlan plan = PaymentPlanCalculator.calculate(bd("12345.67"), bd("7.35"),
                LocalDate.of(2026, 3, 10), LocalDate.of(2029, 3, 10));

        // then
        PaymentPlan.Row last = plan.rows().get(plan.rows().size() - 1);
        assertThat(last.remainingBalance()).isEqualTo(bd("0.00"));
        assertThat(plan.rows()).hasSize(36);
        assertConsistent(plan, bd("12345.67"));
    }

    @Test
    void calculate_zeroRate_splitsPrincipalEvenlyAndLastRowAbsorbsRemainder() {
        // when
        PaymentPlan plan = PaymentPlanCalculator.calculate(bd("1000"), bd("0"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1));

        // then
        assertThat(plan.monthlyPayment()).isEqualTo(bd("333.33"));
        assertThat(plan.rows()).extracting(PaymentPlan.Row::payment)
                .containsExactly(bd("333.33"), bd("333.33"), bd("333.34"));
        assertThat(plan.rows()).allSatisfy(r -> assertThat(r.interest()).isEqualByComparingTo("0"));
        assertThat(plan.totalInterest()).isEqualByComparingTo("0");
        assertThat(plan.totalPaid()).isEqualByComparingTo("1000.00");
        assertConsistent(plan, bd("1000"));
    }

    @Test
    void calculate_oneMonthTerm_repaysEverythingInOneRow() {
        // when
        PaymentPlan plan = PaymentPlanCalculator.calculate(bd("1000"), bd("12"),
                LocalDate.of(2026, 5, 20), LocalDate.of(2026, 6, 20));

        // then
        assertThat(plan.termMonths()).isEqualTo(1);
        assertThat(plan.rows()).hasSize(1);
        PaymentPlan.Row row = plan.rows().get(0);
        assertThat(row.interest()).isEqualTo(bd("10.00"));
        assertThat(row.principal()).isEqualTo(bd("1000.00"));
        assertThat(row.payment()).isEqualTo(bd("1010.00"));
        assertThat(row.remainingBalance()).isEqualTo(bd("0.00"));
        assertThat(row.date()).isEqualTo(LocalDate.of(2026, 6, 20));
    }

    @Test
    void calculate_480MonthTerm_producesFullPlanThatEndsAtZero() {
        // when
        PaymentPlan plan = PaymentPlanCalculator.calculate(bd("100000"), bd("6"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2066, 1, 1));

        // then: the classic 40-year payment of 100000 at 6% is 549.? -> verify by formula below
        assertThat(plan.termMonths()).isEqualTo(480);
        assertThat(plan.rows()).hasSize(480);
        double r = 0.005;
        double expected = 100000 * r / (1 - Math.pow(1 + r, -480));
        assertThat(plan.monthlyPayment().doubleValue()).isCloseTo(expected, org.assertj.core.data.Offset.offset(0.006));
        assertThat(plan.rows().get(479).remainingBalance()).isEqualTo(bd("0.00"));
        assertThat(plan.rows().get(479).date()).isEqualTo(LocalDate.of(2066, 1, 1));
        assertConsistent(plan, bd("100000"));
    }

    @Test
    void calculate_tinyAmountAndHighRate_staysConsistent() {
        // when
        PaymentPlan plan = PaymentPlanCalculator.calculate(bd("0.01"), bd("100"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 7, 1));

        // then
        assertThat(plan.rows()).hasSize(6);
        assertThat(plan.rows().get(5).remainingBalance()).isEqualTo(bd("0.00"));
    }

    @Test
    void calculate_startOnThe31st_clampsDatesToMonthEndInNonLeapYear() {
        // when
        PaymentPlan plan = PaymentPlanCalculator.calculate(bd("1000"), bd("5"),
                LocalDate.of(2023, 1, 31), LocalDate.of(2023, 5, 31));

        // then
        assertThat(plan.rows()).extracting(PaymentPlan.Row::date).containsExactly(
                LocalDate.of(2023, 2, 28), LocalDate.of(2023, 3, 31),
                LocalDate.of(2023, 4, 30), LocalDate.of(2023, 5, 31));
    }

    @Test
    void calculate_startOnThe31st_clampsFebruaryTo29InLeapYear() {
        // when
        PaymentPlan plan = PaymentPlanCalculator.calculate(bd("1000"), bd("5"),
                LocalDate.of(2024, 1, 31), LocalDate.of(2024, 4, 30));

        // then
        assertThat(plan.rows()).extracting(PaymentPlan.Row::date).containsExactly(
                LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 31), LocalDate.of(2024, 4, 30));
    }

    @Test
    void termMonths_endIsClampedMonthEnd_isAccepted() {
        // when / then: 31 Jan + 1 month = 28 Feb (non-leap) / 29 Feb (leap)
        assertThat(PaymentPlanCalculator.termMonths(LocalDate.of(2023, 1, 31), LocalDate.of(2023, 2, 28))).isEqualTo(1);
        assertThat(PaymentPlanCalculator.termMonths(LocalDate.of(2024, 1, 31), LocalDate.of(2024, 2, 29))).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-01-15, 2026-02-16",
            "2026-01-15, 2026-02-14",
            "2026-01-15, 2026-01-15",
            "2026-01-15, 2026-01-10",
            "2024-01-31, 2024-02-28"
    })
    void calculate_endNotWholeMonthsAfterStart_throwsInvalidRequest(LocalDate start, LocalDate end) {
        // when / then
        assertThatThrownBy(() -> PaymentPlanCalculator.calculate(bd("1000"), bd("5"), start, end))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("whole number of months");
    }

    @Test
    void calculate_pastStartDate_isAllowed() {
        // when
        PaymentPlan plan = PaymentPlanCalculator.calculate(bd("1000"), bd("5"),
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 7, 1));

        // then
        assertThat(plan.rows()).hasSize(6);
    }

    @Test
    void calculate_rows_areImmutable() {
        // given
        PaymentPlan plan = PaymentPlanCalculator.calculate(bd("1000"), bd("5"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1));

        // when / then
        List<PaymentPlan.Row> rows = plan.rows();
        assertThatThrownBy(() -> rows.add(rows.get(0))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void termMonths_hugeSpan_returnsMinusOne() {
        assertThat(PaymentPlanCalculator.termMonths(LocalDate.of(1900, 1, 1), LocalDate.of(2200, 12, 31))).isEqualTo(-1);
        assertThat(PaymentPlanCalculator.termMonths(LocalDate.MIN, LocalDate.MAX)).isEqualTo(-1);
    }

    @Test
    void termMonths_nearLocalDateExtremes_returnsMinusOneWithoutThrowing() {
        assertThat(PaymentPlanCalculator.termMonths(LocalDate.MAX.minusDays(40), LocalDate.MAX)).isEqualTo(-1);
        assertThat(PaymentPlanCalculator.termMonths(LocalDate.MIN, LocalDate.MIN.plusDays(40))).isEqualTo(-1);
        assertThat(PaymentPlanCalculator.termMonths(LocalDate.MIN, LocalDate.MIN.plusMonths(1))).isEqualTo(1);
        assertThat(PaymentPlanCalculator.termMonths(LocalDate.of(1, 1, 1), LocalDate.of(999999, 1, 1))).isEqualTo(11_999_976);
    }

    @Test
    void calculate_hugeSpan_throwsInvalidRequestInsteadOfOverflowing() {
        assertThatThrownBy(() -> PaymentPlanCalculator.calculate(bd("1000"), bd("5"), LocalDate.MIN, LocalDate.MAX))
                .isInstanceOf(InvalidRequestException.class);
    }
}
