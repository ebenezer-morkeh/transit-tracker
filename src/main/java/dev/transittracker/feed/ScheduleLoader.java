package dev.transittracker.feed;

import dev.transittracker.feed.FeedProperties.Feed;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Downloads each agency's static GTFS zip and stores its {@code stop_times.txt}. */
@Component
public class ScheduleLoader {

	private static final Logger log = LoggerFactory.getLogger(ScheduleLoader.class);

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	private final ScheduleStore store;

	private final List<Feed> feeds;

	ScheduleLoader(ScheduleStore store, FeedProperties properties) {
		this.store = store;
		this.feeds = properties.feeds();
	}

	/** On startup, then every {@code transit.static-refresh-interval}. */
	@Scheduled(initialDelay = 0, fixedDelayString = "${transit.static-refresh-interval}")
	public void scheduledLoad() {
		loadNow();
	}

	/** Loads every agency that has a static GTFS URL, one after another. A failure is logged and keeps that agency's previous schedule. */
	public void loadNow() {
		for (Feed feed : feeds) {
			if (feed.staticGtfsUrl() != null) {
				load(feed);
			}
		}
	}

	private void load(Feed feed) {
		long start = System.nanoTime();
		try {
			HttpRequest request = HttpRequest.newBuilder(feed.staticGtfsUrl()).timeout(Duration.ofSeconds(60)).build();
			HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() != 200) {
				throw new IllegalStateException("HTTP " + response.statusCode());
			}
			int rows = store.replaceAgency(feed.agencyId(), sink -> {
				try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response.body()))) {
					for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
						if (e.getName().equals("stop_times.txt") || e.getName().endsWith("/stop_times.txt")) {
							StopTimesParser.parse(zip, sink);
							return;
						}
					}
				}
				throw new IllegalStateException("zip has no stop_times.txt");
			});
			log.info("Loaded schedule agency={} stopTimes={} ms={}", feed.agencyId(), rows,
					(System.nanoTime() - start) / 1_000_000);
		}
		catch (IOException | RuntimeException e) {
			log.warn("Schedule load failed agency={} url={} reason={} (keeping previous schedule)", feed.agencyId(),
					feed.staticGtfsUrl(), e.toString());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
