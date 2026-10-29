package com.creditsai.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.creditsai.config.AttachmentProperties;
import com.creditsai.dto.AttachmentDownload;
import com.creditsai.dto.AttachmentResponse;
import com.creditsai.dto.AttachmentUpload;
import com.creditsai.exception.BusinessRuleException;
import com.creditsai.exception.InvalidRequestException;
import com.creditsai.exception.PayloadTooLargeException;
import com.creditsai.exception.ResourceNotFoundException;
import com.creditsai.exception.UnsupportedFileTypeException;
import com.creditsai.mapper.AttachmentMapper;
import com.creditsai.model.Client;
import com.creditsai.model.ClientAttachment;
import com.creditsai.repository.ClientRepository;
import com.creditsai.storage.FileSystemAttachmentStorage;
import com.creditsai.storage.FileTypeDetector;
import com.creditsai.storage.SampleFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

/** Service tests with the real ingestor, detector and file-system storage (in a temp dir). */
@ExtendWith(MockitoExtension.class)
class AttachmentServiceImplTest {

    @TempDir
    Path tempDir;

    @Mock
    private ClientRepository clientRepository;

    private AttachmentServiceImpl service;
    private Client client;

    @BeforeEach
    void setUp() {
        service = serviceWith(new AttachmentProperties(tempDir.resolve("data"),
                DataSize.ofMegabytes(5), DataSize.ofMegabytes(20), 30));
        client = new Client();
        ReflectionTestUtils.setField(client, "id", 1L);
    }

    private AttachmentServiceImpl serviceWith(AttachmentProperties props) {
        FileSystemAttachmentStorage storage = new FileSystemAttachmentStorage(props);
        AttachmentIngestor ingestor = new AttachmentIngestor(storage, new FileTypeDetector(), props);
        return new AttachmentServiceImpl(clientRepository, storage, ingestor,
                Mappers.getMapper(AttachmentMapper.class));
    }

    private static AttachmentUpload upload(String name, byte[] content) {
        return new AttachmentUpload(name, content.length, new ByteArrayResource(content));
    }

    private void lockReturnsClient() {
        given(clientRepository.findByIdForUpdate(1L)).willReturn(Optional.of(client));
    }

    private ClientAttachment attached(long id, String name, long size) {
        ClientAttachment a = ClientAttachment.builder().fileName(name).contentType("text/plain").sizeBytes(size)
                .sha256("0".repeat(64)).uploadedBy("admin1").build();
        ReflectionTestUtils.setField(a, "id", id);
        client.addAttachment(a, 30, 20L * 1024 * 1024);
        return a;
    }

    private long filesOnDisk() throws IOException {
        Path dir = tempDir.resolve("data/clients/1");
        if (!Files.exists(dir)) {
            return 0;
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.count();
        }
    }

    // ---- list ----

    @Test
    void list_adminOrUser_returnsAttachmentsSortedByFileName() {
        // given
        attached(1, "b.txt", 1);
        attached(2, "A.txt", 1);
        attached(3, "c.txt", 1);
        given(clientRepository.findByIdAndArchivedFalse(1L)).willReturn(Optional.of(client));

        // when
        List<AttachmentResponse> result = service.list(1L, false);

        // then
        assertThat(result).extracting(AttachmentResponse::fileName).containsExactly("A.txt", "b.txt", "c.txt");
    }

