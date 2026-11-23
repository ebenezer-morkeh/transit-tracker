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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fetches every agency's vehicle positions and (when configured) trip updates feeds in parallel, parses them and
 * saves the result.
 */
@Component
public class FeedPoller {

	private static final Logger log = LoggerFactory.getLogger(FeedPoller.class);

	private final HttpClient http = FeedHttp.client(Duration.ofSeconds(5));

	private final VehiclePositionStore vehicles;

	private final TripDelayStore delays;

	private final ScheduleStore schedule;

	private final List<Feed> feeds;

	private final VehicleStream stream;

	FeedPoller(VehiclePositionStore vehicles, TripDelayStore delays, ScheduleStore schedule, VehicleStream stream,
			FeedProperties properties) {
		this.vehicles = vehicles;
		this.delays = delays;
		this.schedule = schedule;
		this.stream = stream;
		this.feeds = properties.feeds();
	}

	@Scheduled(fixedDelayString = "${transit.poll-interval}")
	public void scheduledPoll() {
		pollOnce();
	}

	/**
	 * One poll cycle, now. Each feed succeeds or fails (logged) on its own; returns when all are done. If any agency's
	 * vehicles were saved, stream subscribers are then notified in the background.
	 */
	public void pollOnce() {
		List<CompletableFuture<Boolean>> vehiclePolls = new ArrayList<>();
		List<CompletableFuture<Boolean>> tripPolls = new ArrayList<>();
		for (Feed feed : feeds) {
			vehiclePolls.add(poll(feed, feed.vehiclePositionsUrl(), body -> {
				List<VehiclePosition> positions = FeedParser.parseVehiclePositions(body, feed.agencyId());
				vehicles.replaceAgency(feed.agencyId(), positions);
				return "vehicles=" + positions.size();
			}));
			if (feed.tripUpdatesUrl() != null) {
				tripPolls.add(poll(feed, feed.tripUpdatesUrl(), body -> {
					List<TripPrediction> predictions = FeedParser.parseTripUpdates(body, feed.agencyId());
					var scheduledStopTime = schedule.forTrips(feed.agencyId(),
							predictions.stream().map(TripPrediction::tripId).toList());
					List<TripDelay> tripDelays = predictions.stream()
						.map(p -> TripDelays.resolve(p, scheduledStopTime))
						.flatMap(Optional::stream)
						.toList();
					delays.replaceAgency(feed.agencyId(), tripDelays);
					return "trips=" + predictions.size() + " delays=" + tripDelays.size();
				}));
			}
		}
		CompletableFuture.allOf(tripPolls.toArray(CompletableFuture[]::new)).join();
		// toList() waits for every vehicle poll; anyMatch alone would stop at the first success.
		if (vehiclePolls.stream().map(CompletableFuture::join).toList().contains(true)) {
			stream.publish();
		}
	}

	/** Parses and saves one feed's body; returns what was saved, for the log. */
	private interface FeedHandler {

		String save(byte[] body) throws Exception;

	}

	/** Completes with whether the feed was fetched and saved; never exceptionally. */
	private CompletableFuture<Boolean> poll(Feed feed, URI url, FeedHandler handler) {
		HttpRequest request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(10)).build();
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
			try {
				log.info("Polled agency={} {}", feed.agencyId(), handler.save(FeedHttp.okBody(response)));
				return true;
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
			return false;
		});
	}

}
