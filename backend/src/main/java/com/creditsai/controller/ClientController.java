package com.creditsai.controller;

import com.creditsai.dto.ClientRequest;
import com.creditsai.dto.ClientResponse;
import com.creditsai.exception.InvalidRequestException;
import com.creditsai.service.ClientService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST controller for clients under {@code /api/clients}. It sees only the service interface and
 * DTOs (layered architecture) and decides what the caller's role may see.
 */
@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientController {

    /** Fields the client list may be sorted by (the {@code sort} request parameter). */
    static final List<String> SORTABLE_FIELDS = List.of("name", "identifier", "createdAt", "updatedAt", "archivedAt");

    private final ClientService clientService;

    /**
     * Creates a client.
     *
     * @param request the validated client data
     * @return 201 with a {@code Location} header and the created client
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody ClientRequest request) {
        ClientResponse created = clientService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Lists clients, optionally filtered by a search text; archived clients are included for ADMIN only.
     *
     * @param search text to find in the name or identifier (case-insensitive); blank or missing lists all
     * @param includeArchived {@code true} to list archived clients together with active ones (ADMIN only),
     *        default {@code false}
     * @param pageable page, size (default 20, at most 100) and sort (default by name)
     * @return the requested page
     * @throws InvalidRequestException if sorting by a field outside {@link #SORTABLE_FIELDS}
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or (hasRole('USER') and !#includeArchived)")
    public Page<ClientResponse> list(
            @RequestParam(name = "search", required = false) String search,
            @P("includeArchived") @RequestParam(name = "includeArchived", defaultValue = "false") boolean includeArchived,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        checkSortable(pageable.getSort());
        return clientService.list(search, includeArchived, pageable);
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
     * Returns one client; an archived client is visible to ADMIN only (404 for USER).
     *
     * @param id the client id
     * @param authentication the caller, used to decide whether archived clients may be seen
     * @return the client
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ClientResponse get(@PathVariable Long id, Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return clientService.get(id, isAdmin);
    }

    /**
     * Replaces all editable fields of a client.
     *
     * @param id the client id
     * @param request the validated client data
     * @return the updated client
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ClientResponse update(@PathVariable Long id, @Valid @RequestBody ClientRequest request) {
        return clientService.update(id, request);
    }

    /**
     * Archives a client ("delete"; nothing is physically removed).
     *
     * @param id the client id
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> archive(@PathVariable Long id) {
        clientService.archive(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Restores an archived client.
     *
     * @param id the client id
     * @return the restored client
     */
    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ClientResponse restore(@PathVariable Long id) {
        return clientService.restore(id);
    }
}
