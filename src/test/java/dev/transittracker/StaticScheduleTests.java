package dev.transittracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.transittracker.feed.ScheduleLoader;
import dev.transittracker.feed.ScheduleStore;
import dev.transittracker.feed.ScheduledStopTime;
import dev.transittracker.feed.TripPrediction;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class StaticScheduleTests {

	/** Response body by path; a missing path is a 404. */
	static final Map<String, byte[]> files = new ConcurrentHashMap<>();

	static final HttpServer server = start();

	@Autowired
	ScheduleLoader loader;

	@Autowired
	ScheduleStore schedule;

	static HttpServer start() {
		try {
			HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			s.createContext("/", exchange -> {
				String path = exchange.getRequestURI().getPath();
				if (path.startsWith("/redirect")) { // the real agency URLs all redirect
					exchange.getResponseHeaders().add("Location", path.substring("/redirect".length()));
					exchange.sendResponseHeaders(302, -1);
				}
				else if (files.containsKey(path)) {
					exchange.sendResponseHeaders(200, files.get(path).length);
					exchange.getResponseBody().write(files.get(path));
				}
				else {
					exchange.sendResponseHeaders(404, -1);
				}
				exchange.close();
			});
			s.start();
			return s;
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void feeds(DynamicPropertyRegistry registry) {
		String base = "http://127.0.0.1:" + server.getAddress().getPort();
		registry.add("transit.feeds[0].agency-id", () -> "goraleigh");
		registry.add("transit.feeds[0].vehicle-positions-url", () -> base + "/goraleigh/vehicles");
		registry.add("transit.feeds[0].static-gtfs-url", () -> base + "/redirect/goraleigh/gtfs.zip");
		registry.add("transit.feeds[1].agency-id", () -> "godurham");
		registry.add("transit.feeds[1].vehicle-positions-url", () -> base + "/godurham/vehicles");
		registry.add("transit.feeds[1].static-gtfs-url", () -> base + "/godurham/gtfs.zip");
		registry.add("transit.feeds[2].agency-id", () -> "norealtimeschedule");
		registry.add("transit.feeds[2].vehicle-positions-url", () -> base + "/x/vehicles");
	}

	@AfterAll
	static void stop() {
		server.stop(0);
	}

	static byte[] gtfsZip(String stopTimes) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("agency.txt"));
			zip.write("agency_id,agency_name\nx,X\n".getBytes(StandardCharsets.UTF_8));
			zip.putNextEntry(new ZipEntry("stop_times.txt"));
			zip.write(stopTimes.getBytes(StandardCharsets.UTF_8));
		}
		return bytes.toByteArray();
	}

	/** The scheduled stop time for a prediction at {@code stopSequence} / {@code stopId} (either may be null). */
	Optional<ScheduledStopTime> find(String agencyId, String tripId, Integer stopSequence, String stopId) {
		return schedule.forTrips(agencyId, List.of(tripId)).apply(prediction(agencyId, tripId, stopSequence, stopId));
	}

	static TripPrediction prediction(String agencyId, String tripId, Integer stopSequence, String stopId) {
		return new TripPrediction(agencyId, tripId, "1", null, null, stopSequence, stopId, Instant.EPOCH, false, null,
				null, Instant.EPOCH);
	}

	static final String HEADER = "trip_id,arrival_time,departure_time,stop_id,stop_sequence\r\n";

	@Test
	void loadsEachAgencyAndAFailedLoadKeepsThePreviousSchedule() throws Exception {
		files.put("/goraleigh/gtfs.zip", gtfsZip(HEADER + "T1,08:00:00,08:00:30,S1,1\r\nT1,24:10:00,24:10:00,S2,2\r\n"));
		files.put("/godurham/gtfs.zip", gtfsZip(HEADER + "T1,09:00:00,09:00:00,S1,1\r\n"));
		loader.loadNow();

		ScheduledStopTime raleighStop2 = new ScheduledStopTime("T1", 2, "S2", 87000, 87000);
		assertThat(find("goraleigh", "T1", 2, null)).contains(raleighStop2);
		assertThat(find("goraleigh", "T1", null, "S2")).contains(raleighStop2);
		assertThat(find("goraleigh", "T1", 99, "S2")).as("unknown sequence falls back to stop").contains(raleighStop2);
		assertThat(find("goraleigh", "T1", 99, null)).isEmpty();
		assertThat(find("goraleigh", "nope", 1, "S1")).isEmpty();
		assertThat(find("godurham", "T1", 1, null)).contains(new ScheduledStopTime("T1", 1, "S1", 32400, 32400));

		// GoRaleigh's zip goes bad; GoDurham publishes a new schedule that replaces its old one wholesale.
		files.put("/goraleigh/gtfs.zip", new byte[] { 'P', 'K', 3, 4, 9, 9, 9 });
		files.put("/godurham/gtfs.zip", gtfsZip(HEADER + "T2,10:00:00,10:00:00,S1,1\r\n"));
		loader.loadNow();

		assertThat(find("goraleigh", "T1", 2, null)).contains(raleighStop2);
		assertThat(find("godurham", "T1", 1, null)).isEmpty();
		assertThat(find("godurham", "T2", 1, null)).isPresent();

		// A zip with a bad row half way through is rejected as a whole, and so is a download error.
		files.put("/goraleigh/gtfs.zip", gtfsZip(HEADER + "T9,01:00:00,01:00:00,S1,1\r\nT9,oops,,S2,2\r\n"));
		files.remove("/godurham/gtfs.zip");
		loader.loadNow();

		assertThat(find("goraleigh", "T9", 1, null)).isEmpty();
		assertThat(find("goraleigh", "T1", 2, null)).contains(raleighStop2);
		assertThat(find("godurham", "T2", 1, null)).isPresent();

		// A zip whose stop_times.txt has no rows is a failed load too.
		files.put("/goraleigh/gtfs.zip", gtfsZip(HEADER));
		loader.loadNow();

		assertThat(find("goraleigh", "T1", 2, null)).contains(raleighStop2);
	}

	@Test
	void oneLookupResolvesEveryTripItWasGiven() throws Exception {
		files.put("/goraleigh/gtfs.zip",
				gtfsZip(HEADER + "A,08:00:00,08:00:00,S1,1\r\nA,08:10:00,08:10:00,S1,2\r\nB,09:00:00,09:00:00,S1,1\r\n"));
		loader.loadNow();

		var lookup = schedule.forTrips("goraleigh", List.of("A", "B", "missing"));
		assertThat(lookup.apply(prediction("goraleigh", "A", 2, null)))
			.contains(new ScheduledStopTime("A", 2, "S1", 29400, 29400));
		assertThat(lookup.apply(prediction("goraleigh", "A", null, "S1"))).as("first visit of a repeated stop")
			.contains(new ScheduledStopTime("A", 1, "S1", 28800, 28800));
		assertThat(lookup.apply(prediction("goraleigh", "B", 1, null)))
			.contains(new ScheduledStopTime("B", 1, "S1", 32400, 32400));
		assertThat(lookup.apply(prediction("goraleigh", "missing", 1, "S1"))).isEmpty();
		assertThat(lookup.apply(prediction("goraleigh", "C", 1, "S1"))).as("trip not asked for").isEmpty();
	}

}
