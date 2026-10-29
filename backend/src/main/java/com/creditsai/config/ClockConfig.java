package com.creditsai.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the application {@link Clock} (system default zone) so that time-dependent code, such
 * as the PDF footer, can be tested with a fixed clock.
 */
@Configuration
public class ClockConfig {

    /**
     * Creates the clock used wherever "now" is needed.
     *
     * @return the system clock in the server's default time zone
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
