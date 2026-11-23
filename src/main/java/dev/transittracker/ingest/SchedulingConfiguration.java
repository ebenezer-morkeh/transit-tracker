package dev.transittracker.ingest;

import dev.transittracker.schedule.ScheduleLoader;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Off in the test profile so tests drive {@link FeedPoller#pollOnce()} and {@link ScheduleLoader#loadNow()}
 * themselves.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "transit.scheduling.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
class SchedulingConfiguration {

}
