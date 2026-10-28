package com.creditsai.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limits for credit input, bound from {@code app.credits.*}.
 *
 * @param maxAmount the largest allowed principal
 * @param maxInterestRate the largest allowed annual interest rate in percent (the smallest is 0)
 * @param minTermMonths the shortest allowed term in months
 * @param maxTermMonths the longest allowed term in months
 * @param minStartDate the earliest allowed start date
 * @param maxEndDate the latest allowed end date
 */
@ConfigurationProperties("app.credits")
public record CreditProperties(
        @DefaultValue("100000000") BigDecimal maxAmount,
        @DefaultValue("100") BigDecimal maxInterestRate,
        @DefaultValue("1") int minTermMonths,
        @DefaultValue("480") int maxTermMonths,
        @DefaultValue("1900-01-01") LocalDate minStartDate,
        @DefaultValue("2200-12-31") LocalDate maxEndDate) {
}
