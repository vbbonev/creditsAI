package com.creditsai.controller;

import com.creditsai.dto.AttachmentResponse;
import com.creditsai.dto.AttachmentUpload;
import com.creditsai.service.CreditAttachmentService;
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
 * REST controller for the files attached to a credit under {@code /api/credits/{id}/attachments}.
 * Everybody who is logged in can list and download; only ADMIN can add and remove files of an
 * existing credit (USER can attach files only while creating a credit, see
 * {@link CreditController}). Archived credits are visible to ADMIN only.
 */
@RestController
@RequestMapping("/api/credits/{creditId}/attachments")
@RequiredArgsConstructor
public class CreditAttachmentController {

    private final CreditAttachmentService creditAttachmentService;

    /**
     * Lists the attachments of a credit sorted by file name.
     *
     * @param creditId the credit id
     * @param authentication the caller, used to decide whether an archived credit may be seen
     * @return the attachments
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public List<AttachmentResponse> list(@PathVariable Long creditId, Authentication authentication) {
        return creditAttachmentService.list(creditId, ClientAuthentication.isAdmin(authentication));
    }

    /**
     * Downloads the content of one attachment as a file, with headers that stop browsers from
     * interpreting it (attachment disposition, {@code nosniff}, no caching).
     *
     * @param creditId the credit id
     * @param attachmentId the attachment id
     * @param authentication the caller, used to decide whether an archived credit may be seen
     * @return the streamed file
     */
    @GetMapping("/{attachmentId}/content")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Resource> download(@PathVariable Long creditId, @PathVariable Long attachmentId,
                                             Authentication authentication) {
        return ClientAuthentication.toDownloadResponse(creditAttachmentService.download(creditId, attachmentId,
                ClientAuthentication.isAdmin(authentication)));
    }

    /**
     * Adds files to an existing credit.
     *
     * @param creditId the credit id
     * @param files the uploaded files (multipart part {@code files})
     * @param authentication the caller, recorded as uploader
     * @return 201 with the created attachments
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AttachmentResponse>> add(@PathVariable Long creditId,
                                                        @RequestPart("files") List<MultipartFile> files,
                                                        Authentication authentication) {
        List<AttachmentUpload> uploads = files.stream().map(ClientAuthentication::toUpload).toList();
        List<AttachmentResponse> created = creditAttachmentService.add(creditId, uploads,
                ClientAuthentication.username(authentication));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().build().toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Removes an attachment (row and file) for good.
     *
     * @param creditId the credit id
     * @param attachmentId the attachment id
     * @return 204 No Content
     */
    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long creditId, @PathVariable Long attachmentId) {
        creditAttachmentService.delete(creditId, attachmentId);
        return ResponseEntity.noContent().build();
    }
}
