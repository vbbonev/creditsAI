package com.creditsai.dto;

import com.creditsai.model.CreditStatus;
import jakarta.validation.constraints.NotNull;

/**
 * DTO pattern: request body for changing a credit's status.
 *
 * @param status the new status
 */
public record CreditStatusRequest(
        @NotNull(message = "Status is required.")
        CreditStatus status) {
}
