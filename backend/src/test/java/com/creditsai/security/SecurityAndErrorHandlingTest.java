package com.creditsai.security;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.creditsai.exception.BusinessRuleException;
import com.creditsai.exception.GlobalExceptionHandler;
import com.creditsai.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tests {@link SecurityConfig} and {@link GlobalExceptionHandler} through a test-only controller,
 * since no real endpoints exist yet. Requests carry a real {@code Authorization: Bearer} header and
 * a mocked {@link JwtDecoder}, so the Keycloak role converter bean is exercised as in production.
 */
@WebMvcTest(controllers = SecurityAndErrorHandlingTest.ProbeController.class)
@Import({SecurityConfig.class, SecurityAndErrorHandlingTest.ProbeController.class})
class SecurityAndErrorHandlingTest {

    private static final String USER_TOKEN = "user-token";
    private static final String ADMIN_TOKEN = "admin-token";
    private static final String NO_ROLE_TOKEN = "no-role-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void request_withoutToken_returns401() throws Exception {
        // when / then
        mockMvc.perform(get("/api/probe"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void read_asUser_returns200() throws Exception {
        // given
        givenToken(USER_TOKEN, "USER");

        // when / then
        mockMvc.perform(get("/api/probe").header(HttpHeaders.AUTHORIZATION, bearer(USER_TOKEN)))
                .andExpect(status().isOk());
    }

    @Test
    void read_withoutRealmRoles_returns403() throws Exception {
        // given
        givenToken(NO_ROLE_TOKEN);

        // when / then
        mockMvc.perform(get("/api/probe").header(HttpHeaders.AUTHORIZATION, bearer(NO_ROLE_TOKEN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You do not have permission to perform this action."));
    }

    @Test
    void adminAction_asUser_returns403() throws Exception {
        // given
        givenToken(USER_TOKEN, "USER");

        // when / then
        mockMvc.perform(delete("/api/probe/1").header(HttpHeaders.AUTHORIZATION, bearer(USER_TOKEN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminAction_asAdmin_returns204() throws Exception {
        // given
        givenToken(ADMIN_TOKEN, "USER", "ADMIN");

        // when / then
        mockMvc.perform(delete("/api/probe/1").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_TOKEN)))
                .andExpect(status().isNoContent());
    }

    @Test
    void notFound_returns404ProblemDetailWithMessage() throws Exception {
        // given
        givenToken(USER_TOKEN, "USER");

        // when / then
        mockMvc.perform(get("/api/probe/not-found").header(HttpHeaders.AUTHORIZATION, bearer(USER_TOKEN)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Client 42 was not found."));
    }

    @Test
    void businessRule_returns409ProblemDetailWithMessage() throws Exception {
        // given
        givenToken(USER_TOKEN, "USER");

        // when / then
        mockMvc.perform(get("/api/probe/conflict").header(HttpHeaders.AUTHORIZATION, bearer(USER_TOKEN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The client has an active credit and cannot be archived."));
    }

    @Test
    void invalidBody_returns400WithFieldErrors() throws Exception {
        // given
        givenToken(USER_TOKEN, "USER");

        // when / then
        mockMvc.perform(post("/api/probe")
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Name is required."));
    }

    @Test
    void malformedJson_returns400() throws Exception {
        // given
        givenToken(USER_TOKEN, "USER");

        // when / then
        mockMvc.perform(post("/api/probe")
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_TOKEN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unexpectedError_returns500WithGenericMessageAndNoInternals() throws Exception {
        // given
        givenToken(USER_TOKEN, "USER");

        // when / then
        mockMvc.perform(get("/api/probe/boom").header(HttpHeaders.AUTHORIZATION, bearer(USER_TOKEN)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred. Please try again later."))
                .andExpect(content().string(Matchers.not(Matchers.containsString("SQL"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("IllegalStateException"))));
    }

    @Test
    void preflight_fromOtherOrigin_isRejected() throws Exception {
        // when / then
        mockMvc.perform(options("/api/probe")
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private void givenToken(String token, String... realmRoles) {
        Jwt jwt = Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .claim("preferred_username", "tester")
                .claim("realm_access", Map.of("roles", List.of(realmRoles)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        given(jwtDecoder.decode(token)).willReturn(jwt);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    record ProbeRequest(@NotBlank(message = "Name is required.") String name) {
    }

    @RestController
    @RequestMapping("/api/probe")
    static class ProbeController {

        @GetMapping
        @PreAuthorize("hasAnyRole('USER','ADMIN')")
        String read() {
            return "ok";
        }

        @PostMapping
        @PreAuthorize("hasAnyRole('USER','ADMIN')")
        ProbeRequest create(@Valid @RequestBody ProbeRequest request) {
            return request;
        }

        @DeleteMapping("/{id}")
        @PreAuthorize("hasRole('ADMIN')")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void archive() {
        }

        @GetMapping("/not-found")
        @PreAuthorize("hasAnyRole('USER','ADMIN')")
        String notFound() {
            throw new ResourceNotFoundException("Client 42 was not found.");
        }

        @GetMapping("/conflict")
        @PreAuthorize("hasAnyRole('USER','ADMIN')")
        String conflict() {
            throw new BusinessRuleException("The client has an active credit and cannot be archived.");
        }

        @GetMapping("/boom")
        @PreAuthorize("hasAnyRole('USER','ADMIN')")
        String boom() {
            throw new IllegalStateException("SQL error in table clients");
        }
    }
}
