package dev.transittracker.ingest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** When each agency's trip updates feed last polled successfully. In memory: resets on restart. */
@Component
public class FeedStatus {

	/** Live data older than this is not live (8 missed polls). */
	public static final Duration LIVE_FOR = Duration.ofMinutes(2);

	private final Map<String, Instant> lastTripUpdates = new ConcurrentHashMap<>();

	private final Clock clock;

	FeedStatus(Clock clock) {
		this.clock = clock;
	}

	void tripUpdatesSucceeded(String agencyId) {
		lastTripUpdates.put(agencyId, clock.instant());
	}

	/** True when the agency's trip updates haven't polled successfully within {@link #LIVE_FOR}, or ever. */
	public boolean liveDataUnavailable(String agencyId) {
		Instant last = lastTripUpdates.get(agencyId);
		return last == null || last.isBefore(clock.instant().minus(LIVE_FOR));
	}

}
