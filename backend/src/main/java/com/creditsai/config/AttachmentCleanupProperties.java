package com.creditsai.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the attachment storage cleanup job, bound from {@code app.attachments.cleanup.*}.
 *
 * @param interval how long to wait between the end of one run and the start of the next
 * @param minAge only files last modified longer ago than this are touched or reported, so uploads
 *        and deletes that are still in progress are never affected
 */
@ConfigurationProperties("app.attachments.cleanup")
public record AttachmentCleanupProperties(
        @DefaultValue("1h") Duration interval,
        @DefaultValue("15m") Duration minAge) {
}
