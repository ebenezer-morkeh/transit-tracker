package dev.transittracker.feed;

import dev.transittracker.feed.FeedProperties.Feed;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Fetches every agency's vehicle positions feed in parallel, parses it and saves the result. */
@Component
@EnableConfigurationProperties(FeedProperties.class)
public class VehiclePoller {

	private static final Logger log = LoggerFactory.getLogger(VehiclePoller.class);

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private final VehiclePositionStore store;

	private final List<Feed> feeds;

	private final VehicleStream stream;

	VehiclePoller(VehiclePositionStore store, VehicleStream stream, FeedProperties properties) {
		this.store = store;
		this.stream = stream;
		this.feeds = properties.feeds();
	}

	@Scheduled(fixedDelayString = "${transit.poll-interval}")
	public void scheduledPoll() {
		pollOnce();
	}

	/** One poll cycle, now. Each agency succeeds or fails (logged) on its own; returns when all are done and stream subscribers are notified. */
	public void pollOnce() {
		CompletableFuture.allOf(feeds.stream().map(this::poll).toArray(CompletableFuture[]::new)).join();
		stream.publish();
	}

	private CompletableFuture<Void> poll(Feed feed) {
		HttpRequest request = HttpRequest.newBuilder(feed.vehiclePositionsUrl()).timeout(Duration.ofSeconds(10)).build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(response -> {
			if (response.statusCode() != 200) {
				throw new IllegalStateException("HTTP " + response.statusCode());
			}
			List<VehiclePosition> positions;
			try {
				positions = FeedParser.parseVehiclePositions(response.body(), feed.agencyId());
			}
			catch (Exception e) {
				throw new IllegalStateException("unparseable feed: " + e.getMessage(), e);
			}
			store.replaceAgency(feed.agencyId(), positions);
			log.info("Polled agency={} vehicles={}", feed.agencyId(), positions.size());
		}).exceptionally(e -> {
			Throwable cause = e.getCause() != null ? e.getCause() : e;
			log.warn("Poll failed agency={} url={} reason={}", feed.agencyId(), feed.vehiclePositionsUrl(), cause.toString());
			return null;
		});
	}

}
