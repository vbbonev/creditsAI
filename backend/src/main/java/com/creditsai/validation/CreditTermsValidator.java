package com.creditsai.validation;

import com.creditsai.config.CreditProperties;
import com.creditsai.dto.CreditTerms;
import com.creditsai.service.impl.PaymentPlanCalculator;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

/**
 * Validates {@link CreditTerms} against {@link CreditProperties}. Missing values are left to the
 * {@code @NotNull} annotations on the fields.
 */
@RequiredArgsConstructor
public class CreditTermsValidator implements ConstraintValidator<ValidCreditTerms, CreditTerms> {

    private final CreditProperties properties;

    /** {@inheritDoc} */
    @Override
    public boolean isValid(CreditTerms terms, ConstraintValidatorContext context) {
        context.disableDefaultConstraintViolation();
        boolean valid = true;
        if (terms.amount() != null && terms.amount().compareTo(properties.maxAmount()) > 0) {
            valid = fail(context, "amount", "Amount must be at most " + properties.maxAmount().toPlainString() + ".");
        }
        if (terms.interestRate() != null && (terms.interestRate().signum() < 0
                || terms.interestRate().compareTo(properties.maxInterestRate()) > 0)) {
            valid = fail(context, "interestRate",
                    "Interest rate must be between 0 and " + properties.maxInterestRate().toPlainString() + " percent.");
        }
        boolean datesInBounds = true;
        if (terms.startDate() != null && terms.startDate().isBefore(properties.minStartDate())) {
            datesInBounds = false;
            valid = fail(context, "startDate", "Start date must be on or after " + properties.minStartDate() + ".");
        }
        if (terms.endDate() != null && terms.endDate().isAfter(properties.maxEndDate())) {
            datesInBounds = false;
            valid = fail(context, "endDate", "End date must be on or before " + properties.maxEndDate() + ".");
        }
        if (terms.startDate() != null && terms.endDate() != null && datesInBounds) {
            if (!terms.endDate().isAfter(terms.startDate())) {
                valid = fail(context, "endDate", "End date must be after the start date.");
            } else {
                int months = PaymentPlanCalculator.termMonths(terms.startDate(), terms.endDate());
                if (months < 1) {
                    valid = fail(context, "endDate",
                            "End date must be a whole number of months after the start date, on the same day of the month.");
                } else if (months < properties.minTermMonths() || months > properties.maxTermMonths()) {
                    valid = fail(context, "endDate", "The term must be between " + properties.minTermMonths()
                            + " and " + properties.maxTermMonths() + " months.");
                }
            }
        }
        return valid;
    }

    /**
     * Adds a violation on one field.
     *
     * @param context the validation context
     * @param field the request field the message belongs to
     * @param message the user-readable message
     * @return always {@code false}, so callers can assign it to the validity flag
     */
    private static boolean fail(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message.replace("{", "\\{")).addPropertyNode(field)
                .addConstraintViolation();
        return false;
    }
}
