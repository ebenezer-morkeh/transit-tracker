package dev.transittracker.feed;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Fetches the vehicle positions feed, parses it and saves the result. */
@Component
public class VehiclePoller {

	private static final Logger log = LoggerFactory.getLogger(VehiclePoller.class);

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private final VehiclePositionStore store;

	// shortcut: one feed from flat properties; ticket 04 turns this into a list of agencies polled in parallel.
	private final String agencyId;

	private final URI vehiclePositionsUrl;

	VehiclePoller(VehiclePositionStore store, @Value("${transit.feed.agency-id}") String agencyId,
			@Value("${transit.feed.vehicle-positions-url}") URI vehiclePositionsUrl) {
		this.store = store;
		this.agencyId = agencyId;
		this.vehiclePositionsUrl = vehiclePositionsUrl;
	}

	@Scheduled(fixedDelayString = "${transit.poll-interval}")
	public void scheduledPoll() {
		try {
			pollOnce();
		}
		catch (Exception e) {
			log.warn("Poll failed agency={} url={}", agencyId, vehiclePositionsUrl, e);
		}
	}

	/** One poll cycle, now. Throws on fetch or parse failure; the scheduled path logs and carries on. */
	public void pollOnce() throws Exception {
		HttpResponse<byte[]> response = http.send(
				HttpRequest.newBuilder(vehiclePositionsUrl).timeout(Duration.ofSeconds(10)).build(),
				HttpResponse.BodyHandlers.ofByteArray());
		if (response.statusCode() != 200) {
			throw new IllegalStateException("HTTP " + response.statusCode() + " from " + vehiclePositionsUrl);
		}
		List<VehiclePosition> positions = FeedParser.parseVehiclePositions(response.body(), agencyId);
		store.upsert(positions);
		log.info("Polled agency={} vehicles={}", agencyId, positions.size());
	}

}
