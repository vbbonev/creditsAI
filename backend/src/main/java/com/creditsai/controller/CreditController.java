package com.creditsai.controller;

import com.creditsai.dto.AttachmentUpload;
import com.creditsai.dto.CreditCalculationRequest;
import com.creditsai.dto.CreditRequest;
import com.creditsai.dto.CreditResponse;
import com.creditsai.dto.CreditStatusRequest;
import com.creditsai.dto.CreditSummaryResponse;
import com.creditsai.dto.PaymentPlanResponse;
import com.creditsai.exception.InvalidRequestException;
import com.creditsai.model.CreditStatus;
import com.creditsai.service.CreditService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST controller for credits under {@code /api/credits}. It sees only the service interface and
 * DTOs (layered architecture) and decides what the caller's role may see.
 */
@RestController
@RequestMapping("/api/credits")
@RequiredArgsConstructor
public class CreditController {

    /** Fields the credit list may be sorted by (the {@code sort} request parameter). */
    static final List<String> SORTABLE_FIELDS = List.of("creditNumber", "startDate", "endDate", "amount", "createdAt");

    private final CreditService creditService;

    /**
     * Calculates a payment plan without saving anything.
     *
     * @param request the validated inputs
     * @return the plan
     */
    @PostMapping("/calculate")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public PaymentPlanResponse calculate(@Valid @RequestBody CreditCalculationRequest request) {
        return creditService.calculate(request);
    }

    /**
     * Creates a credit; the server calculates the plan.
     *
     * @param request the validated credit data
     * @return 201 with a {@code Location} header and the created credit
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<CreditResponse> create(@Valid @RequestBody CreditRequest request) {
        CreditResponse created = creditService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Creates a credit together with its files in one request; all or nothing. This is the only
     * way a USER can attach files to a credit.
     *
     * @param request the validated credit data (multipart part {@code credit}, JSON)
     * @param files the uploaded files (zero or more parts named {@code files})
     * @param authentication the caller, recorded as uploader of the files
     * @return 201 with a {@code Location} header and the created credit
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<CreditResponse> createWithFiles(
            @Valid @RequestPart("credit") CreditRequest request,
            @RequestPart(name = "files", required = false) List<MultipartFile> files,
            Authentication authentication) {
        List<AttachmentUpload> uploads = files == null ? List.of()
                : files.stream().map(ClientAuthentication::toUpload).toList();
        CreditResponse created = creditService.create(request, uploads, ClientAuthentication.username(authentication));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Lists credits of all clients, optionally filtered; archived credits are included for ADMIN only.
     *
     * @param search text to find in the credit number, client name or client identifier
     *        (case-insensitive); blank or missing lists all
     * @param status keep only credits with this status; missing lists both
     * @param includeArchived {@code true} to list archived credits together with active ones (ADMIN only),
     *        default {@code false}
     * @param pageable page, size (default 20, at most 100) and sort (default by creditNumber)
     * @return the requested page
     * @throws InvalidRequestException if sorting by a field outside {@link #SORTABLE_FIELDS}
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or (hasRole('USER') and !#includeArchived)")
    public Page<CreditSummaryResponse> list(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) CreditStatus status,
            @P("includeArchived") @RequestParam(name = "includeArchived", defaultValue = "false") boolean includeArchived,
            @PageableDefault(size = 20, sort = "creditNumber") Pageable pageable) {
        checkSortable(pageable.getSort());
        return creditService.list(search, status, includeArchived, pageable);
    }

    /**
     * Rejects sorting by any field outside {@link #SORTABLE_FIELDS}, so callers get a clear 400
     * instead of a server error, and can't sort by fields that aren't meant to be exposed.
     *
     * @param sort the requested sort
     * @throws InvalidRequestException if a sort field is not allowed
     */
    private void checkSortable(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new InvalidRequestException("Sorting by '" + order.getProperty()
                        + "' is not supported. Allowed fields: " + String.join(", ", SORTABLE_FIELDS) + ".");
            }
        }
    }

    /**
     * Returns one credit with its plan; an archived credit is visible to ADMIN only (404 for USER).
     *
     * @param id the credit id
     * @param authentication the caller, used to decide whether archived credits may be seen
     * @return the credit
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public CreditResponse get(@PathVariable Long id, Authentication authentication) {
        return creditService.get(id, ClientAuthentication.isAdmin(authentication));
    }

    /**
     * Replaces all editable fields of a credit and recalculates its plan.
     *
     * @param id the credit id
     * @param request the validated credit data
     * @return the updated credit
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CreditResponse update(@PathVariable Long id, @Valid @RequestBody CreditRequest request) {
        return creditService.update(id, request);
    }

    /**
     * Changes the status of a credit.
     *
     * @param id the credit id
     * @param request the validated new status
     * @return the updated credit
     */
    @PostMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public CreditResponse changeStatus(@PathVariable Long id, @Valid @RequestBody CreditStatusRequest request) {
        return creditService.changeStatus(id, request.status());
    }

    /**
     * Archives a credit ("delete"; nothing is physically removed).
     *
     * @param id the credit id
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> archive(@PathVariable Long id) {
        creditService.archive(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Restores an archived credit; it stays inactive.
     *
     * @param id the credit id
     * @return the restored credit
     */
    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public CreditResponse restore(@PathVariable Long id) {
        return creditService.restore(id);
    }
}
