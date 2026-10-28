package com.creditsai.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Settings of the client file attachments, bound from {@code app.attachments.*}.
 *
 * @param storageDir the base directory; files live in {@code {storageDir}/{clientId}/{fileName}}
 * @param maxFileSize the largest allowed single file
 * @param maxTotalSize the largest allowed total size of all files of one client
 * @param maxFiles the largest allowed number of files per client
 */
@ConfigurationProperties("app.attachments")
public record AttachmentProperties(
        @DefaultValue("/data/attachments") Path storageDir,
        @DefaultValue("5MB") DataSize maxFileSize,
        @DefaultValue("20MB") DataSize maxTotalSize,
        @DefaultValue("30") int maxFiles) {
}
