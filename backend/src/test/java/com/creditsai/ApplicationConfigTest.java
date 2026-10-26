package com.creditsai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Starts the application with its real configuration (full stack behind nginx) to check that
 * {@code application.yml} is valid and points at the public issuer, the internal Keycloak and
 * a same-origin-only CORS policy.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ApplicationConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @Test
    void jwtProperties_usePublicIssuerAndInternalJwks() {
        // when
        String issuer = environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri");
        String jwkSetUri = environment.getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri");

        // then
        assertThat(issuer).isEqualTo("http://localhost/auth/realms/credits");
        assertThat(jwkSetUri).startsWith("http://keycloak:8080/auth/realms/credits/");
    }

    @Test
    void health_isPublicAndUp() throws Exception {
        // when / then
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void preflight_fromOtherLocalOrigin_isRejected() throws Exception {
        // when / then
        mockMvc.perform(options("/api/clients")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
