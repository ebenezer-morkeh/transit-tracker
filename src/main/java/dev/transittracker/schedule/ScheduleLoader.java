package dev.transittracker.schedule;

import dev.transittracker.ingest.FeedHttp;
import dev.transittracker.ingest.FeedProperties.Feed;
import dev.transittracker.ingest.FeedProperties;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Downloads each agency's static GTFS zip and stores its routes, stops, trips, calendars and stop times. */
@Component
public class ScheduleLoader {

	private static final Logger log = LoggerFactory.getLogger(ScheduleLoader.class);

	private final HttpClient http = FeedHttp.client(Duration.ofSeconds(10));

	private final ScheduleRepository store;

	private final List<Feed> feeds;

	ScheduleLoader(ScheduleRepository store, FeedProperties properties) {
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
			try (ZipFile zip = new ZipFile(zipPath.toFile())) {
				Map<String, Integer> counts = store.replaceAgency(feed.agencyId(), new ZipGtfs(feed.agencyId(), zip));
				log.info("Loaded schedule agency={} rows={} ms={}", feed.agencyId(), counts,
						(System.nanoTime() - start) / 1_000_000);
			}
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

	/** Reads each file straight from the zip; calendar files may be missing (GTFS requires only one of them). */
	private record ZipGtfs(String agencyId, ZipFile zip) implements ScheduleRepository.StaticGtfs {

		interface Parser<T> {

			void parse(InputStream in, Consumer<T> sink) throws IOException;

		}

		private <T> ScheduleRepository.Rows<T> file(String name, boolean required, Parser<T> parser) {
			return sink -> {
				ZipEntry entry = zip.stream()
					.filter(e -> e.getName().equals(name) || e.getName().endsWith("/" + name))
					.findFirst()
					.orElse(null);
				if (entry == null) {
					if (required) {
						throw new IllegalStateException("zip has no " + name);
					}
					return;
				}
				try (InputStream in = zip.getInputStream(entry)) {
					parser.parse(in, sink);
				}
			};
		}

		@Override
		public ScheduleRepository.Rows<Route> routes() {
			return file("routes.txt", true, (in, sink) -> GtfsFiles.routes(agencyId, in, sink));
		}

		@Override
		public ScheduleRepository.Rows<Stop> stops() {
			return file("stops.txt", true, GtfsFiles::stops);
		}

		@Override
		public ScheduleRepository.Rows<Trip> trips() {
			return file("trips.txt", true, GtfsFiles::trips);
		}

		@Override
		public ScheduleRepository.Rows<ServiceCalendar> calendars() {
			return file("calendar.txt", false, GtfsFiles::calendars);
		}

		@Override
		public ScheduleRepository.Rows<ServiceCalendarDate> calendarDates() {
			return file("calendar_dates.txt", false, GtfsFiles::calendarDates);
		}

		@Override
		public ScheduleRepository.Rows<ScheduledStopTime> stopTimes() {
			return file("stop_times.txt", true, StopTimesParser::parse);
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
