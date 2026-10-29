package com.creditsai.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.creditsai.config.AttachmentProperties;
import static com.creditsai.model.AttachmentOwnerType.CLIENT;
import static com.creditsai.model.AttachmentOwnerType.CREDIT;

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
        long written = storage.store(CLIENT, 7, "report ü.txt", bytes("hello"));

        // then
        Path dir = tempDir.resolve("data/clients/7");
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
        storage.store(CLIENT, 7, "a.txt", bytes("x"));

        // then: umask can only remove bits, never add them
        Path dir = tempDir.resolve("data/clients/7");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.resolve("a.txt"))))
                .isIn("rw-r-----", "rw-------");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)))
                .isIn("rwxr-x---", "rwx------");
    }

    @Test
    void store_existingName_failsAndKeepsOriginal() throws IOException {
        // given
        storage.store(CLIENT, 7, "a.txt", bytes("first"));

        // when / then
        assertThatThrownBy(() -> storage.store(CLIENT, 7, "a.txt", bytes("second")))
                .isInstanceOf(com.creditsai.exception.BusinessRuleException.class)
                .hasMessage("A file named 'a.txt' already exists for this client.");
        assertThat(Files.readString(tempDir.resolve("data/clients/7/a.txt"))).isEqualTo("first");
        assertThat(filesIn(tempDir.resolve("data/clients/7"))).containsExactly("a.txt");
    }

    @Test
    void store_nameThatEscapesFolder_isRefusedWithoutLeakingPaths() {
        // when / then
        assertThatThrownBy(() -> storage.store(CLIENT, 7, "../evil.txt", bytes("x")))
                .isInstanceOf(AttachmentStorageException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(tempDir.toString()));
        assertThat(tempDir.resolve("data/clients/evil.txt")).doesNotExist();
    }

    @Test
    void store_rollback_removesTheFile() throws IOException {
        // given
        TransactionSynchronizationManager.initSynchronization();

        // when
        storage.store(CLIENT, 7, "a.txt", bytes("x"));
        assertThat(tempDir.resolve("data/clients/7/a.txt")).exists();
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        // then
        assertThat(filesIn(tempDir.resolve("data/clients/7"))).isEmpty();
    }

    @Test
    void store_commit_keepsTheFile() {
        // given
        TransactionSynchronizationManager.initSynchronization();

        // when
        storage.store(CLIENT, 7, "a.txt", bytes("x"));
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        // then
        assertThat(tempDir.resolve("data/clients/7/a.txt")).exists();
    }

    @Test
    void delete_withoutTransaction_removesFileAtOnce() {
        // given
        storage.store(CLIENT, 7, "a.txt", bytes("x"));

        // when
        storage.delete(CLIENT, 7, "a.txt");

        // then
        assertThat(tempDir.resolve("data/clients/7/a.txt")).doesNotExist();
    }

    @Test
    void delete_thenReuploadSameNameInSameTransaction_newFileSurvivesCommit() throws IOException {
        // given: admin A deletes x.pdf; the real name is free at once for admin B's upload
        storage.store(CLIENT, 7, "x.pdf", bytes("old"));
        TransactionSynchronizationManager.initSynchronization();
        storage.delete(CLIENT, 7, "x.pdf");
        assertThat(tempDir.resolve("data/clients/7/x.pdf")).doesNotExist();
        List<TransactionSynchronization> deleteTx = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();

        // when: B uploads x.pdf (own transaction), then A's commit callbacks run
        TransactionSynchronizationManager.initSynchronization();
        storage.store(CLIENT, 7, "x.pdf", bytes("new"));
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        deleteTx.forEach(TransactionSynchronization::afterCommit);
        deleteTx.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        // then: B's file is intact and no tombstone is left
        assertThat(Files.readString(tempDir.resolve("data/clients/7/x.pdf"))).isEqualTo("new");
        assertThat(filesIn(tempDir.resolve("data/clients/7"))).containsExactly("x.pdf");
    }

    @Test
    void delete_rolledBack_restoresRealNameAndLeavesNoTombstone() throws IOException {
        // given
        storage.store(CLIENT, 7, "a.txt", bytes("keep"));
        TransactionSynchronizationManager.initSynchronization();

        // when
        storage.delete(CLIENT, 7, "a.txt");
        assertThat(filesIn(tempDir.resolve("data/clients/7"))).hasSize(1).doesNotContain("a.txt");
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        // then
        assertThat(filesIn(tempDir.resolve("data/clients/7"))).containsExactly("a.txt");
        assertThat(Files.readString(tempDir.resolve("data/clients/7/a.txt"))).isEqualTo("keep");
    }

    @Test
    void delete_inTransaction_removesFileOnlyAfterCommit() {
        // given
        storage.store(CLIENT, 7, "a.txt", bytes("x"));
        TransactionSynchronizationManager.initSynchronization();

        // when
        storage.delete(CLIENT, 7, "a.txt");

        // then: set aside before commit (no tombstone left after commit)
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(tempDir.resolve("data/clients/7/a.txt")).doesNotExist();
        assertThat(tempDir.resolve("data/clients/7").toFile().list()).isEmpty();
    }

    @Test
    void delete_rolledBack_keepsFile() {
        // given
        storage.store(CLIENT, 7, "a.txt", bytes("x"));
        TransactionSynchronizationManager.initSynchronization();

        // when
        storage.delete(CLIENT, 7, "a.txt");
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        // then
        assertThat(tempDir.resolve("data/clients/7/a.txt")).exists();
    }

    @Test
    void load_storedFile_returnsReadableResource() throws IOException {
        // given
        storage.store(CLIENT, 7, "a.txt", bytes("hello"));

        // when
        Resource resource = storage.load(CLIENT, 7, "a.txt");

        // then
        assertThat(resource.exists()).isTrue();
        assertThat(new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void load_missingFile_returnsResourceThatDoesNotExist() {
        assertThat(storage.load(CLIENT, 7, "nope.txt").exists()).isFalse();
    }

    @Test
    void load_nameThatEscapesFolder_isRefused() {
        assertThatThrownBy(() -> storage.load(CLIENT, 7, "../8/a.txt")).isInstanceOf(AttachmentStorageException.class);
    }

    @Test
    void listClientIds_onlyNumericRealFolders_areReturnedSorted() throws IOException {
        // given
        storage.store(CLIENT, 10, "a.txt", bytes("x"));
        storage.store(CLIENT, 2, "a.txt", bytes("x"));
        Files.createDirectories(tempDir.resolve("data/clients/not-a-number"));
        Files.createDirectories(tempDir.resolve("outside"));
        Files.createSymbolicLink(tempDir.resolve("data/clients/99"), tempDir.resolve("outside"));
        Files.writeString(tempDir.resolve("data/clients/55"), "a file, not a folder");

        // when / then
        assertThat(storage.listOwnerIds(CLIENT)).containsExactly(2L, 10L);
    }

    @Test
    void listClientIds_storageDoesNotExist_returnsEmpty() {
        assertThat(storage.listOwnerIds(CLIENT)).isEmpty();
    }

    @Test
    void listFiles_returnsRegularFilesOnly() throws IOException {
        // given
        storage.store(CLIENT, 7, "a.txt", bytes("x"));
        Files.createDirectories(tempDir.resolve("data/clients/7/sub"));
        Files.createSymbolicLink(tempDir.resolve("data/clients/7/link"), tempDir.resolve("data/clients/7/a.txt"));

        // when / then
        assertThat(storage.listFiles(CLIENT, 7)).extracting(StoredFile::name).containsExactly("a.txt");
        assertThat(storage.listFiles(CLIENT, 8)).isEmpty();
    }

    @Test
    void deleteLeftover_realName_isRefused() throws IOException {
        // given
        storage.store(CLIENT, 7, "a.txt", bytes("x"));

        // when / then
        assertThatThrownBy(() -> storage.deleteLeftover(CLIENT, 7, "a.txt")).isInstanceOf(AttachmentStorageException.class);
        assertThat(tempDir.resolve("data/clients/7/a.txt")).exists();
    }

    @Test
    void deleteLeftover_tombstoneAndTemp_areRemoved() throws IOException {
        // given
        storage.store(CLIENT, 7, "a.txt", bytes("x"));
        Files.writeString(tempDir.resolve("data/clients/7/.deleted-1"), "x");
        Files.writeString(tempDir.resolve("data/clients/7/.upload-1.tmp"), "x");

        // when
        storage.deleteLeftover(CLIENT, 7, ".deleted-1");
        storage.deleteLeftover(CLIENT, 7, ".upload-1.tmp");

        // then
        assertThat(filesIn(tempDir.resolve("data/clients/7"))).containsExactly("a.txt");
    }

    @Test
    void store_creditOwner_writesUnderCreditsFolderIndependentOfClientWithSameId() throws IOException {
        // given / when
        storage.store(CREDIT, 7, "a.txt", bytes("credit"));
        storage.store(CLIENT, 7, "a.txt", bytes("client"));

        // then
        assertThat(Files.readString(tempDir.resolve("data/credits/7/a.txt"))).isEqualTo("credit");
        assertThat(Files.readString(tempDir.resolve("data/clients/7/a.txt"))).isEqualTo("client");
    }

    @Test
    void store_creditDuplicateName_conflictMentionsCredit() {
        // given
        storage.store(CREDIT, 7, "a.txt", bytes("x"));

        // when / then
        assertThatThrownBy(() -> storage.store(CREDIT, 7, "a.txt", bytes("y")))
                .isInstanceOf(com.creditsai.exception.BusinessRuleException.class)
                .hasMessageContaining("already exists for this credit");
    }

    @Test
    void listOwnerIds_credit_listsOnlyNumericFoldersOfCreditsRoot() throws IOException {
        // given
        storage.store(CREDIT, 3, "a.txt", bytes("x"));
        storage.store(CLIENT, 9, "a.txt", bytes("x"));
        Files.createDirectories(tempDir.resolve("data/credits/not-a-number"));

        // when / then
        assertThat(storage.listOwnerIds(CREDIT)).containsExactly(3L);
        assertThat(storage.listOwnerIds(CLIENT)).containsExactly(9L);
    }

    @Test
    void listFilesAndDeleteLeftover_credit_workInCreditFolder() throws IOException {
        // given
        storage.store(CREDIT, 4, "a.txt", bytes("x"));
        Files.writeString(tempDir.resolve("data/credits/4/.deleted-1"), "x");

        // when
        storage.deleteLeftover(CREDIT, 4, ".deleted-1");

        // then
        assertThat(storage.listFiles(CREDIT, 4)).extracting(StoredFile::name).containsExactly("a.txt");
        assertThat(storage.listFiles(CLIENT, 4)).isEmpty();
    }

    @Test
    void delete_creditFileInTransaction_removedAfterCommitRestoredAfterRollback() throws IOException {
        // given
        storage.store(CREDIT, 4, "a.txt", bytes("x"));
        TransactionSynchronizationManager.initSynchronization();
        storage.delete(CREDIT, 4, "a.txt");
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(tempDir.resolve("data/credits/4/a.txt")).exists();

        // when
        TransactionSynchronizationManager.initSynchronization();
        storage.delete(CREDIT, 4, "a.txt");
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        // then
        assertThat(filesIn(tempDir.resolve("data/credits/4"))).isEmpty();
    }

    @Test
    void migrateLegacyClientFolders_numericFolders_movedIntoClientsWithContent() throws IOException {
        // given
        Files.createDirectories(tempDir.resolve("data/5"));
        Files.writeString(tempDir.resolve("data/5/a.txt"), "five");
        Files.createDirectories(tempDir.resolve("data/12"));
        Files.writeString(tempDir.resolve("data/12/b.txt"), "twelve");
        Files.createDirectories(tempDir.resolve("data/credits/3"));
        Files.createDirectories(tempDir.resolve("data/other"));

        // when
        int moved = storage.migrateLegacyClientFolders();

        // then
        assertThat(moved).isEqualTo(2);
        assertThat(Files.readString(tempDir.resolve("data/clients/5/a.txt"))).isEqualTo("five");
        assertThat(Files.readString(tempDir.resolve("data/clients/12/b.txt"))).isEqualTo("twelve");
        assertThat(tempDir.resolve("data/5")).doesNotExist();
        assertThat(tempDir.resolve("data/12")).doesNotExist();
        assertThat(tempDir.resolve("data/credits/3")).exists();
        assertThat(tempDir.resolve("data/other")).exists();
    }

    @Test
    void migrateLegacyClientFolders_runTwice_secondRunMovesNothing() throws IOException {
        // given
        Files.createDirectories(tempDir.resolve("data/5"));
        Files.writeString(tempDir.resolve("data/5/a.txt"), "five");
        storage.migrateLegacyClientFolders();

        // when
        int second = storage.migrateLegacyClientFolders();

        // then
        assertThat(second).isZero();
        assertThat(Files.readString(tempDir.resolve("data/clients/5/a.txt"))).isEqualTo("five");
    }

    @Test
    void migrateLegacyClientFolders_targetExists_skipsAndOverwritesNothing() throws IOException {
        // given
        Files.createDirectories(tempDir.resolve("data/5"));
        Files.writeString(tempDir.resolve("data/5/old.txt"), "old");
        Files.createDirectories(tempDir.resolve("data/clients/5"));
        Files.writeString(tempDir.resolve("data/clients/5/new.txt"), "new");
        Files.createDirectories(tempDir.resolve("data/6"));

        // when
        int moved = storage.migrateLegacyClientFolders();

        // then
        assertThat(moved).isEqualTo(1);
        assertThat(Files.readString(tempDir.resolve("data/5/old.txt"))).isEqualTo("old");
        assertThat(filesIn(tempDir.resolve("data/clients/5"))).containsExactly("new.txt");
        assertThat(tempDir.resolve("data/clients/6")).exists();
    }

    @Test
    void migrateLegacyClientFolders_noStorageOrSymlink_isNoOp() throws IOException {
        // given: storage does not exist
        assertThat(storage.migrateLegacyClientFolders()).isZero();

        // given: a numeric symbolic link is not a real folder
        Files.createDirectories(tempDir.resolve("outside"));
        Files.createDirectories(tempDir.resolve("data"));
        Files.createSymbolicLink(tempDir.resolve("data/9"), tempDir.resolve("outside"));

        // when / then
        assertThat(storage.migrateLegacyClientFolders()).isZero();
        assertThat(tempDir.resolve("data/clients/9")).doesNotExist();
    }
}