    @Test
    void list_archivedClientNotAllowed_throwsNotFound() {
        // given
        given(clientRepository.findByIdAndArchivedFalse(1L)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.list(1L, false)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void list_archivedClientAllowed_usesPlainFindById() {
        // given
        given(clientRepository.findById(1L)).willReturn(Optional.of(client));

        // when / then
        assertThat(service.list(1L, true)).isEmpty();
    }

    // ---- download ----

    @Test
    void download_existingFile_returnsStoredTypeAndContent() throws IOException {
        // given: add a file through the service so it exists on disk
        lockReturnsClient();
        service.add(1L, List.of(upload("note.txt", SampleFiles.txt())), "admin1");
        ReflectionTestUtils.setField(client.getAttachments().get(0), "id", 9L);
        given(clientRepository.findById(1L)).willReturn(Optional.of(client));

        // when
        AttachmentDownload file = service.download(1L, 9L, true);

        // then
        assertThat(file.fileName()).isEqualTo("note.txt");
        assertThat(file.contentType()).isEqualTo("text/plain");
        assertThat(file.sizeBytes()).isEqualTo(SampleFiles.txt().length);
        assertThat(file.content().getInputStream().readAllBytes()).isEqualTo(SampleFiles.txt());
    }

    @Test
    void download_attachmentOfAnotherClient_throwsNotFound() {
        // given: client 1 has no attachment 77
        given(clientRepository.findById(1L)).willReturn(Optional.of(client));

        // when / then
        assertThatThrownBy(() -> service.download(1L, 77L, true)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void download_fileMissingOnDisk_throwsNotFoundWithoutPath() {
        // given
        attached(5, "gone.txt", 3);
        given(clientRepository.findById(1L)).willReturn(Optional.of(client));

        // when / then
        assertThatThrownBy(() -> service.download(1L, 5L, true))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("The content of file 'gone.txt' is not available.");
    }

    // ---- add ----

    @Test
    void add_everyAllowedType_isStoredWithDetectedTypeSizeAndHash() throws IOException {
        // given
        lockReturnsClient();
        List<AttachmentUpload> uploads = SampleFiles.allAllowed().entrySet().stream()
                .map(e -> upload(e.getKey(), e.getValue())).toList();

        // when
        List<AttachmentResponse> result = service.add(1L, uploads, "admin1");

        // then
        assertThat(result).hasSize(11);
        assertThat(result).extracting(AttachmentResponse::contentType).containsExactly(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.oasis.opendocument.text", "text/plain", "image/jpeg", "image/png",
                "image/gif", "image/webp", "image/heic", "image/tiff");
        assertThat(result).allSatisfy(r -> assertThat(r.uploadedBy()).isEqualTo("admin1"));
        assertThat(filesOnDisk()).isEqualTo(11);
        verify(clientRepository).flush();
    }

    @Test
    void add_records_sizeAndSha256OfTheContent() {
        // given
        lockReturnsClient();

        // when
        service.add(1L, List.of(upload("note.txt", "abc".getBytes(StandardCharsets.UTF_8))), "admin1");

        // then: sha256("abc")
        ClientAttachment saved = client.getAttachments().get(0);
        assertThat(saved.getSizeBytes()).isEqualTo(3);
        assertThat(saved.getSha256()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void add_pngRenamedToPdf_isRejectedAndNothingStored() throws IOException {
        // given
        lockReturnsClient();

        // when / then
        assertThatThrownBy(() -> service.add(1L, List.of(upload("fake.pdf", SampleFiles.png())), "admin1"))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessage("The content of file 'fake.pdf' is a PNG file, which does not match its extension '.pdf'.");
        assertThat(filesOnDisk()).isZero();
    }

    @Test
    void add_htmlAndSvgAndExecutable_areRejected() {
        // given
        lockReturnsClient();

        // when / then
        for (var sample : List.of(upload("a.html", SampleFiles.html()), upload("a.svg", SampleFiles.svg()),
                upload("a.txt", SampleFiles.html()), upload("a.pdf", SampleFiles.exe()),
                upload("a.docx", SampleFiles.plainZip()))) {
            assertThatThrownBy(() -> service.add(1L, List.of(sample), "admin1"))
                    .isInstanceOf(UnsupportedFileTypeException.class)
                    .hasMessageContaining("unsupported type")
                    .hasMessageContaining("PDF, DOCX, XLSX, ODT, TXT, JPEG, PNG, GIF, WEBP, HEIC, TIFF");
        }
    }

    @Test
    void add_fileLargerThanMax_throwsPayloadTooLargeBeforeReadingIt() throws IOException {
        // given
        lockReturnsClient();
        AttachmentUpload big = new AttachmentUpload("big.pdf", 5L * 1024 * 1024 + 1, new ByteArrayResource(new byte[0]));

        // when / then
        assertThatThrownBy(() -> service.add(1L, List.of(big), "admin1"))
                .isInstanceOf(PayloadTooLargeException.class)
                .hasMessage("File 'big.pdf' is larger than the maximum allowed size of 5 MB.");
        assertThat(filesOnDisk()).isZero();
    }

    @Test
    void add_emptyFile_throwsInvalidRequest() {
        // given
        lockReturnsClient();

        // when / then
        assertThatThrownBy(() -> service.add(1L, List.of(upload("empty.txt", new byte[0])), "admin1"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("File 'empty.txt' is empty.");
    }

    @Test
    void add_unsafeName_throwsInvalidRequest() {
        // given
        lockReturnsClient();

        // when / then
        assertThatThrownBy(() -> service.add(1L, List.of(upload("..", SampleFiles.txt())), "admin1"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void add_pathInName_isStoredUnderLastSegmentOnly() throws IOException {
        // given
        lockReturnsClient();

        // when
        List<AttachmentResponse> result = service.add(1L, List.of(upload("../../x/evil.txt", SampleFiles.txt())), "a");

        // then
        assertThat(result.get(0).fileName()).isEqualTo("evil.txt");
        assertThat(tempDir.resolve("data/clients/1/evil.txt")).exists();
    }

    @Test
    void add_duplicateNameWithinTheBatch_throwsConflict() {
        // given
        lockReturnsClient();

        // when / then
        assertThatThrownBy(() -> service.add(1L,
                List.of(upload("a.txt", SampleFiles.txt()), upload("a.txt", SampleFiles.txt())), "admin1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A file named 'a.txt' already exists for this client.");
    }

    @Test
    void add_caseVariantNameWithinTheBatch_throwsConflictQuotingStoredName() {
        // given
        lockReturnsClient();

        // when / then
        assertThatThrownBy(() -> service.add(1L,
                List.of(upload("a.txt", SampleFiles.txt()), upload("A.TXT", SampleFiles.txt())), "admin1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A file named 'a.txt' already exists for this client.");
    }

    @Test
    void add_caseVariantOfExistingName_throwsConflict() {
        // given
        attached(1, "contract.txt", 1);
        lockReturnsClient();

        // when / then
        assertThatThrownBy(() -> service.add(1L, List.of(upload("contract.txt", SampleFiles.txt())), "admin1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A file named 'contract.txt' already exists for this client.");
        assertThat(client.getAttachments()).extracting(ClientAttachment::getFileName).containsExactly("contract.txt");
    }

    @Test
    void add_mixedCaseName_isStoredAndReturnedInLowerCase() throws IOException {
        // given
        lockReturnsClient();

        // when
        List<AttachmentResponse> result = service.add(1L, List.of(upload("Report.PDF", SampleFiles.pdf())), "admin1");

        // then
        assertThat(result.get(0).fileName()).isEqualTo("report.pdf");
        assertThat(tempDir.resolve("data/clients/1").toFile().list()).containsExactly("report.pdf");
    }

    @Test
    void add_nameAlreadyOnClient_throwsConflict() {
        // given
        attached(1, "a.txt", 1);
        lockReturnsClient();

        // when / then
        assertThatThrownBy(() -> service.add(1L, List.of(upload("a.txt", SampleFiles.txt())), "admin1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A file named 'a.txt' already exists for this client.");
    }

    @Test
    void add_moreThanMaxFiles_throwsConflict() {
        // given
        AttachmentServiceImpl tiny = serviceWith(new AttachmentProperties(tempDir.resolve("data"),
                DataSize.ofMegabytes(5), DataSize.ofMegabytes(20), 2));
        lockReturnsClient();

        // when / then
        assertThatThrownBy(() -> tiny.add(1L, List.of(upload("1.txt", SampleFiles.txt()),
                upload("2.txt", SampleFiles.txt()), upload("3.txt", SampleFiles.txt())), "admin1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A client can have at most 2 files.");
    }

    @Test
    void add_beyondTotalSize_throwsConflict() {
        // given: total limit 1 MB, existing 1 MB - 1 byte
        AttachmentServiceImpl tiny = serviceWith(new AttachmentProperties(tempDir.resolve("data"),
                DataSize.ofMegabytes(5), DataSize.ofMegabytes(1), 30));
        ClientAttachment existing = ClientAttachment.builder().fileName("big.bin").contentType("text/plain")
                .sizeBytes(1024 * 1024 - 1).sha256("0".repeat(64)).uploadedBy("x").build();
        client.addAttachment(existing, 30, 1024 * 1024);
        lockReturnsClient();

        // when / then
        assertThatThrownBy(() -> tiny.add(1L, List.of(upload("a.txt", SampleFiles.txt())), "admin1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageStartingWith("All files of a client together may take at most 1 MB.");
    }

    @Test
    void add_missingClient_throwsNotFound() {
        // given
        given(clientRepository.findByIdForUpdate(1L)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.add(1L, List.of(upload("a.txt", SampleFiles.txt())), "admin1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- delete ----

    @Test
    void delete_existingAttachment_removesRowAndFile() throws IOException {
        // given
        lockReturnsClient();
        service.add(1L, List.of(upload("a.txt", SampleFiles.txt())), "admin1");
        ReflectionTestUtils.setField(client.getAttachments().get(0), "id", 4L);

        // when
        service.delete(1L, 4L);

        // then
        assertThat(client.getAttachments()).isEmpty();
        assertThat(filesOnDisk()).isZero();
    }

    @Test
    void delete_attachmentOfAnotherClient_throwsNotFoundAndKeepsFiles() throws IOException {
        // given
        lockReturnsClient();
        service.add(1L, List.of(upload("a.txt", SampleFiles.txt())), "admin1");

        // when / then
        assertThatThrownBy(() -> service.delete(1L, 999L)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(filesOnDisk()).isEqualTo(1);
    }
}
