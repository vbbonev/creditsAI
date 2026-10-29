package com.creditsai.job;

import com.creditsai.service.AttachmentStorageCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Migrates the old attachment folder layout and triggers the attachment storage cleanup once when the application is ready and then repeatedly
 * with a fixed delay ({@code app.attachments.cleanup.interval}). It only starts the service and
 * makes sure no failure ever escapes into the scheduler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentCleanupJob {

    private final AttachmentStorageCleanupService cleanupService;

    /**
     * Moves the client folders of the old disk layout into place (once, idempotent) and only then
     * runs the cleanup, so the cleanup never sees the old layout.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void runAtStartup() {
        try {
            cleanupService.migrateLegacyLayout();
        } catch (RuntimeException e) {
            log.error("Attachment folder migration failed", e);
        }
        run();
    }

    /**
     * Runs the cleanup on schedule; the first scheduled run is one interval after startup because
     * the startup run has just happened.
     */
    @Scheduled(fixedDelayString = "${app.attachments.cleanup.interval:1h}",
            initialDelayString = "${app.attachments.cleanup.interval:1h}")
    public void runScheduled() {
        run();
    }

    /**
     * Runs the service and logs, instead of throwing, any unexpected failure.
     */
    private void run() {
        try {
            cleanupService.cleanup();
        } catch (RuntimeException e) {
            log.error("Attachment cleanup failed", e);
        }
    }
}
