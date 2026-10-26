package com.creditsai.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.creditsai.TestcontainersConfiguration;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Runs the real auto-configured {@code JwtDecoder} against tokens signed with a test key, whose
 * public key is served by an in-process JWKS endpoint. Proves that signature, issuer and the
 * {@code azp} restriction from {@link SecurityConfig} are all enforced together.
 *
 * <p>An authenticated request to a path with no endpoint gives 404; a rejected token gives 401.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class JwtValidationIntegrationTest {

    private static final String ISSUER = "http://localhost/auth/realms/credits";
    private static final RSAKey SIGNING_KEY = generateKey();
    private static final RSAKey FOREIGN_KEY = generateKey();
    private static final HttpServer JWKS_SERVER = startJwksServer();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:" + JWKS_SERVER.getAddress().getPort() + "/certs");
    }

    @AfterAll
    static void stopJwksServer() {
        JWKS_SERVER.stop(0);
    }

    @Test
    void request_tokenIssuedToCreditsWeb_isAuthenticated() throws Exception {
        // given
        String token = token(SIGNING_KEY, ISSUER, "credits-web");

        // when / then
        mockMvc.perform(get("/api/clients").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void request_tokenIssuedToOtherClient_returns401() throws Exception {
        // given
        String token = token(SIGNING_KEY, ISSUER, "admin-cli");

        // when / then
        mockMvc.perform(get("/api/clients").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void request_tokenWithoutAzp_returns401() throws Exception {
        // given
        String token = token(SIGNING_KEY, ISSUER, null);

        // when / then
        mockMvc.perform(get("/api/clients").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void request_tokenFromOtherIssuer_returns401() throws Exception {
        // given
        String token = token(SIGNING_KEY, "http://localhost/auth/realms/other", "credits-web");

        // when / then
        mockMvc.perform(get("/api/clients").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void request_tokenSignedWithUnknownKey_returns401() throws Exception {
        // given
        String token = token(FOREIGN_KEY, ISSUER, "credits-web");

        // when / then
        mockMvc.perform(get("/api/clients").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void request_expiredToken_returns401() throws Exception {
        // given: expired beyond the default 60 s clock skew
        String token = token(SIGNING_KEY, ISSUER, "credits-web", Instant.now().minusSeconds(120));

        // when / then
        mockMvc.perform(get("/api/clients").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void request_nonStringAzp_returns401() throws Exception {
        // given
        String token = token(SIGNING_KEY, ISSUER, List.of("credits-web"));

        // when / then
        mockMvc.perform(get("/api/clients").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private static String token(RSAKey key, String issuer, Object azp) throws JOSEException {
        return token(key, issuer, azp, Instant.now().plusSeconds(300));
    }

    private static String token(RSAKey key, String issuer, Object azp, Instant expiresAt) throws JOSEException {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("user-id")
                .claim("preferred_username", "user1")
                .claim("realm_access", Map.of("roles", List.of("USER")))
                .issueTime(Date.from(expiresAt.minusSeconds(300)))
                .expirationTime(Date.from(expiresAt));
        if (azp != null) {
            claims.claim("azp", azp);
        }
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(),
                claims.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("key-" + System.nanoTime()).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpServer startJwksServer() {
        try {
            byte[] body = new JWKSet(SIGNING_KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/certs", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
