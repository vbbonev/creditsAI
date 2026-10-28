package com.creditsai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.creditsai.repository.ClientRepository;
import com.creditsai.storage.SampleFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.util.FileSystemUtils;

/** Full-stack attachment flows on real Postgres and a real (temporary) attachments folder. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AttachmentApiIntegrationTest {

    private static final Path STORAGE = createStorageDir();
    private static final RequestPostProcessor USER = jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))
            .jwt(j -> j.claim("preferred_username", "user1"));
    private static final RequestPostProcessor ADMIN = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
            .jwt(j -> j.claim("preferred_username", "admin1"));
    private static final String CLIENT_JSON =
            "{\"name\":\"Acme\",\"identifier\":\"123\",\"address\":\"Sofia\",\"description\":\"note\"}";
    private static final int MB = 1024 * 1024;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository repository;

    private static Path createStorageDir() {
        try {
            return Files.createTempDirectory("attachments-it");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void removeStorageDir() throws IOException {
        FileSystemUtils.deleteRecursively(STORAGE);
    }

    @DynamicPropertySource
    static void storageDir(DynamicPropertyRegistry registry) {
        registry.add("app.attachments.storage-dir", () -> STORAGE.toString());
    }

    @BeforeEach
    void clean() throws IOException {
        repository.deleteAll();
        try (Stream<Path> s = Files.list(STORAGE)) {
            for (Path p : s.toList()) {
                try (Stream<Path> inner = Files.walk(p)) {
                    inner.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
                }
            }
        }
    }

    private static MockMultipartFile clientPart() {
        return new MockMultipartFile("client", "", MediaType.APPLICATION_JSON_VALUE,
                CLIENT_JSON.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("files", name, "application/octet-stream", content);
    }

    private static byte[] text(int size) {
        byte[] b = new byte[size];
        Arrays.fill(b, (byte) 'a');
        return b;
    }

    private MvcResult createClient(RequestPostProcessor who, MockMultipartFile... files) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/clients").file(clientPart());
        for (MockMultipartFile f : files) {
            request.file(f);
        }
        return mockMvc.perform(request.with(who)).andReturn();
    }

    private long createClientOk(MockMultipartFile... files) throws Exception {
        MvcResult result = createClient(USER, files);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        String location = result.getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    private long fileCountOnDisk() throws IOException {
        try (Stream<Path> s = Files.walk(STORAGE)) {
            return s.filter(Files::isRegularFile).count();
        }
    }

    private MvcResult addFiles(long clientId, RequestPostProcessor who, MockMultipartFile... files) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/clients/" + clientId + "/attachments");
        for (MockMultipartFile f : files) {
            request.file(f);
        }
        return mockMvc.perform(request.with(who)).andReturn();
    }

    @Test
    void createWithFiles_asUser_storesFilesAndExposesThemToEveryone() throws Exception {
        // when
        long id = createClientOk(file("contract.pdf", SampleFiles.pdf()), file("Photo.PNG", SampleFiles.png()),
                file("zé.txt", SampleFiles.txt()));

        // then: on disk under the client folder with their real names
        assertThat(STORAGE.resolve(id + "/contract.pdf")).exists();
        assertThat(STORAGE.resolve(id + "/photo.png")).exists();
        assertThat(STORAGE.resolve(id + "/zé.txt")).exists();

        // and: listed sorted by name with detected types and the uploader
        mockMvc.perform(get("/api/clients/" + id + "/attachments").with(USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].fileName").value("contract.pdf"))
                .andExpect(jsonPath("$[0].contentType").value("application/pdf"))
                .andExpect(jsonPath("$[0].uploadedBy").value("user1"))
                .andExpect(jsonPath("$[0].sizeBytes").value(SampleFiles.pdf().length))
                .andExpect(jsonPath("$[1].fileName").value("photo.png"))
                .andExpect(jsonPath("$[1].contentType").value("image/png"))
                .andExpect(jsonPath("$[2].fileName").value("zé.txt"))
                .andExpect(jsonPath("$[2].uploadedAt").exists());
    }

    @Test
    void upload_mixedCaseName_isStoredListedAndDownloadedInLowerCase() throws Exception {
        // given
        long id = createClientOk(file("Report.PDF", SampleFiles.pdf()));

        // then: disk and listing use the lower-case name
        assertThat(STORAGE.resolve(String.valueOf(id)).toFile().list()).containsExactly("report.pdf");
        String body = mockMvc.perform(get("/api/clients/" + id + "/attachments").with(USER))
                .andExpect(jsonPath("$[0].fileName").value("report.pdf"))
                .andReturn().getResponse().getContentAsString();
        long attachmentId = Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
        mockMvc.perform(get("/api/clients/" + id + "/attachments/" + attachmentId + "/content").with(USER))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("filename=\"report.pdf\"")));

        // and: the same name in other case is a 409
        MvcResult again = addFiles(id, ADMIN, file("REPORT.pdf", SampleFiles.pdf()));
        assertThat(again.getResponse().getStatus()).isEqualTo(409);
        assertThat(again.getResponse().getContentAsString())
                .contains("A file named 'report.pdf' already exists for this client.");
    }

    @Test
    void download_anyUser_getsBytesWithSafeHeaders() throws Exception {
        // given
        long id = createClientOk(file("contract.pdf", SampleFiles.pdf()));
        String body = mockMvc.perform(get("/api/clients/" + id + "/attachments").with(USER))
                .andReturn().getResponse().getContentAsString();
        long attachmentId = Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

        // when
        MvcResult result = mockMvc.perform(get("/api/clients/" + id + "/attachments/" + attachmentId + "/content").with(USER))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Length", String.valueOf(SampleFiles.pdf().length)))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("private")))
                .andReturn();

        // then
        assertThat(result.getResponse().getHeader("Content-Disposition")).startsWith("attachment;")
                .contains("contract.pdf");
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(SampleFiles.pdf());
    }

    @Test
    void createWithFiles_oneSpoofedFile_isAtomicNoClientAndNoFilesRemain() throws Exception {
        // when: the first file is fine, the second is a PNG named .pdf
        MvcResult result = createClient(USER, file("ok.pdf", SampleFiles.pdf()), file("fake.pdf", SampleFiles.png()));

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(415);
        assertThat(result.getResponse().getContentAsString()).contains(
                "The content of file 'fake.pdf' is a PNG file, which does not match its extension '.pdf'.");
        assertThat(repository.count()).isZero();
        assertThat(fileCountOnDisk()).isZero();
    }

    @Test
    void createWithFiles_duplicateNameInTheBatch_returns409AndNothingRemains() throws Exception {
        // when
        MvcResult result = createClient(USER, file("a.txt", SampleFiles.txt()), file("a.txt", SampleFiles.txt()));

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString())
                .contains("A file named 'a.txt' already exists for this client.");
        assertThat(repository.count()).isZero();
        assertThat(fileCountOnDisk()).isZero();
    }

    @Test
    void createWithFiles_moreThan30Files_returns409AndNothingRemains() throws Exception {
        // given
        MockMultipartFile[] files = new MockMultipartFile[31];
        for (int i = 0; i < files.length; i++) {
            files[i] = file("f" + i + ".txt", SampleFiles.txt());
        }

        // when
        MvcResult result = createClient(USER, files);

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("A client can have at most 30 files.");
        assertThat(repository.count()).isZero();
        assertThat(fileCountOnDisk()).isZero();
    }

    @Test
    void createWithFiles_exactly30Files_isAccepted() throws Exception {
        // given
        MockMultipartFile[] files = new MockMultipartFile[30];
        for (int i = 0; i < files.length; i++) {
            files[i] = file("f" + i + ".txt", SampleFiles.txt());
        }

        // when / then
        createClientOk(files);
        assertThat(fileCountOnDisk()).isEqualTo(30);
    }

    @Test
    void createWithFiles_fileOver5MB_returns413AndNothingRemains() throws Exception {
        // when
        MvcResult result = createClient(USER, file("ok.txt", SampleFiles.txt()), file("big.txt", text(5 * MB + 1)));

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(413);
        assertThat(result.getResponse().getContentAsString())
                .contains("File 'big.txt' is larger than the maximum allowed size of 5 MB.");
        assertThat(repository.count()).isZero();
        assertThat(fileCountOnDisk()).isZero();
    }

    @Test
    void createWithFiles_fileOfExactly5MB_isAccepted() throws Exception {
        // when / then
        createClientOk(file("max.txt", text(5 * MB)));
    }

    @Test
    void createWithFiles_totalOver20MB_returns409AndNothingRemains() throws Exception {
        // when: 4 x 5 MB fit, the 5th does not
        MvcResult result = createClient(USER, file("1.txt", text(5 * MB)), file("2.txt", text(5 * MB)),
                file("3.txt", text(5 * MB)), file("4.txt", text(5 * MB)), file("5.txt", text(1)));

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString())
                .contains("All files of a client together may take at most 20 MB.");
        assertThat(repository.count()).isZero();
        assertThat(fileCountOnDisk()).isZero();
    }

    @Test
    void addFiles_asAdmin_returns201AndCountsAgainstExistingFiles() throws Exception {
        // given
        long id = createClientOk(file("a.txt", SampleFiles.txt()));

        // when
        MvcResult added = addFiles(id, ADMIN, file("b.docx", SampleFiles.docx()), file("c.xlsx", SampleFiles.xlsx()));

        // then
        assertThat(added.getResponse().getStatus()).isEqualTo(201);
        assertThat(added.getResponse().getContentAsString())
                .contains("\"fileName\":\"b.docx\"")
                .contains("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .contains("\"uploadedBy\":\"admin1\"");
        assertThat(fileCountOnDisk()).isEqualTo(3);

        // and: the same name again is a 409
        assertThat(addFiles(id, ADMIN, file("a.txt", SampleFiles.txt())).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void addFiles_sameNameInOtherCase_returns409() throws Exception {
        // given
        long id = createClientOk(file("a.txt", SampleFiles.txt()));

        // when
        MvcResult result = addFiles(id, ADMIN, file("A.TXT", SampleFiles.txt()));

        // then: readable message quoting the stored (lower-case) name; only the first file exists
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString())
                .contains("A file named 'a.txt' already exists for this client.");
        assertThat(fileCountOnDisk()).isEqualTo(1);
    }

    @Test
    void createWithFiles_caseVariantNamesInOneBatch_returns409AndNothingRemains() throws Exception {
        // when
        MvcResult result = createClient(USER, file("a.txt", SampleFiles.txt()), file("A.txt", SampleFiles.txt()));

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(repository.count()).isZero();
        assertThat(fileCountOnDisk()).isZero();
    }

    @Test
    void deleteThenReuploadWithDifferentCase_returns201() throws Exception {
        // given
        long id = createClientOk(file("a.txt", SampleFiles.txt()));
        String body = mockMvc.perform(get("/api/clients/" + id + "/attachments").with(USER))
                .andReturn().getResponse().getContentAsString();
        long attachmentId = Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

        // when
        mockMvc.perform(delete("/api/clients/" + id + "/attachments/" + attachmentId).with(ADMIN))
                .andExpect(status().isNoContent());
        MvcResult again = addFiles(id, ADMIN, file("A.txt", SampleFiles.txt()));

        // then: stored under the lower-case name
        assertThat(again.getResponse().getStatus()).isEqualTo(201);
        // (names are listed, because the test machine's file system may be case-insensitive)
        assertThat(STORAGE.resolve(String.valueOf(id)).toFile().list()).containsExactly("a.txt");
    }

    @Test
    void addFiles_asUserToExistingClient_returns403() throws Exception {
        // given
        long id = createClientOk();

        // when / then
        assertThat(addFiles(id, USER, file("a.txt", SampleFiles.txt())).getResponse().getStatus()).isEqualTo(403);
        assertThat(fileCountOnDisk()).isZero();
    }

    @Test
    void addFiles_failingBatch_leavesNoFilesBehind() throws Exception {
        // given
        long id = createClientOk(file("a.txt", SampleFiles.txt()));

        // when: second file is rejected after the first one was already written
        MvcResult result = addFiles(id, ADMIN, file("b.pdf", SampleFiles.pdf()), file("c.pdf", SampleFiles.exe()));

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(415);
        assertThat(fileCountOnDisk()).isEqualTo(1);
        mockMvc.perform(get("/api/clients/" + id + "/attachments").with(USER))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void parallelUploads_neverExceedTheTotalLimit() throws Exception {
        // given: an empty client and 6 admins uploading 5 MB each at the same time
        long id = createClientOk();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String name = "p" + i + ".txt";
            Callable<Integer> task = () -> addFiles(id, ADMIN, file(name, text(5 * MB))).getResponse().getStatus();
            results.add(pool.submit(task));
        }

        // when
        int created = 0;
        int conflicts = 0;
        for (Future<Integer> f : results) {
            int code = f.get();
            if (code == 201) {
                created++;
            } else if (code == 409) {
                conflicts++;
            }
        }
        pool.shutdown();

        // then: exactly 4 x 5 MB fit into 20 MB
        assertThat(created).isEqualTo(4);
        assertThat(conflicts).isEqualTo(2);
        assertThat(fileCountOnDisk()).isEqualTo(4);
    }

    @Test
    void delete_asAdmin_removesRowAndFilePhysically() throws Exception {
        // given
        long id = createClientOk(file("a.txt", SampleFiles.txt()), file("b.txt", SampleFiles.txt()));
        String body = mockMvc.perform(get("/api/clients/" + id + "/attachments").with(USER))
                .andReturn().getResponse().getContentAsString();
        long firstId = Long.parseLong(body.replaceAll("^\\[\\{\"id\":(\\d+).*", "$1"));

        // when: a USER may not, an ADMIN may
        mockMvc.perform(delete("/api/clients/" + id + "/attachments/" + firstId).with(USER))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/clients/" + id + "/attachments/" + firstId).with(ADMIN))
                .andExpect(status().isNoContent());

        // then: row and file are gone, the other file stays
        assertThat(STORAGE.resolve(id + "/a.txt")).doesNotExist();
        assertThat(STORAGE.resolve(id + "/b.txt")).exists();
        mockMvc.perform(get("/api/clients/" + id + "/attachments").with(USER))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fileName").value("b.txt"));
        mockMvc.perform(get("/api/clients/" + id + "/attachments/" + firstId + "/content").with(USER))
                .andExpect(status().isNotFound());
    }

    @Test
    void attachmentOfAnotherClient_isNotFoundThroughTheWrongClient() throws Exception {
        // given: two clients, a file on the first
        long first = createClientOk(file("a.txt", SampleFiles.txt()));
        long second = createClientOk();
        String body = mockMvc.perform(get("/api/clients/" + first + "/attachments").with(USER))
                .andReturn().getResponse().getContentAsString();
        long attachmentId = Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

        // when / then
        mockMvc.perform(get("/api/clients/" + second + "/attachments/" + attachmentId + "/content").with(USER))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/clients/" + second + "/attachments/" + attachmentId).with(ADMIN))
                .andExpect(status().isNotFound());
        assertThat(STORAGE.resolve(first + "/a.txt")).exists();
    }

    @Test
    void archivedClient_attachmentsAreAdminOnly_andArchivingKeepsFiles() throws Exception {
        // given
        long id = createClientOk(file("a.txt", SampleFiles.txt()));
        mockMvc.perform(delete("/api/clients/" + id).with(ADMIN)).andExpect(status().isNoContent());
        String path = "/api/clients/" + id + "/attachments";

        // then: files untouched; USER gets 404 on list and download
        assertThat(STORAGE.resolve(id + "/a.txt")).exists();
        mockMvc.perform(get(path).with(USER)).andExpect(status().isNotFound());
        mockMvc.perform(get(path + "/1/content").with(USER)).andExpect(status().isNotFound());

        // and: ADMIN can list, add and delete
        mockMvc.perform(get(path).with(ADMIN)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        assertThat(addFiles(id, ADMIN, file("b.txt", SampleFiles.txt())).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void createWithoutFilesAsMultipart_works() throws Exception {
        // when / then
        long id = createClientOk();
        mockMvc.perform(get("/api/clients/" + id + "/attachments").with(USER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
}
