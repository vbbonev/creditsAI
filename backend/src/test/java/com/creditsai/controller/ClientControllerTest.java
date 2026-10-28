package com.creditsai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.creditsai.dto.ClientRequest;
import com.creditsai.dto.ClientResponse;
import com.creditsai.exception.BusinessRuleException;
import com.creditsai.exception.ResourceNotFoundException;
import com.creditsai.security.SecurityConfig;
import com.creditsai.service.ClientService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ClientController.class)
@Import(SecurityConfig.class)
class ClientControllerTest {

    private static final String VALID_BODY =
            "{\"name\":\"Acme\",\"identifier\":\"123\",\"address\":\"Sofia\",\"description\":\"note\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClientService clientService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static RequestPostProcessor user() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static ClientResponse response(long id, boolean archived) {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        return new ClientResponse(id, "Acme", "123", "Sofia", "note", archived,
                archived ? now : null, now, now);
    }

    // ---- 401 ----

    @Test
    void endpoints_withoutToken_return401() throws Exception {
        // when / then
        mockMvc.perform(get("/api/clients")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/clients/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/clients").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/clients/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/clients/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/clients/1/restore")).andExpect(status().isUnauthorized());
        verifyNoInteractions(clientService);
    }

    // ---- create ----

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_USER", "ROLE_ADMIN"})
    void create_validRequest_returns201WithLocationAndBody(String role) throws Exception {
        // given
        given(clientService.create(any(ClientRequest.class))).willReturn(response(5, false));

        // when / then
        mockMvc.perform(post("/api/clients")
                        .with(jwt().authorities(new SimpleGrantedAuthority(role)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/clients/5"))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("Acme"))
                .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void create_withoutDescription_returns201() throws Exception {
        // given
        given(clientService.create(any(ClientRequest.class))).willReturn(response(5, false));

        // when / then
        mockMvc.perform(post("/api/clients").with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"identifier\":\"123\",\"address\":\"Sofia\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void create_blankFields_returns400WithFieldErrors() throws Exception {
        // when / then
        mockMvc.perform(post("/api/clients").with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"identifier\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Name is required."))
                .andExpect(jsonPath("$.errors.identifier").value("Identifier is required."))
                .andExpect(jsonPath("$.errors.address").value("Address is required."));
        verifyNoInteractions(clientService);
    }

    @Test
    void create_tooLongFields_returns400WithFieldErrors() throws Exception {
        // given
        String body = "{\"name\":\"" + "a".repeat(101) + "\",\"identifier\":\"" + "b".repeat(51)
                + "\",\"address\":\"" + "c".repeat(201) + "\",\"description\":\"" + "d".repeat(501) + "\"}";

        // when / then
        mockMvc.perform(post("/api/clients").with(user())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Name must be at most 100 characters."))
                .andExpect(jsonPath("$.errors.identifier").value("Identifier must be at most 50 characters."))
                .andExpect(jsonPath("$.errors.address").value("Address must be at most 200 characters."))
                .andExpect(jsonPath("$.errors.description").value("Description must be at most 500 characters."));
    }

    @Test
    void create_fieldsAtMaxLength_returns201() throws Exception {
        // given
        given(clientService.create(any(ClientRequest.class))).willReturn(response(1, false));
        String body = "{\"name\":\"" + "a".repeat(100) + "\",\"identifier\":\"" + "b".repeat(50)
                + "\",\"address\":\"" + "c".repeat(200) + "\",\"description\":\"" + "d".repeat(500) + "\"}";

        // when / then
        mockMvc.perform(post("/api/clients").with(user())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    // ---- list ----

    @Test
    void list_asUserDefault_returns200WithPageShapeAndDefaults() throws Exception {
        // given
        given(clientService.list(isNull(), eq(false), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(response(1, false)), PageRequest.of(0, 20), 1));

        // when / then
        mockMvc.perform(get("/api/clients").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(1));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(clientService).list(isNull(), eq(false), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getSort().getOrderFor("name")).isNotNull();
    }

    @Test
    void list_customPageAndSize_passesPageableToService() throws Exception {
        // given
        given(clientService.list(isNull(), eq(false), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 0));

        // when
        mockMvc.perform(get("/api/clients?page=2&size=5").with(admin())).andExpect(status().isOk());

        // then
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(clientService).list(isNull(), eq(false), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void list_includeArchivedAsAdmin_returns200() throws Exception {
        // given
        given(clientService.list(isNull(), eq(true), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(response(1, false), response(2, true)), PageRequest.of(0, 20), 2));

        // when / then
        mockMvc.perform(get("/api/clients?includeArchived=true").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].archived").value(false))
                .andExpect(jsonPath("$.content[1].archived").value(true));
    }

    @Test
    void list_includeArchivedAsUser_returns403() throws Exception {
        // when / then
        mockMvc.perform(get("/api/clients?includeArchived=true").with(user()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(clientService);
    }

    @Test
    void list_withSearch_passesSearchToService() throws Exception {
        // given
        given(clientService.list(eq("acme"), eq(false), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(response(1, false)), PageRequest.of(0, 20), 1));

        // when / then
        mockMvc.perform(get("/api/clients?search=acme").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
        verify(clientService).list(eq("acme"), eq(false), any(Pageable.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "identifier", "createdAt", "updatedAt", "archivedAt"})
    void list_sortByAllowedField_passesSortToService(String field) throws Exception {
        // given
        given(clientService.list(isNull(), eq(false), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        // when
        mockMvc.perform(get("/api/clients?sort=" + field + ",desc").with(user())).andExpect(status().isOk());

        // then
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(clientService).list(isNull(), eq(false), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getSort().getOrderFor(field).isDescending()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"foo", "id", "archived", "description"})
    void list_sortByFieldNotAllowed_returns400WithMessage(String field) throws Exception {
        // when / then
        mockMvc.perform(get("/api/clients?sort=" + field).with(user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Sorting by '" + field
                        + "' is not supported. Allowed fields: name, identifier, createdAt, updatedAt, archivedAt."));
        verifyNoInteractions(clientService);
    }

    @Test
    void list_sizeAboveMax_isClampedTo100() throws Exception {
        // given
        given(clientService.list(isNull(), eq(false), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        // when
        mockMvc.perform(get("/api/clients?size=100000").with(user())).andExpect(status().isOk());

        // then
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(clientService).list(isNull(), eq(false), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    // ---- get ----

    @Test
    void get_asUser_passesIncludeArchivedFalse() throws Exception {
        // given
        given(clientService.get(1L, false)).willReturn(response(1, false));

        // when / then
        mockMvc.perform(get("/api/clients/1").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
        verify(clientService).get(1L, false);
    }

    @Test
    void get_asAdmin_passesIncludeArchivedTrue() throws Exception {
        // given
        given(clientService.get(1L, true)).willReturn(response(1, true));

        // when / then
        mockMvc.perform(get("/api/clients/1").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        verify(clientService).get(1L, true);
    }

    @Test
    void get_missingClient_returns404WithMessage() throws Exception {
        // given
        given(clientService.get(9L, false)).willThrow(new ResourceNotFoundException("Client 9 was not found."));

        // when / then
        mockMvc.perform(get("/api/clients/9").with(user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Client 9 was not found."));
    }

    // ---- update ----

    @Test
    void update_asAdmin_returns200() throws Exception {
        // given
        given(clientService.update(eq(1L), any(ClientRequest.class))).willReturn(response(1, false));

        // when / then
        mockMvc.perform(put("/api/clients/1").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme"));
    }

    @Test
    void update_asUser_returns403() throws Exception {
        // when / then
        mockMvc.perform(put("/api/clients/1").with(user())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(clientService);
    }

    @Test
    void update_invalidBody_returns400WithFieldErrors() throws Exception {
        // when / then
        mockMvc.perform(put("/api/clients/1").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"identifier\":\"1\",\"address\":\"a\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Name is required."));
    }

    @Test
    void update_missingClient_returns404() throws Exception {
        // given
        given(clientService.update(eq(1L), any(ClientRequest.class)))
                .willThrow(new ResourceNotFoundException("Client 1 was not found."));

        // when / then
        mockMvc.perform(put("/api/clients/1").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    // ---- archive ----

    @Test
    void archive_asAdmin_returns204() throws Exception {
        // when / then
        mockMvc.perform(delete("/api/clients/1").with(admin())).andExpect(status().isNoContent());
        verify(clientService).archive(1L);
    }

    @Test
    void archive_asUser_returns403() throws Exception {
        // when / then
        mockMvc.perform(delete("/api/clients/1").with(user())).andExpect(status().isForbidden());
        verifyNoInteractions(clientService);
    }

    @Test
    void archive_alreadyArchived_returns409WithMessage() throws Exception {
        // given
        doThrow(new BusinessRuleException("This client is already archived.")).when(clientService).archive(1L);

        // when / then
        mockMvc.perform(delete("/api/clients/1").with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This client is already archived."));
    }

    @Test
    void archive_missingClient_returns404() throws Exception {
        // given
        doThrow(new ResourceNotFoundException("Client 1 was not found.")).when(clientService).archive(1L);

        // when / then
        mockMvc.perform(delete("/api/clients/1").with(admin())).andExpect(status().isNotFound());
    }

    // ---- restore ----

    @Test
    void restore_asAdmin_returns200WithActiveClient() throws Exception {
        // given
        given(clientService.restore(1L)).willReturn(response(1, false));

        // when / then
        mockMvc.perform(post("/api/clients/1/restore").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.archivedAt").doesNotExist());
    }

    @Test
    void restore_asUser_returns403() throws Exception {
        // when / then
        mockMvc.perform(post("/api/clients/1/restore").with(user())).andExpect(status().isForbidden());
        verifyNoInteractions(clientService);
    }

    @Test
    void restore_notArchived_returns409WithMessage() throws Exception {
        // given
        given(clientService.restore(1L)).willThrow(new BusinessRuleException("This client is not archived."));

        // when / then
        mockMvc.perform(post("/api/clients/1/restore").with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This client is not archived."));
    }

    @Test
    void restore_missingClient_returns404() throws Exception {
        // given
        given(clientService.restore(1L)).willThrow(new ResourceNotFoundException("Client 1 was not found."));

        // when / then
        mockMvc.perform(post("/api/clients/1/restore").with(admin())).andExpect(status().isNotFound());
    }
}
