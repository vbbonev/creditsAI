package com.creditsai.dto;

/**
 * DTO pattern: a generated payment plan document ready to be sent to the caller.
 *
 * @param fileName the file name to offer to the user, e.g. CR-2026-000123-payment-plan.pdf
 * @param content the PDF bytes
 */
public record PaymentPlanPdf(String fileName, byte[] content) {
}
