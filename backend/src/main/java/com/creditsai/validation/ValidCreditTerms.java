package com.creditsai.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Checks the configurable limits of {@code app.credits} on a {@link com.creditsai.dto.CreditTerms}:
 * amount, rate, end after start and a term of whole months within the allowed range. Violations
 * are reported on the field concerned.
 */
@Documented
@Constraint(validatedBy = CreditTermsValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCreditTerms {

    /**
     * Returns the default message.
     *
     * @return the message
     */
    String message() default "The credit terms are invalid.";

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
