package com.creditsai.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Checks a {@link com.creditsai.dto.CreditCollateral}: value and description are required with
 * collateral and must be absent without it. Violations are reported on the field concerned.
 */
@Documented
@Constraint(validatedBy = CollateralValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCollateral {

    /**
     * Returns the default message.
     *
     * @return the message
     */
    String message() default "The collateral is invalid.";

    /**
     * Returns the validation groups.
     *
     * @return the groups
     */
    Class<?>[] groups() default {};

    /**
     * Returns the payload.
     *
     * @return the payload
     */
    Class<? extends Payload>[] payload() default {};
}
