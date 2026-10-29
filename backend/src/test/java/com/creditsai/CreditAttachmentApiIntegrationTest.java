package com.creditsai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.creditsai.dto.StorageCleanupReport;
import com.creditsai.repository.ClientRepository;
import com.creditsai.repository.CreditRepository;
import com.creditsai.service.AttachmentStorageCleanupService;
import com.creditsai.storage.SampleFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

/** Full-stack credit attachment flows on real Postgres and a real (temporary) attachments folder. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CreditAttachmentApiIntegrationTest {

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
    private ClientRepository clientRepository;

    @Autowired
    private CreditRepository creditRepository;

    @Autowired
    private AttachmentStorageCleanupService cleanupService;

    private long clientId;

    private static Path createStorageDir() {
        try {
            return Files.createTempDirectory("credit-attachments-it");
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
    void clean() throws Exception {
        creditRepository.deleteAll();
        clientRepository.deleteAll();
        try (Stream<Path> s = Files.list(STORAGE)) {
            for (Path p : s.toList()) {
                FileSystemUtils.deleteRecursively(p);
            }
        }
        MvcResult created = mockMvc.perform(post("/api/clients").with(USER).contentType(MediaType.APPLICATION_JSON)
                .content(CLIENT_JSON)).andReturn();
        String location = created.getResponse().getHeader("Location");
        clientId = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    private String creditJson() {
        return "{\"clientId\":" + clientId + ",\"type\":\"CONSUMER\",\"amount\":10000,\"currency\":\"EUR\","
                + "\"interestRate\":12,\"startDate\":\"2026-01-15\",\"endDate\":\"2027-01-15\","
                + "\"hasCollateral\":false}";
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("files", name, "application/octet-stream", content);
    }

    private static byte[] text(int size) {
        byte[] b = new byte[size];
        Arrays.fill(b, (byte) 'a');
        return b;
    }

    private MvcResult createCredit(RequestPostProcessor who, MockMultipartFile... files) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/credits").file(
                new MockMultipartFile("credit", "", MediaType.APPLICATION_JSON_VALUE,
                        creditJson().getBytes(StandardCharsets.UTF_8)));
        for (MockMultipartFile f : files) {
            request.file(f);
        }
        return mockMvc.perform(request.with(who)).andReturn();
    }

    private long createCreditOk(MockMultipartFile... files) throws Exception {
        MvcResult result = createCredit(USER, files);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        String location = result.getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    private MvcResult addFiles(long creditId, RequestPostProcessor who, MockMultipartFile... files) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/credits/" + creditId + "/attachments");
        for (MockMultipartFile f : files) {
            request.file(f);
        }
        return mockMvc.perform(request.with(who)).andReturn();
    }

    private long firstAttachmentId(long creditId) throws Exception {
        String body = mockMvc.perform(get("/api/credits/" + creditId + "/attachments").with(ADMIN))
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll("^\\[\\{\"id\":(\\d+).*", "$1"));
    }

    private long fileCountOnDisk() throws IOException {
        try (Stream<Path> s = Files.walk(STORAGE)) {
            return s.filter(Files::isRegularFile).count();
        }
    }

    private void deactivateAndArchiveCredit(long creditId) throws Exception {
        mockMvc.perform(post("/api/credits/" + creditId + "/status").with(ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/credits/" + creditId).with(ADMIN)).andExpect(status().isNoContent());
    }

    // ---- create with files ----

    @Test
    void allEndpoints_withoutToken_return401() throws Exception {
        mockMvc.perform(get("/api/credits/1/attachments")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/credits/1/attachments/2/content")).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/credits/1/attachments").file(file("a.txt", SampleFiles.txt())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/credits/1/attachments/2")).andExpect(status().isUnauthorized());
    }

    @Test
    void createWithFiles_asUser_storesUnderCreditsFolderAndListsSortedForEveryone() throws Exception {
        // when
        long id = createCreditOk(file("zeta.txt", SampleFiles.txt()), file("Contract.PDF", SampleFiles.pdf()));

        // then: on disk under credits/{id} with lower-case names, not under the client
        assertThat(STORAGE.resolve("credits/" + id + "/contract.pdf")).exists();
        assertThat(STORAGE.resolve("credits/" + id + "/zeta.txt")).exists();
        assertThat(STORAGE.resolve("clients")).doesNotExist();
        mockMvc.perform(get("/api/credits/" + id + "/attachments").with(USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fileName").value("contract.pdf"))
                .andExpect(jsonPath("$[0].contentType").value("application/pdf"))
                .andExpect(jsonPath("$[0].uploadedBy").value("user1"))
                .andExpect(jsonPath("$[1].fileName").value("zeta.txt"));
        // and: the client's own files are separate
        mockMvc.perform(get("/api/clients/" + clientId + "/attachments").with(USER))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createWithFiles_returnsTheCreditWithItsRecalculatedPlan() throws Exception {
        // when
        MvcResult result = createCredit(USER, file("a.txt", SampleFiles.txt()));

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getContentAsString())
                .contains("\"status\":\"ACTIVE\"").contains("\"termMonths\":12").contains("\"creditNumber\":\"CR-");
    }

    @Test
    void createWithoutFiles_asMultipartOrJson_works() throws Exception {
        // when / then
        long id = createCreditOk();
        mockMvc.perform(get("/api/credits/" + id + "/attachments").with(USER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(post("/api/credits").with(USER).contentType(MediaType.APPLICATION_JSON).content(creditJson()))
                .andExpect(status().isCreated());
    }

    @Test
    void createWithFiles_oneSpoofedFile_isAtomicNoCreditAndNoFilesRemain() throws Exception {
        // when
        MvcResult result = createCredit(USER, file("ok.pdf", SampleFiles.pdf()), file("fake.pdf", SampleFiles.png()));

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(415);
        assertThat(creditRepository.count()).isZero();
        assertThat(fileCountOnDisk()).isZero();
    }

    @Test
    void createWithFiles_invalidCredit_returns400AndStoresNothing() throws Exception {
        // when: amount is negative
        MvcResult result = mockMvc.perform(multipart("/api/credits")
                .file(new MockMultipartFile("credit", "", MediaType.APPLICATION_JSON_VALUE,
                        creditJson().replace("\"amount\":10000", "\"amount\":-1").getBytes(StandardCharsets.UTF_8)))
                .file(file("a.txt", SampleFiles.txt())).with(USER)).andReturn();

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(creditRepository.count()).isZero();
        assertThat(fileCountOnDisk()).isZero();
    }

    @Test
    void createWithFiles_duplicateNameAnyCase_returns409NamingTheCreditAndNothingRemains() throws Exception {
        // when
        MvcResult result = createCredit(USER, file("a.txt", SampleFiles.txt()), file("A.TXT", SampleFiles.txt()));

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString())
                .contains("A file named 'a.txt' already exists for this credit.");
        assertThat(creditRepository.count()).isZero();
        assertThat(fileCountOnDisk()).isZero();
    }

    @Test
    void createWithFiles_archivedClient_returns404AndStoresNothing() throws Exception {
        // given
        mockMvc.perform(delete("/api/clients/" + clientId).with(ADMIN)).andExpect(status().isNoContent());

        // when
        MvcResult result = createCredit(USER, file("a.txt", SampleFiles.txt()));

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(fileCountOnDisk()).isZero();
    }

    // ---- per-credit limits ----

    @Test
    void createWithFiles_moreThan30Files_returns409AndNothingRemains() throws Exception {
        // given
        MockMultipartFile[] files = new MockMultipartFile[31];
        for (int i = 0; i < files.length; i++) {
            files[i] = file("f" + i + ".txt", SampleFiles.txt());
        }

        // when
        MvcResult result = createCredit(USER, files);

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("A credit can have at most 30 files.");
        assertThat(creditRepository.count()).isZero();
        assertThat(fileCountOnDisk()).isZero();
    }

    @Test
    void createWithFiles_totalOver20MB_returns409() throws Exception {
        // when
        MvcResult result = createCredit(USER, file("1.txt", text(5 * MB)), file("2.txt", text(5 * MB)),
                file("3.txt", text(5 * MB)), file("4.txt", text(5 * MB)), file("5.txt", text(1)));

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString())
                .contains("All files of a credit together may take at most 20 MB.");
        assertThat(fileCountOnDisk()).isZero();
    }

    @Test
    void createWithFiles_fileOver5MB_returns413() throws Exception {
        // when
        MvcResult result = createCredit(USER, file("big.txt", text(5 * MB + 1)));

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(413);
        assertThat(fileCountOnDisk()).isZero();
    }

    @Test
    void limits_areCountedPerCreditIndependentOfTheClientsOwnFiles() throws Exception {
        // given: the client holds 20 MB in 4 files and two credits each hold 20 MB too
        MockMultipartHttpServletRequestBuilder clientRequest = multipart("/api/clients").file(
                new MockMultipartFile("client", "", MediaType.APPLICATION_JSON_VALUE,
                        CLIENT_JSON.getBytes(StandardCharsets.UTF_8)));
        for (int i = 1; i <= 4; i++) {
            clientRequest.file(file(i + ".txt", text(5 * MB)));
        }
        assertThat(mockMvc.perform(clientRequest.with(USER)).andReturn().getResponse().getStatus()).isEqualTo(201);

        // when / then: a credit of that client still takes its own 20 MB, and so does a second credit
        for (int credit = 0; credit < 2; credit++) {
            createCreditOk(file("1.txt", text(5 * MB)), file("2.txt", text(5 * MB)), file("3.txt", text(5 * MB)),
                    file("4.txt", text(5 * MB)));
        }
        assertThat(fileCountOnDisk()).isEqualTo(12);
    }

    @Test
    void addFiles_countsAgainstTheCreditsExistingFiles() throws Exception {
        // given: 4 x 5 MB already
        long id = createCreditOk(file("1.txt", text(5 * MB)), file("2.txt", text(5 * MB)),
                file("3.txt", text(5 * MB)), file("4.txt", text(5 * MB)));

        // when
        MvcResult result = addFiles(id, ADMIN, file("5.txt", text(1)));

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString())
                .contains("All files of a credit together may take at most 20 MB.");
        assertThat(fileCountOnDisk()).isEqualTo(4);
    }

    @Test
    void parallelUploads_neverExceedTheTotalLimit() throws Exception {
        // given
        long id = createCreditOk();
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

        // then
        assertThat(created).isEqualTo(4);
        assertThat(conflicts).isEqualTo(2);
        assertThat(fileCountOnDisk()).isEqualTo(4);
    }

    // ---- download ----

    @Test
    void download_anyUser_getsBytesWithSafeHeaders() throws Exception {
        // given
        long id = createCreditOk(file("contract.pdf", SampleFiles.pdf()));
        long attachmentId = firstAttachmentId(id);

        // when
        MvcResult result = mockMvc.perform(get("/api/credits/" + id + "/attachments/" + attachmentId + "/content")
                        .with(USER))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Length", String.valueOf(SampleFiles.pdf().length)))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox; default-src 'none'"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn();

        // then
        assertThat(result.getResponse().getHeader("Content-Disposition")).startsWith("attachment;")
                .contains("contract.pdf");
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(SampleFiles.pdf());
    }

    @Test
    void attachmentOfAnotherCredit_isNotFoundThroughTheWrongCredit() throws Exception {
        // given
        long first = createCreditOk(file("a.txt", SampleFiles.txt()));
        long second = createCreditOk();
        long attachmentId = firstAttachmentId(first);

        // when / then
        mockMvc.perform(get("/api/credits/" + second + "/attachments/" + attachmentId + "/content").with(USER))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/credits/" + second + "/attachments/" + attachmentId).with(ADMIN))
                .andExpect(status().isNotFound());
        assertThat(STORAGE.resolve("credits/" + first + "/a.txt")).exists();
    }

    @Test
    void unknownCredit_returns404ForEveryEndpoint() throws Exception {
        mockMvc.perform(get("/api/credits/999999/attachments").with(ADMIN)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/credits/999999/attachments/1/content").with(ADMIN)).andExpect(status().isNotFound());
        assertThat(addFiles(999999, ADMIN, file("a.txt", SampleFiles.txt())).getResponse().getStatus())
                .isEqualTo(404);
        mockMvc.perform(delete("/api/credits/999999/attachments/1").with(ADMIN)).andExpect(status().isNotFound());
    }

    // ---- add and delete (ADMIN only) ----

    @Test
    void addFiles_asAdminToActiveCredit_returns201() throws Exception {
        // given
        long id = createCreditOk(file("a.txt", SampleFiles.txt()));

        // when
        MvcResult added = addFiles(id, ADMIN, file("b.docx", SampleFiles.docx()), file("c.xlsx", SampleFiles.xlsx()));

        // then
        assertThat(added.getResponse().getStatus()).isEqualTo(201);
        assertThat(added.getResponse().getContentAsString())
                .contains("\"fileName\":\"b.docx\"").contains("\"uploadedBy\":\"admin1\"");
        assertThat(fileCountOnDisk()).isEqualTo(3);
        assertThat(addFiles(id, ADMIN, file("a.txt", SampleFiles.txt())).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void addFiles_failingBatch_leavesNoFilesBehind() throws Exception {
        // given
        long id = createCreditOk(file("a.txt", SampleFiles.txt()));

        // when
        MvcResult result = addFiles(id, ADMIN, file("b.pdf", SampleFiles.pdf()), file("c.pdf", SampleFiles.exe()));

        // then
        assertThat(result.getResponse().getStatus()).isEqualTo(415);
        assertThat(fileCountOnDisk()).isEqualTo(1);
    }

    @Test
    void addAndDelete_asUser_return403() throws Exception {
        // given
        long id = createCreditOk(file("a.txt", SampleFiles.txt()));
        long attachmentId = firstAttachmentId(id);

        // when / then
        assertThat(addFiles(id, USER, file("b.txt", SampleFiles.txt())).getResponse().getStatus()).isEqualTo(403);
        mockMvc.perform(delete("/api/credits/" + id + "/attachments/" + attachmentId).with(USER))
                .andExpect(status().isForbidden());
        assertThat(fileCountOnDisk()).isEqualTo(1);
    }

    @Test
    void delete_asAdmin_removesRowAndFilePhysicallyAndAllowsReupload() throws Exception {
        // given
        long id = createCreditOk(file("a.txt", SampleFiles.txt()), file("b.txt", SampleFiles.txt()));
        long firstId = firstAttachmentId(id);

        // when
        mockMvc.perform(delete("/api/credits/" + id + "/attachments/" + firstId).with(ADMIN))
                .andExpect(status().isNoContent());

        // then
        assertThat(STORAGE.resolve("credits/" + id + "/a.txt")).doesNotExist();
        assertThat(STORAGE.resolve("credits/" + id + "/b.txt")).exists();
        mockMvc.perform(get("/api/credits/" + id + "/attachments").with(USER))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fileName").value("b.txt"));
        mockMvc.perform(get("/api/credits/" + id + "/attachments/" + firstId + "/content").with(USER))
                .andExpect(status().isNotFound());
        assertThat(addFiles(id, ADMIN, file("A.txt", SampleFiles.txt())).getResponse().getStatus()).isEqualTo(201);
        assertThat(STORAGE.resolve("credits/" + id).toFile().list()).containsExactlyInAnyOrder("a.txt", "b.txt");
    }

    // ---- visibility ----

    @Test
    void archivedCredit_attachmentsAreAdminOnly_andAdminCanStillAddAndDelete() throws Exception {
        // given
        long id = createCreditOk(file("a.txt", SampleFiles.txt()));
        long attachmentId = firstAttachmentId(id);
        deactivateAndArchiveCredit(id);
        String path = "/api/credits/" + id + "/attachments";

        // then: files untouched, USER gets 404 on list and download
        assertThat(STORAGE.resolve("credits/" + id + "/a.txt")).exists();
        mockMvc.perform(get(path).with(USER)).andExpect(status().isNotFound());
        mockMvc.perform(get(path + "/" + attachmentId + "/content").with(USER)).andExpect(status().isNotFound());

        // and: ADMIN lists, downloads, adds and deletes
        mockMvc.perform(get(path).with(ADMIN)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get(path + "/" + attachmentId + "/content").with(ADMIN)).andExpect(status().isOk());
        assertThat(addFiles(id, ADMIN, file("b.txt", SampleFiles.txt())).getResponse().getStatus()).isEqualTo(201);
        mockMvc.perform(delete(path + "/" + attachmentId).with(ADMIN)).andExpect(status().isNoContent());
        assertThat(STORAGE.resolve("credits/" + id + "/a.txt")).doesNotExist();
    }

    @Test
    void creditOfArchivedClient_attachmentsAreAdminOnly() throws Exception {
        // given: deactivate the credit so the client can be archived (the credit itself stays unarchived)
        long id = createCreditOk(file("a.txt", SampleFiles.txt()));
        long attachmentId = firstAttachmentId(id);
        mockMvc.perform(post("/api/credits/" + id + "/status").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INACTIVE\"}")).andExpect(status().isOk());
        mockMvc.perform(delete("/api/clients/" + clientId).with(ADMIN)).andExpect(status().isNoContent());
        String path = "/api/credits/" + id + "/attachments";

        // then
        mockMvc.perform(get(path).with(USER)).andExpect(status().isNotFound());
        mockMvc.perform(get(path + "/" + attachmentId + "/content").with(USER)).andExpect(status().isNotFound());
        mockMvc.perform(get(path).with(ADMIN)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        assertThat(addFiles(id, ADMIN, file("b.txt", SampleFiles.txt())).getResponse().getStatus()).isEqualTo(201);
    }

    // ---- startup migration and cleanup ----

    @Test
    void migrateLegacyLayout_oldClientFolders_areMovedOnceAndNothingIsOverwritten() throws Exception {
        // given: an old-layout folder 77, and a conflicting pair 88 (old and new)
        Files.createDirectories(STORAGE.resolve("77"));
        Files.writeString(STORAGE.resolve("77/a.txt"), "seventy-seven");
        Files.createDirectories(STORAGE.resolve("88"));
        Files.writeString(STORAGE.resolve("88/old.txt"), "old");
        Files.createDirectories(STORAGE.resolve("clients/88"));
        Files.writeString(STORAGE.resolve("clients/88/new.txt"), "new");

        // when
        int first = cleanupService.migrateLegacyLayout();
        int second = cleanupService.migrateLegacyLayout();

        // then
        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(Files.readString(STORAGE.resolve("clients/77/a.txt"))).isEqualTo("seventy-seven");
        assertThat(STORAGE.resolve("77")).doesNotExist();
        assertThat(STORAGE.resolve("88/old.txt")).exists();
        assertThat(STORAGE.resolve("clients/88/new.txt")).exists();
        assertThat(STORAGE.resolve("clients/88/old.txt")).doesNotExist();
    }

    @Test
    void cleanup_coversClientAndCreditFolders_removesOldLeftoversAndKeepsOrphans() throws Exception {
        // given: old leftovers and an old orphan under both roots
        Instant old = Instant.now().minus(3, ChronoUnit.HOURS);
        List<Path> leftovers = new ArrayList<>();
        for (String root : List.of("clients/5", "credits/5")) {
            Files.createDirectories(STORAGE.resolve(root));
            for (String name : List.of(".deleted-abc", ".upload-xyz.tmp")) {
                Path leftover = Files.writeString(STORAGE.resolve(root).resolve(name), "x");
                Files.setLastModifiedTime(leftover, FileTime.from(old));
                leftovers.add(leftover);
            }
            Path orphan = Files.writeString(STORAGE.resolve(root).resolve("orphan.txt"), "x");
            Files.setLastModifiedTime(orphan, FileTime.from(old));
        }

        // when
        StorageCleanupReport report = cleanupService.cleanup();

        // then
        assertThat(leftovers).allSatisfy(p -> assertThat(p).doesNotExist());
        assertThat(STORAGE.resolve("clients/5/orphan.txt")).exists();
        assertThat(STORAGE.resolve("credits/5/orphan.txt")).exists();
        assertThat(report.leftoversRemoved()).isEqualTo(4);
        assertThat(report.orphanFiles()).isEqualTo(2);
        assertThat(report.clientsChecked()).isEqualTo(1);
        assertThat(report.creditsChecked()).isEqualTo(1);
    }

    @Test
    void cleanup_creditRowsAreMatchedWithCreditFiles_noFalseReports() throws Exception {
        // given: a credit with a real file and row
        createCreditOk(file("a.txt", SampleFiles.txt()));

        // when
        StorageCleanupReport report = cleanupService.cleanup();

        // then
        assertThat(report.creditsChecked()).isEqualTo(1);
        assertThat(report.missingFiles()).isZero();
        assertThat(report.orphanFiles()).isZero();
        assertThat(report.errors()).isZero();
    }
}
