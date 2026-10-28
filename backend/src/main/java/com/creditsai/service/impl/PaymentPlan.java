package com.creditsai.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The result of {@link PaymentPlanCalculator}: an annuity payment plan.
 *
 * @param termMonths the number of instalments
 * @param monthlyPayment the regular instalment (the last row may differ by a few cents)
 * @param totalInterest the sum of the interest of all rows
 * @param totalPaid the sum of all instalments
 * @param rows the instalments in order
 */
public record PaymentPlan(
        int termMonths,
        BigDecimal monthlyPayment,
        BigDecimal totalInterest,
        BigDecimal totalPaid,
        List<Row> rows) {

    /**
     * One instalment of the plan.
     *
     * @param number the position, starting at 1
     * @param date the due date
     * @param payment the whole instalment
     * @param principal the part that repays the principal
     * @param interest the interest part
     * @param remainingBalance the principal still owed after this instalment
     */
    public record Row(
            int number,
            LocalDate date,
            BigDecimal payment,
            BigDecimal principal,
            BigDecimal interest,
            BigDecimal remainingBalance) {
    }
}
