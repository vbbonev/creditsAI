package com.creditsai;

import static org.assertj.core.api.Assertions.assertThat;

import com.creditsai.config.AttachmentCleanupProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

/**
 * Checks that the cleanup settings bind and the job is registered with the configured interval.
 * The startup run does not disturb other integration tests: it only deletes leftovers older than
 * min-age (15 minutes) and otherwise only logs, and the first scheduled run is an hour later.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AttachmentCleanupSchedulingTest {

    @Autowired
    private AttachmentCleanupProperties properties;

    @Autowired
    private ScheduledTaskHolder scheduledTasks;

    @Test
    void properties_bindDefaultsFromApplicationYml() {
        assertThat(properties.interval()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.minAge()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void scheduling_cleanupJobIsRegisteredWithTheConfiguredInterval() {
        assertThat(scheduledTasks.getScheduledTasks()).anySatisfy(task -> {
            assertThat(task.getTask()).isInstanceOf(FixedDelayTask.class);
            FixedDelayTask delay = (FixedDelayTask) task.getTask();
            assertThat(delay.getIntervalDuration()).isEqualTo(Duration.ofHours(1));
            assertThat(delay.getInitialDelayDuration()).isEqualTo(Duration.ofHours(1));
            assertThat(delay.getRunnable().toString()).contains("AttachmentCleanupJob.runScheduled");
        });
    }
}
