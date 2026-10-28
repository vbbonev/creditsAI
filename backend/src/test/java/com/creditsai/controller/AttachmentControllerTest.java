package com.creditsai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.creditsai.dto.AttachmentDownload;
import com.creditsai.dto.AttachmentResponse;
import com.creditsai.dto.AttachmentUpload;
import com.creditsai.exception.BusinessRuleException;
import com.creditsai.exception.PayloadTooLargeException;
import com.creditsai.exception.ResourceNotFoundException;
import com.creditsai.exception.UnsupportedFileTypeException;
import com.creditsai.security.SecurityConfig;
import com.creditsai.service.AttachmentService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AttachmentController.class)
@Import(SecurityConfig.class)
class AttachmentControllerTest {

    private static final String BASE = "/api/clients/1/attachments";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AttachmentService attachmentService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static RequestPostProcessor user() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                .jwt(j -> j.claim("preferred_username", "admin1"));
    }

    private static AttachmentResponse response(long id, String name) {
        return new AttachmentResponse(id, name, "application/pdf", 10, "admin1", Instant.parse("2026-01-01T10:00:00Z"));
    }

    private static MockMultipartFile file(String name) {
        return new MockMultipartFile("files", name, "application/pdf", "data".getBytes(StandardCharsets.UTF_8));
    }

    // ---- 401 ----

    @Test
    void allEndpoints_withoutToken_return401() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/2/content")).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart(BASE).file(file("a.pdf"))).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(BASE + "/2")).andExpect(status().isUnauthorized());
        verifyNoInteractions(attachmentService);
    }

    // ---- list ----

    @Test
    void list_asUser_returns200WithoutArchivedAccess() throws Exception {
        // given
        given(attachmentService.list(1L, false)).willReturn(List.of(response(2, "a.pdf")));

        // when / then
        mockMvc.perform(get(BASE).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].fileName").value("a.pdf"))
                .andExpect(jsonPath("$[0].contentType").value("application/pdf"))
                .andExpect(jsonPath("$[0].sizeBytes").value(10))
                .andExpect(jsonPath("$[0].uploadedBy").value("admin1"))
                .andExpect(jsonPath("$[0].uploadedAt").exists());
    }

    @Test
    void list_asAdmin_mayUseArchivedClient() throws Exception {
        // given
        given(attachmentService.list(1L, true)).willReturn(List.of());

        // when / then
        mockMvc.perform(get(BASE).with(admin())).andExpect(status().isOk());
        verify(attachmentService).list(1L, true);
    }

    @Test
    void list_archivedClientAsUser_returns404() throws Exception {
        // given
        given(attachmentService.list(1L, false)).willThrow(new ResourceNotFoundException("Client 1 was not found."));

        // when / then
        mockMvc.perform(get(BASE).with(user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Client 1 was not found."));
    }

    // ---- download ----

    @Test
    void download_asUser_streamsFileWithSafeHeaders() throws Exception {
        // given
        given(attachmentService.download(1L, 2L, false)).willReturn(new AttachmentDownload(
                "résumé 1.pdf", "application/pdf", 4, new ByteArrayResource("data".getBytes(StandardCharsets.UTF_8))));

        // when / then
        mockMvc.perform(get(BASE + "/2/content").with(user()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Length", "4"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"resume 1.pdf\"; filename*=UTF-8''r%C3%A9sum%C3%A9%201.pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox; default-src 'none'"))
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(content().bytes("data".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void download_asAdmin_mayUseArchivedClient() throws Exception {
        // given
        given(attachmentService.download(1L, 2L, true)).willReturn(new AttachmentDownload(
                "a.txt", "text/plain", 1, new ByteArrayResource(new byte[] {'x'})));

        // when / then
        mockMvc.perform(get(BASE + "/2/content").with(admin())).andExpect(status().isOk());
    }

    @Test
    void download_attachmentOfAnotherClient_returns404() throws Exception {
        // given
        given(attachmentService.download(1L, 2L, false))
                .willThrow(new ResourceNotFoundException("Attachment 2 was not found."));

        // when / then
        mockMvc.perform(get(BASE + "/2/content").with(user())).andExpect(status().isNotFound());
    }

    // ---- add ----

    @Test
    void add_asAdmin_returns201WithCreatedAttachments() throws Exception {
        // given
        given(attachmentService.add(eq(1L), any(), eq("admin1")))
                .willReturn(List.of(response(2, "a.pdf"), response(3, "b.pdf")));

        // when / then
        mockMvc.perform(multipart(BASE).file(file("a.pdf")).file(file("b.pdf")).with(admin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].fileName").value("b.pdf"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AttachmentUpload>> captor = ArgumentCaptor.forClass(List.class);
        verify(attachmentService).add(eq(1L), captor.capture(), eq("admin1"));
        org.assertj.core.api.Assertions.assertThat(captor.getValue())
                .extracting(AttachmentUpload::fileName).containsExactly("a.pdf", "b.pdf");
    }

    @Test
    void add_asUser_returns403() throws Exception {
        mockMvc.perform(multipart(BASE).file(file("a.pdf")).with(user())).andExpect(status().isForbidden());
        verifyNoInteractions(attachmentService);
    }

    @Test
    void add_withoutFilesPart_returns400() throws Exception {
        mockMvc.perform(multipart(BASE).with(admin())).andExpect(status().isBadRequest());
    }

    @Test
    void add_ruleViolations_areMappedToReadableProblems() throws Exception {
        // given
        given(attachmentService.add(eq(1L), any(), any()))
                .willThrow(new BusinessRuleException("A file named 'a.pdf' already exists for this client."))
                .willThrow(new PayloadTooLargeException("File 'a.pdf' is larger than the maximum allowed size of 5 MB."))
                .willThrow(new UnsupportedFileTypeException("File 'a.pdf' has an unsupported type."));

        // when / then
        mockMvc.perform(multipart(BASE).file(file("a.pdf")).with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A file named 'a.pdf' already exists for this client."));
        mockMvc.perform(multipart(BASE).file(file("a.pdf")).with(admin()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.detail").value("File 'a.pdf' is larger than the maximum allowed size of 5 MB."));
        mockMvc.perform(multipart(BASE).file(file("a.pdf")).with(admin()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value("File 'a.pdf' has an unsupported type."));
    }

    @Test
    void add_missingClient_returns404() throws Exception {
        // given
        given(attachmentService.add(eq(1L), any(), any())).willThrow(new ResourceNotFoundException("Client 1 was not found."));

        // when / then
        mockMvc.perform(multipart(BASE).file(file("a.pdf")).with(admin())).andExpect(status().isNotFound());
    }

    // ---- delete ----

    @Test
    void delete_asAdmin_returns204() throws Exception {
        mockMvc.perform(delete(BASE + "/2").with(admin())).andExpect(status().isNoContent());
        verify(attachmentService).delete(1L, 2L);
    }

    @Test
    void delete_asUser_returns403() throws Exception {
        mockMvc.perform(delete(BASE + "/2").with(user())).andExpect(status().isForbidden());
        verifyNoInteractions(attachmentService);
    }

    @Test
    void delete_attachmentOfAnotherClient_returns404() throws Exception {
        // given
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Attachment 2 was not found."))
                .when(attachmentService).delete(1L, 2L);

        // when / then
        mockMvc.perform(delete(BASE + "/2").with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Attachment 2 was not found."));
    }
}
