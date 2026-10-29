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
import com.creditsai.model.AttachmentDraft;
import com.creditsai.model.Credit;
import com.creditsai.model.CreditAttachment;
import com.creditsai.repository.CreditRepository;
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
class CreditAttachmentServiceImplTest {

    @TempDir
    Path tempDir;

    @Mock
    private CreditRepository creditRepository;

    private CreditAttachmentServiceImpl service;
    private Credit credit;

    @BeforeEach
    void setUp() {
        service = serviceWith(new AttachmentProperties(tempDir.resolve("data"),
                DataSize.ofMegabytes(5), DataSize.ofMegabytes(20), 30));
        credit = new Credit();
        ReflectionTestUtils.setField(credit, "id", 1L);
    }

    private CreditAttachmentServiceImpl serviceWith(AttachmentProperties props) {
        FileSystemAttachmentStorage storage = new FileSystemAttachmentStorage(props);
        AttachmentIngestor ingestor = new AttachmentIngestor(storage, new FileTypeDetector(), props);
        return new CreditAttachmentServiceImpl(creditRepository, storage, ingestor,
                Mappers.getMapper(AttachmentMapper.class));
    }

    private static AttachmentUpload upload(String name, byte[] content) {
        return new AttachmentUpload(name, content.length, new ByteArrayResource(content));
    }

    private void lockReturnsCredit() {
        given(creditRepository.findByIdForUpdate(1L)).willReturn(Optional.of(credit));
    }

