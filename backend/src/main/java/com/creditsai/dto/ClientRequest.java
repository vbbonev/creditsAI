package com.creditsai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO pattern: request body for creating and updating a client (PUT replaces all fields).
 * Values are stored exactly as sent (no trimming).
 *
 * @param name the client name
 * @param identifier free-text identifier of the person or company
 * @param address the postal address
 * @param description optional free-text note
 */
public record ClientRequest(
        @NotBlank(message = "Name is required.")
        @Size(max = 100, message = "Name must be at most 100 characters.")
        String name,

        @NotBlank(message = "Identifier is required.")
        @Size(max = 50, message = "Identifier must be at most 50 characters.")
        String identifier,

        @NotBlank(message = "Address is required.")
        @Size(max = 200, message = "Address must be at most 200 characters.")
        String address,

        @Size(max = 500, message = "Description must be at most 500 characters.")
        String description) {
}
