package dev.transittracker;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeEvent;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;
import com.sun.net.httpserver.HttpServer;
import dev.transittracker.feed.ScheduleLoader;
import dev.transittracker.feed.VehiclePoller;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DelaysApiTests {

	/** Response by path; a missing path is a 404. */
	static final Map<String, byte[]> files = new ConcurrentHashMap<>();

	static final HttpServer server = start();

	/** 20:55 EST on Nov 22. */
	static final Instant FEED_TIME = Instant.parse("2026-11-23T01:55:00Z");

	@Autowired
	MockMvc mvc;

	@Autowired
	VehiclePoller poller;

	@Autowired
	ScheduleLoader loader;

	static HttpServer start() {
		try {
			HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			s.createContext("/", exchange -> {
				byte[] body = files.get(exchange.getRequestURI().getPath());
				exchange.sendResponseHeaders(body == null ? 500 : 200, body == null || body.length == 0 ? -1 : body.length);
				if (body != null) {
					exchange.getResponseBody().write(body);
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
		for (String[] f : new String[][] { { "0", "goraleigh" }, { "1", "godurham" } }) {
			registry.add("transit.feeds[" + f[0] + "].agency-id", () -> f[1]);
			registry.add("transit.feeds[" + f[0] + "].vehicle-positions-url", () -> base + "/" + f[1] + "/vehicles");
			registry.add("transit.feeds[" + f[0] + "].trip-updates-url", () -> base + "/" + f[1] + "/trips");
			registry.add("transit.feeds[" + f[0] + "].static-gtfs-url", () -> base + "/" + f[1] + "/gtfs.zip");
		}
	}

	@AfterAll
	static void stop() {
		server.stop(0);
	}

	@Test
	void delaysAreComputedOrTakenFromTheFeedSortedMostDelayedFirst() throws Exception {
		files.put("/goraleigh/gtfs.zip", gtfsZip("""
				trip_id,arrival_time,departure_time,stop_id,stop_sequence
				T1,20:50:00,20:50:00,S1,1
				T1,21:00:00,21:00:00,S2,2
				T2,21:00:00,21:00:00,S1,1
				T4,21:00:00,21:00:00,S1,1
				"""));
		files.put("/godurham/gtfs.zip", gtfsZip("trip_id,arrival_time,departure_time,stop_id,stop_sequence\n"));
		loader.loadNow();
		files.put("/goraleigh/vehicles", vehicles());
		files.put("/godurham/vehicles", vehicles());
		// GoRaleigh: predicted times only. T3 has no schedule and no delay, so it is skipped.
		files.put("/goraleigh/trips", trips(trip("T2", "1", 1, "2026-11-23T01:58:00Z", null),
				trip("T1", "1", 2, "2026-11-23T02:05:00Z", null), trip("T3", "1", 1, "2026-11-23T02:05:00Z", null),
				trip("T4", "2", 1, "2026-11-23T02:01:00Z", null)));
		// GoDurham: same route ID, delay from the feed.
		files.put("/godurham/trips", trips(trip("D1", "1", 7, "2026-11-23T02:05:00Z", 600)));
		poller.pollOnce();

		mvc.perform(get("/api/agencies/goraleigh/routes/1/delays"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].tripId", contains("T1", "T2")))
			.andExpect(jsonPath("$[*].delaySeconds", contains(300, -120)))
			.andExpect(jsonPath("$[0].agencyId").value("goraleigh"))
			.andExpect(jsonPath("$[0].routeId").value("1"))
			.andExpect(jsonPath("$[0].vehicleId").value("V-T1"))
			.andExpect(jsonPath("$[0].feedTimestamp").value(FEED_TIME.toString()));
		mvc.perform(get("/api/agencies/godurham/routes/1/delays"))
			.andExpect(jsonPath("$[*].tripId", contains("D1")))
			.andExpect(jsonPath("$[*].delaySeconds", contains(600)));
		mvc.perform(get("/api/agencies/goraleigh/routes/99/delays"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));

		// T2 drops out of GoRaleigh's next poll; GoDurham's trips feed fails and keeps its last good delays.
		files.put("/goraleigh/trips", trips(trip("T1", "1", 2, "2026-11-23T02:06:00Z", null)));
		files.remove("/godurham/trips");
		poller.pollOnce();

		mvc.perform(get("/api/agencies/goraleigh/routes/1/delays"))
			.andExpect(jsonPath("$[*].tripId", contains("T1")))
			.andExpect(jsonPath("$[*].delaySeconds", contains(360)));
		mvc.perform(get("/api/agencies/godurham/routes/1/delays")).andExpect(jsonPath("$[*].tripId", contains("D1")));
	}

	static TripUpdate.Builder trip(String tripId, String routeId, int sequence, String predicted, Integer delay) {
		StopTimeEvent.Builder arrival = StopTimeEvent.newBuilder().setTime(Instant.parse(predicted).getEpochSecond());
		if (delay != null) {
			arrival.setDelay(delay);
		}
		return TripUpdate.newBuilder()
			.setTrip(TripDescriptor.newBuilder().setTripId(tripId).setRouteId(routeId).setStartDate("20261122"))
			.setVehicle(com.google.transit.realtime.GtfsRealtime.VehicleDescriptor.newBuilder().setId("V-" + tripId))
			.addStopTimeUpdate(StopTimeUpdate.newBuilder().setStopSequence(sequence).setArrival(arrival));
	}

	static byte[] trips(TripUpdate.Builder... trips) {
		FeedMessage.Builder msg = FeedMessage.newBuilder()
			.setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").setTimestamp(FEED_TIME.getEpochSecond()));
		for (TripUpdate.Builder t : trips) {
			msg.addEntity(FeedEntity.newBuilder().setId(t.getTrip().getTripId()).setTripUpdate(t));
		}
		return msg.build().toByteArray();
	}

	static byte[] vehicles() {
		return trips();
	}

	static byte[] gtfsZip(String stopTimes) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("stop_times.txt"));
			zip.write(stopTimes.getBytes(StandardCharsets.UTF_8));
		}
		return bytes.toByteArray();
	}

}
