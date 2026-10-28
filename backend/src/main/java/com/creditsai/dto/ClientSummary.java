package com.creditsai.dto;

/**
 * DTO pattern: the few client fields shown inside a credit.
 *
 * @param id the client id
 * @param name the client name
 * @param identifier free-text identifier of the person or company
 */
public record ClientSummary(Long id, String name, String identifier) {
}
