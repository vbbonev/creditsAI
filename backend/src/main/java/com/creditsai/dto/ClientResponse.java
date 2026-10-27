package com.creditsai.dto;

import java.time.Instant;

/**
 * DTO pattern: a client as returned by the API.
 *
 * @param id the client id
 * @param name the client name
 * @param identifier free-text identifier of the person or company
 * @param address the postal address
 * @param description optional free-text note, may be null
 * @param archived whether the client is archived
 * @param archivedAt when the client was archived, null if active
 * @param createdAt when the client was created
 * @param updatedAt when the client was last changed
 */
public record ClientResponse(
        Long id,
        String name,
        String identifier,
        String address,
        String description,
        boolean archived,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt) {
}
