package com.creditsai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.creditsai.dto.AttachmentUpload;
import com.creditsai.dto.ClientRequest;
import com.creditsai.dto.ClientResponse;
import com.creditsai.exception.BusinessRuleException;
import com.creditsai.exception.UnsupportedFileTypeException;
import com.creditsai.security.SecurityConfig;
import com.creditsai.service.ClientService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The multipart variant of {@code POST /api/clients} (client JSON part plus files). */
@WebMvcTest(ClientController.class)
@Import(SecurityConfig.class)
class ClientMultipartCreateTest {

    private static final String VALID =
            "{\"name\":\"Acme\",\"identifier\":\"123\",\"address\":\"Sofia\",\"description\":\"note\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClientService clientService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static RequestPostProcessor user() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")).jwt(j -> j.claim("preferred_username", "user1"));
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")).jwt(j -> j.claim("preferred_username", "admin1"));
    }

    private static MockMultipartFile clientPart(String json) {
        return new MockMultipartFile("client", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile file(String name) {
        return new MockMultipartFile("files", name, "application/pdf", "data".getBytes(StandardCharsets.UTF_8));
    }

    private static ClientResponse created() {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        return new ClientResponse(5L, "Acme", "123", "Sofia", "note", false, null, now, now);
    }

    @Test
    void createWithFiles_withoutToken_returns401() throws Exception {
        mockMvc.perform(multipart("/api/clients").file(clientPart(VALID))).andExpect(status().isUnauthorized());
        verifyNoInteractions(clientService);
    }

    @Test
    void createWithFiles_asUser_returns201AndPassesFilesAndUsername() throws Exception {
        // given
        given(clientService.create(any(ClientRequest.class), any(), eq("user1"))).willReturn(created());

        // when / then
        mockMvc.perform(multipart("/api/clients").file(clientPart(VALID)).file(file("a.pdf")).file(file("b.pdf")).with(user()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/clients/5")))
                .andExpect(jsonPath("$.id").value(5));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AttachmentUpload>> captor = ArgumentCaptor.forClass(List.class);
        verify(clientService).create(any(ClientRequest.class), captor.capture(), eq("user1"));
        assertThat(captor.getValue()).extracting(AttachmentUpload::fileName).containsExactly("a.pdf", "b.pdf");
    }

    @Test
    void createWithFiles_asAdminWithoutFiles_returns201() throws Exception {
        // given
        given(clientService.create(any(ClientRequest.class), eq(List.of()), eq("admin1"))).willReturn(created());

        // when / then
        mockMvc.perform(multipart("/api/clients").file(clientPart(VALID)).with(admin())).andExpect(status().isCreated());
    }

    @Test
    void createWithFiles_invalidClientPart_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(multipart("/api/clients")
                        .file(clientPart("{\"name\":\"\",\"identifier\":\"1\",\"address\":\"a\"}")).with(user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Name is required."));
        verifyNoInteractions(clientService);
    }

    @Test
    void createWithFiles_missingClientPart_returns400() throws Exception {
        mockMvc.perform(multipart("/api/clients").file(file("a.pdf")).with(user()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithFiles_rejectedFile_returnsReadableProblem() throws Exception {
        // given
        given(clientService.create(any(ClientRequest.class), any(), any()))
                .willThrow(new UnsupportedFileTypeException("File 'a.pdf' has an unsupported type."))
                .willThrow(new BusinessRuleException("A client can have at most 30 files."));

        // when / then
        mockMvc.perform(multipart("/api/clients").file(clientPart(VALID)).file(file("a.pdf")).with(user()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value("File 'a.pdf' has an unsupported type."));
        mockMvc.perform(multipart("/api/clients").file(clientPart(VALID)).file(file("a.pdf")).with(user()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A client can have at most 30 files."));
    }

    @Test
    void create_jsonVariant_stillWorksAsBefore() throws Exception {
        // given
        given(clientService.create(any(ClientRequest.class))).willReturn(created());

        // when / then
        mockMvc.perform(post("/api/clients").with(user()).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated());
        verify(clientService).create(any(ClientRequest.class));
    }
}
