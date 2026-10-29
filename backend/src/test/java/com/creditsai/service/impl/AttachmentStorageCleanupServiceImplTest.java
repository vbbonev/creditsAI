package com.creditsai.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.creditsai.config.AttachmentCleanupProperties;
import com.creditsai.config.AttachmentProperties;
import com.creditsai.dto.StorageCleanupReport;
import com.creditsai.exception.AttachmentStorageException;
import com.creditsai.model.AttachmentOwnerType;
import com.creditsai.repository.ClientAttachmentRepository;
import com.creditsai.repository.CreditAttachmentRepository;
import com.creditsai.storage.FileSystemAttachmentStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;

/** Real file-system storage in a temp dir, mocked repository. */
@ExtendWith(MockitoExtension.class)
class AttachmentStorageCleanupServiceImplTest {

    @TempDir
    Path tempDir;

    @Mock
    private ClientAttachmentRepository repository;

    @Mock
    private CreditAttachmentRepository creditRepository;

    private Path base;
    private FileSystemAttachmentStorage storage;
    private AttachmentStorageCleanupServiceImpl service;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() throws IOException {
        base = tempDir.resolve("data");
        Files.createDirectories(base);
        storage = spy(new FileSystemAttachmentStorage(
                new AttachmentProperties(base, DataSize.ofMegabytes(5), DataSize.ofMegabytes(20), 30)));
        service = new AttachmentStorageCleanupServiceImpl(storage, repository, creditRepository,
                new AttachmentCleanupProperties(Duration.ofHours(1), Duration.ofMinutes(15)));
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(AttachmentStorageCleanupServiceImpl.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(AttachmentStorageCleanupServiceImpl.class)).detachAppender(logs);
    }

    private Path file(String client, String name, Duration age) throws IOException {
        return file("clients", client, name, age);
    }

    private Path file(String root, String owner, String name, Duration age) throws IOException {
        Path dir = Files.createDirectories(base.resolve(root).resolve(owner));
        Path f = Files.writeString(dir.resolve(name), "x");
        Files.setLastModifiedTime(f, FileTime.from(Instant.now().minus(age)));
        return f;
    }

    private List<String> messages(Level level) {
        return logs.list.stream().filter(e -> e.getLevel() == level).map(ILoggingEvent::getFormattedMessage).toList();
    }

    private void rows(long clientId, String... names) {
        given(repository.findFileNamesByClientId(clientId)).willReturn(List.of(names));
        given(repository.findClientIdsWithAttachments()).willReturn(names.length == 0 ? List.of() : List.of(clientId));
    }

    @Test
    void cleanup_oldTombstoneAndTempFile_areDeletedAndLogged() throws IOException {
        // given
        Path tomb = file("1", ".deleted-abc", Duration.ofHours(1));
        Path temp = file("1", ".upload-xyz.tmp", Duration.ofHours(2));
        given(repository.findClientIdsWithAttachments()).willReturn(List.of());
        given(repository.findFileNamesByClientId(1L)).willReturn(List.of());

        // when
        StorageCleanupReport report = service.cleanup();

        // then
        assertThat(tomb).doesNotExist();
        assertThat(temp).doesNotExist();
        assertThat(report.leftoversRemoved()).isEqualTo(2);
        assertThat(messages(Level.INFO)).anyMatch(m -> m.equals(
                "Attachment cleanup: removed leftover file '.deleted-abc' of client 1"));
        assertThat(messages(Level.INFO)).anyMatch(m -> m.contains("Attachment cleanup finished: 1 clients and 0 credits checked, 2 leftovers removed"));
        assertThat(String.join("\n", messages(Level.INFO))).doesNotContain(tempDir.toString());
    }

    @Test
    void cleanup_freshTombstoneAndTempFile_areLeftAlone() throws IOException {
        // given
        Path tomb = file("1", ".deleted-abc", Duration.ofMinutes(1));
        Path temp = file("1", ".upload-xyz.tmp", Duration.ofMinutes(14));
        rows(1);
        given(repository.findFileNamesByClientId(1L)).willReturn(List.of());

        // when
        StorageCleanupReport report = service.cleanup();

        // then
        assertThat(tomb).exists();
        assertThat(temp).exists();
        assertThat(report.leftoversRemoved()).isZero();
    }

    @Test
    void cleanup_oldOrphanFile_isReportedAsWarningAndNotDeleted() throws IOException {
        // given: a real-named file without a row
        Path orphan = file("1", "orphan.pdf", Duration.ofHours(3));
        rows(1);
        given(repository.findFileNamesByClientId(1L)).willReturn(List.of());

        // when
        StorageCleanupReport report = service.cleanup();

        // then
        assertThat(orphan).exists();
        assertThat(report.orphanFiles()).isEqualTo(1);
        assertThat(messages(Level.WARN)).containsExactly(
                "Attachment cleanup: file 'orphan.pdf' of client 1 has no database row (not deleted)");
    }

