package com.creditsai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Switches on {@code @Scheduled} support (used by the attachment cleanup job).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
