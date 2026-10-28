package com.creditsai.dto;

import java.math.BigDecimal;

/** The collateral inputs of a credit request, so one validator can check their consistency. */
public interface CreditCollateral {

    /**
     * Tells whether the credit is secured by collateral.
     *
     * @return the flag, may be null in an invalid request
     */
    Boolean hasCollateral();

    /**
     * Returns the collateral value.
     *
     * @return the value in the credit's currency, may be null
     */
    BigDecimal collateralValue();

    /**
     * Returns the collateral description.
     *
     * @return the description, may be null
     */
    String collateralDescription();
}