    @Test
    void cleanup_freshOrphanFile_isNotReported() throws IOException {
        // given: an upload that is not committed yet
        file("1", "new.pdf", Duration.ofMinutes(1));
        given(repository.findClientIdsWithAttachments()).willReturn(List.of());
        given(repository.findFileNamesByClientId(1L)).willReturn(List.of());

        // when / then
        assertThat(service.cleanup().orphanFiles()).isZero();
        assertThat(messages(Level.WARN)).isEmpty();
    }

    @Test
    void cleanup_rowWithoutFile_isReportedEvenWithoutAClientFolder() throws IOException {
        // given: client 1 has a folder with one of two files, client 2 has a row but no folder
        file("1", "here.pdf", Duration.ofHours(3));
        given(repository.findClientIdsWithAttachments()).willReturn(List.of(1L, 2L));
        given(repository.findFileNamesByClientId(1L)).willReturn(List.of("here.pdf", "gone.pdf"));
        given(repository.findFileNamesByClientId(2L)).willReturn(List.of("lost.pdf"));

        // when
        StorageCleanupReport report = service.cleanup();

        // then
        assertThat(report.missingFiles()).isEqualTo(2);
        assertThat(report.orphanFiles()).isZero();
        assertThat(messages(Level.WARN)).containsExactly(
                "Attachment cleanup: database row for file 'gone.pdf' of client 1 has no file",
                "Attachment cleanup: database row for file 'lost.pdf' of client 2 has no file");
    }

    @Test
    void cleanup_pendingDelete_doesNotReportItsFileAsMissing() throws IOException {
        // given: a delete in progress set the file aside; the row is still visible
        file("1", ".deleted-abc", Duration.ofSeconds(5));
        rows(1, "x.pdf");

        // when / then
        assertThat(service.cleanup().missingFiles()).isZero();
    }

    @Test
    void cleanup_nonNumericFolderAndSymlinkedFolder_areIgnored() throws IOException {
        // given
        Path other = file("not-a-client", ".deleted-abc", Duration.ofHours(5));
        Path outside = Files.createDirectories(tempDir.resolve("outside"));
        Path outsideLeftover = Files.writeString(outside.resolve(".deleted-zzz"), "x");
        Files.setLastModifiedTime(outsideLeftover, FileTime.from(Instant.now().minus(Duration.ofDays(1))));
        Files.createDirectories(base.resolve("clients"));
        Files.createSymbolicLink(base.resolve("clients").resolve("7"), outside);
        given(repository.findClientIdsWithAttachments()).willReturn(List.of());

        // when
        StorageCleanupReport report = service.cleanup();

        // then
        assertThat(other).exists();
        assertThat(outsideLeftover).exists();
        assertThat(report.clientsChecked()).isZero();
    }

    @Test
    void cleanup_symlinkedFileInsideClientFolder_isNotFollowedOrDeleted() throws IOException {
        // given
        Path target = Files.writeString(tempDir.resolve("secret.txt"), "secret");
        Files.createDirectories(base.resolve("clients/1"));
        Path link = Files.createSymbolicLink(base.resolve("clients/1/.deleted-link"), target);
        Files.setLastModifiedTime(target, FileTime.from(Instant.now().minus(Duration.ofDays(1))));
        given(repository.findClientIdsWithAttachments()).willReturn(List.of());
        given(repository.findFileNamesByClientId(1L)).willReturn(List.of());

        // when
        service.cleanup();

        // then
        assertThat(target).exists();
        assertThat(Files.isSymbolicLink(link)).isTrue();
    }

    @Test
    void cleanup_ioErrorOnOneFile_doesNotStopTheRun() throws IOException {
        // given: two old tombstones in client 1 and one in client 2; deleting one fails
        file("1", ".deleted-a", Duration.ofHours(1));
        Path second = file("1", ".deleted-b", Duration.ofHours(1));
        Path other = file("2", ".deleted-c", Duration.ofHours(1));
        given(repository.findClientIdsWithAttachments()).willReturn(List.of());
        given(repository.findFileNamesByClientId(anyLong())).willReturn(List.of());
        lenient().doThrow(new AttachmentStorageException("boom")).when(storage).deleteLeftover(eq(AttachmentOwnerType.CLIENT), eq(1L), eq(".deleted-a"));

        // when
        StorageCleanupReport report = service.cleanup();

        // then
        assertThat(report.errors()).isEqualTo(1);
        assertThat(report.leftoversRemoved()).isEqualTo(2);
        assertThat(second).doesNotExist();
        assertThat(other).doesNotExist();
        assertThat(messages(Level.ERROR)).singleElement().asString().contains("'.deleted-a' of client 1");
    }

