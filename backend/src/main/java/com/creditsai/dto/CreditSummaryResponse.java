package com.creditsai.dto;

import com.creditsai.model.CreditCurrency;
import com.creditsai.model.CreditStatus;
import com.creditsai.model.CreditType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO pattern: a credit as listed, without the payment rows.
 *
 * @param id the credit id
 * @param creditNumber the generated number, e.g. CR-2026-000123
 * @param client the owning client
 * @param type the kind of credit
 * @param amount the principal
 * @param currency the currency of the amount
 * @param interestRate the fixed annual nominal rate in percent
 * @param startDate the start date
 * @param endDate the end date
 * @param termMonths the number of instalments
 * @param monthlyPayment the regular instalment
 * @param status ACTIVE or INACTIVE
 * @param archived whether the credit is archived
 * @param createdAt when the credit was created
 */
public record CreditSummaryResponse(
        Long id,
        String creditNumber,
        ClientSummary client,
        CreditType type,
        BigDecimal amount,
        CreditCurrency currency,
        BigDecimal interestRate,
        LocalDate startDate,
        LocalDate endDate,
        int termMonths,
        BigDecimal monthlyPayment,
        CreditStatus status,
        boolean archived,
        Instant createdAt) {
}
