package com.creditsai.validation;

import com.creditsai.dto.CreditCollateral;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Validates that the collateral fields of a {@link CreditCollateral} agree with its flag. */
public class CollateralValidator implements ConstraintValidator<ValidCollateral, CreditCollateral> {

    /** {@inheritDoc} */
    @Override
    public boolean isValid(CreditCollateral collateral, ConstraintValidatorContext context) {
        if (collateral.hasCollateral() == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        boolean valid = true;
        if (collateral.hasCollateral()) {
            if (collateral.collateralValue() == null) {
                valid = fail(context, "collateralValue", "Collateral value is required when the credit has collateral.");
            }
            if (collateral.collateralDescription() == null || collateral.collateralDescription().isBlank()) {
                valid = fail(context, "collateralDescription",
                        "Collateral description is required when the credit has collateral.");
            }
        } else {
            if (collateral.collateralValue() != null) {
                valid = fail(context, "collateralValue", "Collateral value must be empty when the credit has no collateral.");
            }
            if (collateral.collateralDescription() != null) {
                valid = fail(context, "collateralDescription",
                        "Collateral description must be empty when the credit has no collateral.");
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
        context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation();
        return false;
    }
}
