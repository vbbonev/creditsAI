package com.creditsai.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The inputs that determine a payment plan; implemented by the requests that carry them so one
 * validator can check the limits for both.
 */
public interface CreditTerms {

    /**
     * Returns the principal.
     *
     * @return the amount, may be null in an invalid request
     */
    BigDecimal amount();

    /**
     * Returns the annual nominal rate.
     *
     * @return the rate in percent, may be null in an invalid request
     */
    BigDecimal interestRate();

    /**
     * Returns the start date.
     *
     * @return the start date, may be null in an invalid request
     */
    LocalDate startDate();

    /**
     * Returns the end date.
     *
     * @return the end date, may be null in an invalid request
     */
    LocalDate endDate();
}
