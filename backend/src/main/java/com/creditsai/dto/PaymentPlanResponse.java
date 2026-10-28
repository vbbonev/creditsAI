package com.creditsai.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO pattern: a payment plan as returned by the API.
 *
 * @param termMonths the number of instalments
 * @param monthlyPayment the regular instalment (the last row may differ by a few cents)
 * @param totalInterest the sum of the interest of all rows
 * @param totalPaid the sum of all instalments
 * @param rows the instalments in order
 */
public record PaymentPlanResponse(
        int termMonths,
        BigDecimal monthlyPayment,
        BigDecimal totalInterest,
        BigDecimal totalPaid,
        List<PaymentRowResponse> rows) {
}
