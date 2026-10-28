package com.creditsai.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO pattern: one instalment of a payment plan.
 *
 * @param number the position in the plan, starting at 1
 * @param date the due date
 * @param payment the whole instalment
 * @param principal the part that repays the principal
 * @param interest the interest part
 * @param remainingBalance the principal still owed after this instalment
 */
public record PaymentRowResponse(
        int number,
        LocalDate date,
        BigDecimal payment,
        BigDecimal principal,
        BigDecimal interest,
        BigDecimal remainingBalance) {
}
