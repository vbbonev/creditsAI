package com.creditsai.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.creditsai.config.AttachmentProperties;
import com.creditsai.exception.AttachmentStorageException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;

class FileSystemAttachmentStorageTest {

    @TempDir
    Path tempDir;

    private FileSystemAttachmentStorage storage;

    @BeforeEach
    void setUp() {
        storage = new FileSystemAttachmentStorage(
                new AttachmentProperties(tempDir.resolve("data"), DataSize.ofMegabytes(5), DataSize.ofMegabytes(20), 30));
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static ByteArrayInputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private List<String> filesIn(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).toList();
        }
    }

    private void completeTransaction(int status) {
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        if (status == TransactionSynchronization.STATUS_COMMITTED) {
            syncs.forEach(TransactionSynchronization::afterCommit);
        }
        syncs.forEach(s -> s.afterCompletion(status));
    }

    @Test
    void store_newFile_writesItUnderClientFolderWithRealNameAndNoTempLeft() throws IOException {
        // when
        long written = storage.store(7, "report ü.txt", bytes("hello"));

        // then
        Path dir = tempDir.resolve("data/7");
        assertThat(written).isEqualTo(5);
        assertThat(filesIn(dir)).containsExactly("report ü.txt");
        assertThat(Files.readString(dir.resolve("report ü.txt"))).isEqualTo("hello");
    }

    @Test
    void store_onPosix_usesRestrictivePermissions() throws IOException {
        // given
        org.junit.jupiter.api.Assumptions.assumeTrue(
                FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));

        // when
        storage.store(7, "a.txt", bytes("x"));

        // then: umask can only remove bits, never add them
        Path dir = tempDir.resolve("data/7");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.resolve("a.txt"))))
                .isIn("rw-r-----", "rw-------");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)))
                .isIn("rwxr-x---", "rwx------");
    }

    @Test
    void store_existingName_failsAndKeepsOriginal() throws IOException {
        // given
        storage.store(7, "a.txt", bytes("first"));

        // when / then
        assertThatThrownBy(() -> storage.store(7, "a.txt", bytes("second")))
                .isInstanceOf(com.creditsai.exception.BusinessRuleException.class)
                .hasMessage("A file named 'a.txt' already exists for this client.");
        assertThat(Files.readString(tempDir.resolve("data/7/a.txt"))).isEqualTo("first");
        assertThat(filesIn(tempDir.resolve("data/7"))).containsExactly("a.txt");
    }

    @Test
    void store_nameThatEscapesFolder_isRefusedWithoutLeakingPaths() {
        // when / then
        assertThatThrownBy(() -> storage.store(7, "../evil.txt", bytes("x")))
                .isInstanceOf(AttachmentStorageException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(tempDir.toString()));
        assertThat(tempDir.resolve("data/evil.txt")).doesNotExist();
    }

    @Test
    void store_rollback_removesTheFile() throws IOException {
        // given
        TransactionSynchronizationManager.initSynchronization();

        // when
        storage.store(7, "a.txt", bytes("x"));
        assertThat(tempDir.resolve("data/7/a.txt")).exists();
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        // then
        assertThat(filesIn(tempDir.resolve("data/7"))).isEmpty();
    }

    @Test
    void store_commit_keepsTheFile() {
        // given
        TransactionSynchronizationManager.initSynchronization();

        // when
        storage.store(7, "a.txt", bytes("x"));
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        // then
        assertThat(tempDir.resolve("data/7/a.txt")).exists();
    }

    @Test
    void delete_withoutTransaction_removesFileAtOnce() {
        // given
        storage.store(7, "a.txt", bytes("x"));

        // when
        storage.delete(7, "a.txt");

        // then
        assertThat(tempDir.resolve("data/7/a.txt")).doesNotExist();
    }

    @Test
    void delete_thenReuploadSameNameInSameTransaction_newFileSurvivesCommit() throws IOException {
        // given: admin A deletes x.pdf; the real name is free at once for admin B's upload
        storage.store(7, "x.pdf", bytes("old"));
        TransactionSynchronizationManager.initSynchronization();
        storage.delete(7, "x.pdf");
        assertThat(tempDir.resolve("data/7/x.pdf")).doesNotExist();
        List<TransactionSynchronization> deleteTx = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();

        // when: B uploads x.pdf (own transaction), then A's commit callbacks run
        TransactionSynchronizationManager.initSynchronization();
        storage.store(7, "x.pdf", bytes("new"));
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        deleteTx.forEach(TransactionSynchronization::afterCommit);
        deleteTx.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        // then: B's file is intact and no tombstone is left
        assertThat(Files.readString(tempDir.resolve("data/7/x.pdf"))).isEqualTo("new");
        assertThat(filesIn(tempDir.resolve("data/7"))).containsExactly("x.pdf");
    }

    @Test
    void delete_rolledBack_restoresRealNameAndLeavesNoTombstone() throws IOException {
        // given
        storage.store(7, "a.txt", bytes("keep"));
        TransactionSynchronizationManager.initSynchronization();

        // when
        storage.delete(7, "a.txt");
        assertThat(filesIn(tempDir.resolve("data/7"))).hasSize(1).doesNotContain("a.txt");
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        // then
        assertThat(filesIn(tempDir.resolve("data/7"))).containsExactly("a.txt");
        assertThat(Files.readString(tempDir.resolve("data/7/a.txt"))).isEqualTo("keep");
    }

    @Test
    void delete_inTransaction_removesFileOnlyAfterCommit() {
        // given
        storage.store(7, "a.txt", bytes("x"));
        TransactionSynchronizationManager.initSynchronization();

        // when
        storage.delete(7, "a.txt");

        // then: set aside before commit (no tombstone left after commit)
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(tempDir.resolve("data/7/a.txt")).doesNotExist();
        assertThat(tempDir.resolve("data/7").toFile().list()).isEmpty();
    }

    @Test
    void delete_rolledBack_keepsFile() {
        // given
        storage.store(7, "a.txt", bytes("x"));
        TransactionSynchronizationManager.initSynchronization();

        // when
        storage.delete(7, "a.txt");
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        // then
        assertThat(tempDir.resolve("data/7/a.txt")).exists();
    }

    @Test
    void load_storedFile_returnsReadableResource() throws IOException {
        // given
        storage.store(7, "a.txt", bytes("hello"));

        // when
        Resource resource = storage.load(7, "a.txt");

        // then
        assertThat(resource.exists()).isTrue();
        assertThat(new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void load_missingFile_returnsResourceThatDoesNotExist() {
        assertThat(storage.load(7, "nope.txt").exists()).isFalse();
    }

    @Test
    void load_nameThatEscapesFolder_isRefused() {
        assertThatThrownBy(() -> storage.load(7, "../8/a.txt")).isInstanceOf(AttachmentStorageException.class);
    }

    @Test
    void listClientIds_onlyNumericRealFolders_areReturnedSorted() throws IOException {
        // given
        storage.store(10, "a.txt", bytes("x"));
        storage.store(2, "a.txt", bytes("x"));
        Files.createDirectories(tempDir.resolve("data/not-a-number"));
        Files.createDirectories(tempDir.resolve("outside"));
        Files.createSymbolicLink(tempDir.resolve("data/99"), tempDir.resolve("outside"));
        Files.writeString(tempDir.resolve("data/55"), "a file, not a folder");

        // when / then
        assertThat(storage.listClientIds()).containsExactly(2L, 10L);
    }

    @Test
    void listClientIds_storageDoesNotExist_returnsEmpty() {
        assertThat(storage.listClientIds()).isEmpty();
    }

    @Test
    void listFiles_returnsRegularFilesOnly() throws IOException {
        // given
        storage.store(7, "a.txt", bytes("x"));
        Files.createDirectories(tempDir.resolve("data/7/sub"));
        Files.createSymbolicLink(tempDir.resolve("data/7/link"), tempDir.resolve("data/7/a.txt"));

        // when / then
        assertThat(storage.listFiles(7)).extracting(StoredFile::name).containsExactly("a.txt");
        assertThat(storage.listFiles(8)).isEmpty();
    }

    @Test
    void deleteLeftover_realName_isRefused() throws IOException {
        // given
        storage.store(7, "a.txt", bytes("x"));

        // when / then
        assertThatThrownBy(() -> storage.deleteLeftover(7, "a.txt")).isInstanceOf(AttachmentStorageException.class);
        assertThat(tempDir.resolve("data/7/a.txt")).exists();
    }

    @Test
    void deleteLeftover_tombstoneAndTemp_areRemoved() throws IOException {
        // given
        storage.store(7, "a.txt", bytes("x"));
        Files.writeString(tempDir.resolve("data/7/.deleted-1"), "x");
        Files.writeString(tempDir.resolve("data/7/.upload-1.tmp"), "x");

        // when
        storage.deleteLeftover(7, ".deleted-1");
        storage.deleteLeftover(7, ".upload-1.tmp");

        // then
        assertThat(filesIn(tempDir.resolve("data/7"))).containsExactly("a.txt");
    }
}
