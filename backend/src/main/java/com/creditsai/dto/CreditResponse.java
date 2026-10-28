package com.creditsai.dto;

import com.creditsai.model.CreditCurrency;
import com.creditsai.model.CreditStatus;
import com.creditsai.model.CreditType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO pattern: a credit as returned by the API, with its full payment plan.
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
 * @param hasCollateral whether the credit is secured by collateral
 * @param collateralValue the collateral value in the credit's currency, null without collateral
 * @param collateralDescription the collateral description, null without collateral
 * @param status ACTIVE or INACTIVE
 * @param archived whether the credit is archived
 * @param archivedAt when the credit was archived, null if not archived
 * @param createdAt when the credit was created
 * @param updatedAt when the credit was last changed
 * @param plan the payment plan
 */
public record CreditResponse(
        Long id,
        String creditNumber,
        ClientSummary client,
        CreditType type,
        BigDecimal amount,
        CreditCurrency currency,
        BigDecimal interestRate,
        LocalDate startDate,
        LocalDate endDate,
        boolean hasCollateral,
        BigDecimal collateralValue,
        String collateralDescription,
        CreditStatus status,
        boolean archived,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt,
        PaymentPlanResponse plan) {
}