    @Test
    void cleanup_failureCheckingOneClient_isLoggedAndOthersAreStillChecked() throws IOException {
        // given
        file("1", "a.pdf", Duration.ofHours(3));
        file("2", "b.pdf", Duration.ofHours(3));
        given(repository.findClientIdsWithAttachments()).willReturn(List.of());
        given(repository.findFileNamesByClientId(1L)).willThrow(new IllegalStateException("db down"));
        given(repository.findFileNamesByClientId(2L)).willReturn(List.of());

        // when
        StorageCleanupReport report = service.cleanup();

        // then
        assertThat(report.errors()).isEqualTo(1);
        assertThat(report.orphanFiles()).isEqualTo(1);
    }

    @Test
    void cleanup_storageMissingEntirely_finishesWithZeroCounts() throws IOException {
        // given
        Files.delete(base);
        given(repository.findClientIdsWithAttachments()).willReturn(List.of());

        // when / then
        assertThat(service.cleanup()).isEqualTo(new StorageCleanupReport(0, 0, 0, 0, 0, 0));
    }

    @Test
    void cleanup_creditFolders_oldLeftoversRemovedOrphansReportedAndRowsWithoutFileReported() throws IOException {
        // given: credit 5 has an old tombstone, an old orphan and a file with a row; credit 6 has a row but no folder
        Path tomb = file("credits", "5", ".deleted-abc", Duration.ofHours(1));
        Path temp = file("credits", "5", ".upload-xyz.tmp", Duration.ofHours(2));
        Path orphan = file("credits", "5", "orphan.pdf", Duration.ofHours(3));
        file("credits", "5", "here.pdf", Duration.ofHours(3));
        given(repository.findClientIdsWithAttachments()).willReturn(List.of());
        given(creditRepository.findCreditIdsWithAttachments()).willReturn(List.of(5L, 6L));
        given(creditRepository.findFileNamesByCreditId(5L)).willReturn(List.of("here.pdf", "gone.pdf"));
        given(creditRepository.findFileNamesByCreditId(6L)).willReturn(List.of("lost.pdf"));

        // when
        StorageCleanupReport report = service.cleanup();

        // then
        assertThat(tomb).doesNotExist();
        assertThat(temp).doesNotExist();
        assertThat(orphan).exists();
        assertThat(report.creditsChecked()).isEqualTo(2);
        assertThat(report.clientsChecked()).isZero();
        assertThat(report.leftoversRemoved()).isEqualTo(2);
        assertThat(report.orphanFiles()).isEqualTo(1);
        assertThat(report.missingFiles()).isEqualTo(2);
        assertThat(messages(Level.WARN)).contains(
                "Attachment cleanup: file 'orphan.pdf' of credit 5 has no database row (not deleted)",
                "Attachment cleanup: database row for file 'lost.pdf' of credit 6 has no file");
    }

    @Test
    void cleanup_clientAndCreditWithSameId_areCheckedSeparately() throws IOException {
        // given: client 1 and credit 1 each own one file with a row
        file("clients", "1", "c.pdf", Duration.ofHours(3));
        file("credits", "1", "d.pdf", Duration.ofHours(3));
        given(repository.findClientIdsWithAttachments()).willReturn(List.of(1L));
        given(repository.findFileNamesByClientId(1L)).willReturn(List.of("c.pdf"));
        given(creditRepository.findCreditIdsWithAttachments()).willReturn(List.of(1L));
        given(creditRepository.findFileNamesByCreditId(1L)).willReturn(List.of("d.pdf"));

        // when
        StorageCleanupReport report = service.cleanup();

        // then
        assertThat(report).isEqualTo(new StorageCleanupReport(1, 1, 0, 0, 0, 0));
    }

    @Test
    void cleanup_creditRowsUnreadable_isCountedAndClientsStillChecked() throws IOException {
        // given
        file("clients", "1", "a.pdf", Duration.ofHours(3));
        given(repository.findClientIdsWithAttachments()).willReturn(List.of());
        given(repository.findFileNamesByClientId(1L)).willReturn(List.of());
        given(creditRepository.findCreditIdsWithAttachments()).willThrow(new IllegalStateException("db down"));

        // when
        StorageCleanupReport report = service.cleanup();

        // then
        assertThat(report.errors()).isEqualTo(1);
        assertThat(report.orphanFiles()).isEqualTo(1);
    }

    @Test
    void migrateLegacyLayout_oldClientFolder_isMovedIntoClients() throws IOException {
        // given
        Files.createDirectories(base.resolve("4"));
        Files.writeString(base.resolve("4/a.txt"), "x");

        // when
        int moved = service.migrateLegacyLayout();

        // then
        assertThat(moved).isEqualTo(1);
        assertThat(base.resolve("clients/4/a.txt")).exists();
        assertThat(base.resolve("4")).doesNotExist();
    }
}
