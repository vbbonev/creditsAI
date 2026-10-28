package com.creditsai.mapper;

import com.creditsai.dto.ClientRequest;
import com.creditsai.dto.ClientResponse;
import com.creditsai.model.Client;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper pattern (MapStruct) between {@link Client} and its DTOs. The server-set fields
 * (id, archived, archivedAt, createdAt, updatedAt) have no setters on the entity, so MapStruct
 * never treats them as targets and they need no {@code ignore}. The attachments are changed only
 * through the entity's methods, so they are ignored explicitly.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ClientMapper {

    /**
     * Converts an entity to its response DTO.
     *
     * @param client the entity
     * @return the response
     */
    ClientResponse toResponse(Client client);

    /**
     * Creates a new entity from a request.
     *
     * @param request the validated request
     * @return a new, unsaved client
     */
    @Mapping(target = "attachments", ignore = true)
    Client toEntity(ClientRequest request);

    /**
     * Copies the editable fields of a request onto an existing entity.
     *
     * @param request the validated request
     * @param client the entity to change
     */
    @Mapping(target = "attachments", ignore = true)
    void updateEntity(ClientRequest request, @MappingTarget Client client);
}
