package dev.transittracker.feed;

import dev.transittracker.feed.FeedProperties.Feed;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Downloads each agency's static GTFS zip and stores its {@code stop_times.txt}. */
@Component
public class ScheduleLoader {

	private static final Logger log = LoggerFactory.getLogger(ScheduleLoader.class);

	private final HttpClient http = FeedHttp.client(Duration.ofSeconds(10));

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
		Path download = null;
		try {
			// To disk, not memory: GoRaleigh's zip is tens of MB.
			download = Files.createTempFile("gtfs-" + feed.agencyId() + "-", ".zip");
			HttpRequest request = HttpRequest.newBuilder(feed.staticGtfsUrl()).timeout(Duration.ofSeconds(60)).build();
			Path zipPath = FeedHttp.okBody(http.send(request, HttpResponse.BodyHandlers.ofFile(download)));
			int rows = store.replaceAgency(feed.agencyId(), sink -> {
				try (ZipFile zip = new ZipFile(zipPath.toFile())) {
					ZipEntry stopTimes = zip.stream()
						.filter(e -> e.getName().equals("stop_times.txt") || e.getName().endsWith("/stop_times.txt"))
						.findFirst()
						.orElseThrow(() -> new IllegalStateException("zip has no stop_times.txt"));
					try (InputStream in = zip.getInputStream(stopTimes)) {
						StopTimesParser.parse(in, sink);
					}
				}
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
		finally {
			deleteQuietly(download);
		}
	}

	private static void deleteQuietly(Path file) {
		if (file == null) {
			return;
		}
		try {
			Files.deleteIfExists(file);
		}
		catch (IOException e) {
			log.warn("Could not delete {}: {}", file, e.toString());
		}
	}

}
