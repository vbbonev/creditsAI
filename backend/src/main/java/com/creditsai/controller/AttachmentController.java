package com.creditsai.controller;

import com.creditsai.dto.AttachmentResponse;
import com.creditsai.dto.AttachmentUpload;
import com.creditsai.service.AttachmentService;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST controller for the files attached to a client under {@code /api/clients/{id}/attachments}.
 * Everybody who is logged in can list and download; only ADMIN can add and remove files of an
 * existing client (USER can attach files only while creating a client, see
 * {@link ClientController}). Archived clients are visible to ADMIN only.
 */
@RestController
@RequestMapping("/api/clients/{clientId}/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    /**
     * Lists the attachments of a client sorted by file name.
     *
     * @param clientId the client id
     * @param authentication the caller, used to decide whether an archived client may be seen
     * @return the attachments
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<AttachmentResponse> list(@PathVariable Long clientId, Authentication authentication) {
        return attachmentService.list(clientId, ClientAuthentication.isAdmin(authentication));
    }

    /**
     * Downloads the content of one attachment as a file, with headers that stop browsers from
     * interpreting it (attachment disposition, {@code nosniff}, no caching).
     *
     * @param clientId the client id
     * @param attachmentId the attachment id
     * @param authentication the caller, used to decide whether an archived client may be seen
     * @return the streamed file
     */
    @GetMapping("/{attachmentId}/content")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Resource> download(@PathVariable Long clientId, @PathVariable Long attachmentId,
                                             Authentication authentication) {
        return ClientAuthentication.toDownloadResponse(attachmentService.download(clientId, attachmentId,
                ClientAuthentication.isAdmin(authentication)));
    }

    /**
     * Adds files to an existing client.
     *
     * @param clientId the client id
     * @param files the uploaded files (multipart part {@code files})
     * @param authentication the caller, recorded as uploader
     * @return 201 with the created attachments
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AttachmentResponse>> add(@PathVariable Long clientId,
                                                        @RequestPart("files") List<MultipartFile> files,
                                                        Authentication authentication) {
        List<AttachmentUpload> uploads = files.stream().map(ClientAuthentication::toUpload).toList();
        List<AttachmentResponse> created = attachmentService.add(clientId, uploads,
                ClientAuthentication.username(authentication));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().build().toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Removes an attachment (row and file) for good.
     *
     * @param clientId the client id
     * @param attachmentId the attachment id
     * @return 204 No Content
     */
    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long clientId, @PathVariable Long attachmentId) {
        attachmentService.delete(clientId, attachmentId);
        return ResponseEntity.noContent().build();
    }
}
