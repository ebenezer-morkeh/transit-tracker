package dev.transittracker.feed;

import dev.transittracker.feed.FeedProperties.Feed;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fetches every agency's vehicle positions and (when configured) trip updates feeds in parallel, parses them and
 * saves the result.
 */
@Component
@EnableConfigurationProperties(FeedProperties.class)
public class FeedPoller {

	private static final Logger log = LoggerFactory.getLogger(FeedPoller.class);

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private final VehiclePositionStore store;

	private final TripDelayStore delays;

	private final ScheduleStore schedule;

	private final List<Feed> feeds;

	private final VehicleStream stream;

	FeedPoller(VehiclePositionStore store, TripDelayStore delays, ScheduleStore schedule, VehicleStream stream,
			FeedProperties properties) {
		this.store = store;
		this.delays = delays;
		this.schedule = schedule;
		this.stream = stream;
		this.feeds = properties.feeds();
	}

	@Scheduled(fixedDelayString = "${transit.poll-interval}")
	public void scheduledPoll() {
		pollOnce();
	}

	/** One poll cycle, now. Each feed succeeds or fails (logged) on its own; returns when all are done and stream subscribers are notified. */
	public void pollOnce() {
		List<CompletableFuture<Void>> polls = new ArrayList<>();
		for (Feed feed : feeds) {
			polls.add(poll(feed, feed.vehiclePositionsUrl(), body -> {
				List<VehiclePosition> positions = FeedParser.parseVehiclePositions(body, feed.agencyId());
				store.replaceAgency(feed.agencyId(), positions);
				return "vehicles=" + positions.size();
			}));
			if (feed.tripUpdatesUrl() != null) {
				polls.add(poll(feed, feed.tripUpdatesUrl(), body -> {
					List<TripPrediction> predictions = FeedParser.parseTripUpdates(body, feed.agencyId());
					List<TripDelay> found = predictions.stream()
						.map(p -> TripDelays.resolve(p,
								x -> schedule.find(x.agencyId(), x.tripId(), x.stopSequence(), x.stopId())))
						.flatMap(Optional::stream)
						.toList();
					delays.replaceAgency(feed.agencyId(), found);
					return "trips=" + predictions.size() + " delays=" + found.size();
				}));
			}
		}
		CompletableFuture.allOf(polls.toArray(CompletableFuture[]::new)).join();
		stream.publish();
	}

	/** Parses and saves one feed's body; returns what was saved, for the log. */
	private interface FeedHandler {

		String save(byte[] body) throws Exception;

	}

	private CompletableFuture<Void> poll(Feed feed, URI url, FeedHandler handler) {
		HttpRequest request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(10)).build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(response -> {
			if (response.statusCode() != 200) {
				throw new IllegalStateException("HTTP " + response.statusCode());
			}
			try {
				log.info("Polled agency={} {}", feed.agencyId(), handler.save(response.body()));
			}
			catch (Exception e) {
				throw new IllegalStateException(e);
			}
		}).exceptionally(e -> {
			Throwable cause = e;
			while (cause.getCause() != null) {
				cause = cause.getCause();
			}
			log.warn("Poll failed agency={} url={} reason={}", feed.agencyId(), url, cause.toString());
			return null;
		});
	}

}