    private CreditAttachment attached(long id, String name, long size) {
        CreditAttachment a = credit.attach(new AttachmentDraft(name, "text/plain", size, "0".repeat(64), "admin1"),
                30, 20L * 1024 * 1024);
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    private long filesOnDisk() throws IOException {
        Path dir = tempDir.resolve("data/credits/1");
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
        given(creditRepository.findByIdAndArchivedFalseAndClientArchivedFalse(1L)).willReturn(Optional.of(credit));

        // when
        List<AttachmentResponse> result = service.list(1L, false);

        // then
        assertThat(result).extracting(AttachmentResponse::fileName).containsExactly("A.txt", "b.txt", "c.txt");
    }

    @Test
    void list_archivedCreditNotAllowed_throwsNotFound() {
        // given
        given(creditRepository.findByIdAndArchivedFalseAndClientArchivedFalse(1L)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.list(1L, false)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void list_archivedCreditAllowed_usesPlainFindById() {
        // given
        given(creditRepository.findById(1L)).willReturn(Optional.of(credit));

        // when / then
        assertThat(service.list(1L, true)).isEmpty();
    }

    // ---- download ----

    @Test
    void download_existingFile_returnsStoredTypeAndContent() throws IOException {
        // given: add a file through the service so it exists on disk
        lockReturnsCredit();
        service.add(1L, List.of(upload("note.txt", SampleFiles.txt())), "admin1");
        ReflectionTestUtils.setField(credit.getAttachments().get(0), "id", 9L);
        given(creditRepository.findById(1L)).willReturn(Optional.of(credit));

        // when
        AttachmentDownload file = service.download(1L, 9L, true);

        // then
        assertThat(file.fileName()).isEqualTo("note.txt");
        assertThat(file.contentType()).isEqualTo("text/plain");
        assertThat(file.sizeBytes()).isEqualTo(SampleFiles.txt().length);
        assertThat(file.content().getInputStream().readAllBytes()).isEqualTo(SampleFiles.txt());
    }

    @Test
    void download_attachmentOfAnotherCredit_throwsNotFound() {
        // given: credit 1 has no attachment 77
        given(creditRepository.findById(1L)).willReturn(Optional.of(credit));

        // when / then
        assertThatThrownBy(() -> service.download(1L, 77L, true)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void download_fileMissingOnDisk_throwsNotFoundWithoutPath() {
        // given
        attached(5, "gone.txt", 3);
        given(creditRepository.findById(1L)).willReturn(Optional.of(credit));

        // when / then
        assertThatThrownBy(() -> service.download(1L, 5L, true))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("The content of file 'gone.txt' is not available.");
    }

    // ---- add ----

    @Test
    void add_everyAllowedType_isStoredWithDetectedTypeSizeAndHash() throws IOException {
        // given
        lockReturnsCredit();
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
        verify(creditRepository).flush();
    }

    @Test
    void add_records_sizeAndSha256OfTheContent() {
        // given
        lockReturnsCredit();

        // when
        service.add(1L, List.of(upload("note.txt", "abc".getBytes(StandardCharsets.UTF_8))), "admin1");

        // then: sha256("abc")
        CreditAttachment saved = credit.getAttachments().get(0);
        assertThat(saved.getSizeBytes()).isEqualTo(3);
        assertThat(saved.getSha256()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void add_pngRenamedToPdf_isRejectedAndNothingStored() throws IOException {
        // given
        lockReturnsCredit();

        // when / then
        assertThatThrownBy(() -> service.add(1L, List.of(upload("fake.pdf", SampleFiles.png())), "admin1"))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessage("The content of file 'fake.pdf' is a PNG file, which does not match its extension '.pdf'.");
        assertThat(filesOnDisk()).isZero();
    }

    @Test
    void add_htmlAndSvgAndExecutable_areRejected() {
        // given
        lockReturnsCredit();

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
        lockReturnsCredit();
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
        lockReturnsCredit();

        // when / then
        assertThatThrownBy(() -> service.add(1L, List.of(upload("empty.txt", new byte[0])), "admin1"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("File 'empty.txt' is empty.");
    }

    @Test
    void add_unsafeName_throwsInvalidRequest() {
        // given
        lockReturnsCredit();

        // when / then
        assertThatThrownBy(() -> service.add(1L, List.of(upload("..", SampleFiles.txt())), "admin1"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void add_pathInName_isStoredUnderLastSegmentOnly() throws IOException {
        // given
        lockReturnsCredit();

        // when
        List<AttachmentResponse> result = service.add(1L, List.of(upload("../../x/evil.txt", SampleFiles.txt())), "a");

        // then
        assertThat(result.get(0).fileName()).isEqualTo("evil.txt");
        assertThat(tempDir.resolve("data/credits/1/evil.txt")).exists();
    }

    @Test
    void add_duplicateNameWithinTheBatch_throwsConflict() {
        // given
        lockReturnsCredit();

        // when / then
        assertThatThrownBy(() -> service.add(1L,
                List.of(upload("a.txt", SampleFiles.txt()), upload("a.txt", SampleFiles.txt())), "admin1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A file named 'a.txt' already exists for this credit.");
    }

    @Test
    void add_caseVariantNameWithinTheBatch_throwsConflictQuotingStoredName() {
        // given
        lockReturnsCredit();

        // when / then
        assertThatThrownBy(() -> service.add(1L,
                List.of(upload("a.txt", SampleFiles.txt()), upload("A.TXT", SampleFiles.txt())), "admin1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A file named 'a.txt' already exists for this credit.");
    }

    @Test
    void add_caseVariantOfExistingName_throwsConflict() {
        // given
        attached(1, "contract.txt", 1);
        lockReturnsCredit();

        // when / then
        assertThatThrownBy(() -> service.add(1L, List.of(upload("contract.txt", SampleFiles.txt())), "admin1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A file named 'contract.txt' already exists for this credit.");
        assertThat(credit.getAttachments()).extracting(CreditAttachment::getFileName).containsExactly("contract.txt");
    }

    @Test
    void add_mixedCaseName_isStoredAndReturnedInLowerCase() throws IOException {
        // given
        lockReturnsCredit();

        // when
        List<AttachmentResponse> result = service.add(1L, List.of(upload("Report.PDF", SampleFiles.pdf())), "admin1");

        // then
        assertThat(result.get(0).fileName()).isEqualTo("report.pdf");
        assertThat(tempDir.resolve("data/credits/1").toFile().list()).containsExactly("report.pdf");
    }

    @Test
    void add_nameAlreadyOnCredit_throwsConflict() {
        // given
        attached(1, "a.txt", 1);
        lockReturnsCredit();

        // when / then
        assertThatThrownBy(() -> service.add(1L, List.of(upload("a.txt", SampleFiles.txt())), "admin1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A file named 'a.txt' already exists for this credit.");
    }

    @Test
    void add_moreThanMaxFiles_throwsConflict() {
        // given
        CreditAttachmentServiceImpl tiny = serviceWith(new AttachmentProperties(tempDir.resolve("data"),
                DataSize.ofMegabytes(5), DataSize.ofMegabytes(20), 2));
        lockReturnsCredit();

        // when / then
        assertThatThrownBy(() -> tiny.add(1L, List.of(upload("1.txt", SampleFiles.txt()),
                upload("2.txt", SampleFiles.txt()), upload("3.txt", SampleFiles.txt())), "admin1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A credit can have at most 2 files.");
    }

    @Test
    void add_beyondTotalSize_throwsConflict() {
        // given: total limit 1 MB, existing 1 MB - 1 byte
        CreditAttachmentServiceImpl tiny = serviceWith(new AttachmentProperties(tempDir.resolve("data"),
                DataSize.ofMegabytes(5), DataSize.ofMegabytes(1), 30));
        credit.attach(new AttachmentDraft("big.bin", "text/plain", 1024 * 1024 - 1, "0".repeat(64), "x"), 30,
                1024 * 1024);
        lockReturnsCredit();

        // when / then
        assertThatThrownBy(() -> tiny.add(1L, List.of(upload("a.txt", SampleFiles.txt())), "admin1"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageStartingWith("All files of a credit together may take at most 1 MB.");
    }

    @Test
    void add_missingCredit_throwsNotFound() {
        // given
        given(creditRepository.findByIdForUpdate(1L)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.add(1L, List.of(upload("a.txt", SampleFiles.txt())), "admin1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- delete ----

    @Test
    void delete_existingAttachment_removesRowAndFile() throws IOException {
        // given
        lockReturnsCredit();
        service.add(1L, List.of(upload("a.txt", SampleFiles.txt())), "admin1");
        ReflectionTestUtils.setField(credit.getAttachments().get(0), "id", 4L);

        // when
        service.delete(1L, 4L);

        // then
        assertThat(credit.getAttachments()).isEmpty();
        assertThat(filesOnDisk()).isZero();
    }

    @Test
    void delete_attachmentOfAnotherCredit_throwsNotFoundAndKeepsFiles() throws IOException {
        // given
        lockReturnsCredit();
        service.add(1L, List.of(upload("a.txt", SampleFiles.txt())), "admin1");

        // when / then
        assertThatThrownBy(() -> service.delete(1L, 999L)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(filesOnDisk()).isEqualTo(1);
    }
}
