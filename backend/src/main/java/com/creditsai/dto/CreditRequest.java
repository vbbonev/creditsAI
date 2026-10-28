package com.creditsai.dto;

import com.creditsai.model.CreditCurrency;
import com.creditsai.model.CreditType;
import com.creditsai.validation.ValidCollateral;
import com.creditsai.validation.ValidCreditTerms;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO pattern: request body for creating and updating a credit (PUT replaces all fields). The
 * payment plan is never sent: the server always calculates it from these inputs.
 *
 * @param clientId the client who owns the credit; must exist and not be archived
 * @param type the kind of credit
 * @param amount the principal
 * @param currency the currency of the amount
 * @param interestRate the fixed annual nominal rate in percent, e.g. 5.25
 * @param startDate the start date, may be in the past
 * @param endDate the end date, a whole number of months after the start date
 * @param hasCollateral whether the credit is secured by collateral
 * @param collateralValue the collateral value in the credit's currency; required with collateral, empty without
 * @param collateralDescription the collateral description; required with collateral, empty without
 */
@ValidCreditTerms
@ValidCollateral
public record CreditRequest(
        @NotNull(message = "Client is required.")
        Long clientId,

        @NotNull(message = "Type is required.")
        CreditType type,

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
        LocalDate endDate,

        @NotNull(message = "Collateral flag is required.")
        Boolean hasCollateral,

        @Positive(message = "Collateral value must be greater than zero.")
        @Digits(integer = 10, fraction = 2, message = "Collateral value may have at most 10 digits and 2 decimals.")
        BigDecimal collateralValue,

        @Size(max = 500, message = "Collateral description must be at most 500 characters.")
        String collateralDescription) implements CreditTerms, CreditCollateral {
}
