package com.creditsai.mapper;

import com.creditsai.dto.AttachmentResponse;
import com.creditsai.model.ClientAttachment;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Mapper pattern (MapStruct) from {@link ClientAttachment} to its response DTO.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AttachmentMapper {

    /**
     * Converts an attachment entity to its response DTO.
     *
     * @param attachment the entity
     * @return the response
     */
    AttachmentResponse toResponse(ClientAttachment attachment);
}
