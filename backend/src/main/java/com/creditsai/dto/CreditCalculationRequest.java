package com.creditsai.dto;

import com.creditsai.model.CreditCurrency;
import com.creditsai.validation.ValidCreditTerms;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO pattern: request body of the stateless payment plan preview. Nothing is saved.
 *
 * @param amount the principal
 * @param currency the currency of the amount
 * @param interestRate the fixed annual nominal rate in percent, e.g. 5.25
 * @param startDate the start date, may be in the past
 * @param endDate the end date, a whole number of months after the start date
 */
@ValidCreditTerms
public record CreditCalculationRequest(
        @NotNull(message = "Amount is required.")
        @Positive(message = "Amount must be greater than zero.")
        @Digits(integer = 10, fraction = 2, message = "Amount may have at most 10 digits and 2 decimals.")
        BigDecimal amount,

        @NotNull(message = "Currency is required.")
        CreditCurrency currency,

        @NotNull(message = "Interest rate is required.")
        @Digits(integer = 3, fraction = 4, message = "Interest rate may have at most 4 decimals.")
        BigDecimal interestRate,

        @NotNull(message = "Start date is required.")
        LocalDate startDate,

        @NotNull(message = "End date is required.")
        LocalDate endDate) implements CreditTerms {
}
