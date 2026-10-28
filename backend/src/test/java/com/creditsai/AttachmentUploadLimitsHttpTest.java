package com.creditsai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Real HTTP against the embedded Tomcat, so the {@code spring.servlet.multipart} limits and the
 * handler for {@code MaxUploadSizeExceededException} are exercised (MockMvc does not apply them).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AttachmentUploadLimitsHttpTest {

    private static final Path STORAGE = storageDir();
    private static final int MB = 1024 * 1024;

    @LocalServerPort
    private int port;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Value("${spring.servlet.multipart.max-file-size}")
    private String configuredMaxFile;

    private RestClient client;

    private static Path storageDir() {
        try {
            return Files.createTempDirectory("attachments-http");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void removeStorageDir() throws IOException {
        FileSystemUtils.deleteRecursively(STORAGE);
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.attachments.storage-dir", () -> STORAGE.toString());
    }

    @BeforeEach
    void setUp() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "u1")
                .claim("preferred_username", "user1").claim("azp", "credits-web")
                .claim("realm_access", Map.of("roles", List.of("USER")))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
        given(jwtDecoder.decode(anyString())).willReturn(jwt);
        client = RestClient.builder().baseUrl("http://localhost:" + port)
                .requestFactory(new org.springframework.http.client.BufferingClientHttpRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory()))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer t")
                .defaultStatusHandler(status -> true, (req, res) -> { })
                .build();
    }

    private MultiValueMap<String, Object> body(int fileCount, int fileSize) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        parts.add("client", new HttpEntity<>(
                "{\"name\":\"Acme\",\"identifier\":\"1\",\"address\":\"Sofia\"}", json));
        for (int i = 0; i < fileCount; i++) {
            byte[] content = new byte[fileSize];
            java.util.Arrays.fill(content, (byte) 'a');
            parts.add("files", new ByteArrayResource(content) {
                @Override
                public String getFilename() {
                    return "f" + System.nanoTime() + ".txt";
                }
            });
        }
        return parts;
    }

    private org.springframework.http.ResponseEntity<String> post(MultiValueMap<String, Object> body) {
        return client.post().uri(URI.create("http://localhost:" + port + "/api/clients"))
                .contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().toEntity(String.class);
    }

    @Test
    void config_multipartFileLimitMatchesAttachmentLimit() {
        assertThat(configuredMaxFile).isEqualTo("5MB");
    }

    @Test
    void create_fileLargerThanMultipartLimit_returns413ProblemDetail() {
        // when
        var response = post(body(1, 5 * MB + 10));

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).contains("\"detail\":\"The upload is too large.");
    }

    @Test
    void create_requestLargerThanMultipartRequestLimit_returns413ProblemDetail() {
        // when: 6 x 4 MB = 24 MB > 21 MB request limit
        var response = post(body(6, 4 * MB));

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).contains("The upload is too large.");
    }

    @Test
    void create_31SmallFilesOverRealHttp_isRejectedWithTheCountMessage() {
        // when
        var response = post(body(31, 10));

        // then: the container accepts 31 parts and the business rule answers
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).contains("A client can have at most 30 files.");
    }

    @Test
    void create_30SmallFilesOverRealHttp_returns201() {
        // when
        var response = post(body(30, 10));

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void create_33PartsOverRealHttp_isRejectedByTheContainer() {
        // when: 32 files + client part = 33 parts > max-part-count 32
        var response = post(body(32, 10));

        // then: the container's part-count limit is answered with the readable 413 problem
        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).contains("The upload is too large.");
    }
}
