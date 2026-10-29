package com.creditsai.job;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.creditsai.dto.StorageCleanupReport;
import com.creditsai.service.AttachmentStorageCleanupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttachmentCleanupJobTest {

    @Mock
    private AttachmentStorageCleanupService service;

    @InjectMocks
    private AttachmentCleanupJob job;

    @Test
    void runAtStartup_callsTheService() {
        // given
        given(service.cleanup()).willReturn(new StorageCleanupReport(0, 0, 0, 0, 0, 0));

        // when
        job.runAtStartup();

        // then
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(service);
        order.verify(service).migrateLegacyLayout();
        order.verify(service).cleanup();
    }

    @Test
    void runAtStartup_migrationFails_cleanupStillRuns() {
        // given
        given(service.migrateLegacyLayout()).willThrow(new IllegalStateException("boom"));
        given(service.cleanup()).willReturn(new StorageCleanupReport(0, 0, 0, 0, 0, 0));

        // when
        job.runAtStartup();

        // then
        verify(service).cleanup();
    }

    @Test
    void runScheduled_doesNotMigrate() {
        // given
        given(service.cleanup()).willReturn(new StorageCleanupReport(0, 0, 0, 0, 0, 0));

        // when
        job.runScheduled();

        // then
        verify(service).cleanup();
        org.mockito.Mockito.verify(service, org.mockito.Mockito.never()).migrateLegacyLayout();
    }

    @Test
    void runScheduled_serviceThrows_neverEscapesIntoTheScheduler() {
        // given
        given(service.cleanup()).willThrow(new IllegalStateException("boom"));

        // when / then
        assertThatCode(job::runScheduled).doesNotThrowAnyException();
        assertThatCode(job::runAtStartup).doesNotThrowAnyException();
    }
}
