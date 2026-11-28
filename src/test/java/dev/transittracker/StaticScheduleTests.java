package dev.transittracker;

import dev.transittracker.ingest.TripPrediction;
import dev.transittracker.schedule.ScheduleLoader;
import dev.transittracker.schedule.ScheduleService;
import dev.transittracker.schedule.Route;
import dev.transittracker.schedule.ScheduledStopTime;
import dev.transittracker.schedule.Stop;
import dev.transittracker.schedule.RouteDirection;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
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
	ScheduleService schedule;

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

	static final String ROUTES = "route_id,route_short_name,route_long_name,route_type\r\n7,7,South Saunders,3\r\n";

	static final String STOPS = "stop_id,stop_name,stop_lat,stop_lon\r\nS1,First,35.7,-78.6\r\nS2,Second,35.8,-78.7\r\n";

	static final String TRIPS = "route_id,service_id,trip_id\r\n7,DAILY,T1\r\n";

	static final String CALENDAR = "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,"
			+ "end_date\r\n"
			+ "DAILY,1,1,1,1,1,1,1,20260101,20271231\r\n";

	/** A zip with {@code stopTimes} and minimal valid routes, stops, trips and calendar files. */
	static byte[] gtfsZip(String stopTimes) throws IOException {
		return gtfsZip(Map.of("routes.txt", ROUTES, "stops.txt", STOPS, "trips.txt", TRIPS, "calendar.txt", CALENDAR,
				"stop_times.txt", stopTimes));
	}

	/** A zip holding exactly {@code files} (name to contents), plus an agency.txt. */
	static byte[] gtfsZip(Map<String, String> files) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("agency.txt"));
			zip.write("agency_id,agency_name\nx,X\n".getBytes(StandardCharsets.UTF_8));
			for (var file : files.entrySet()) {
				zip.putNextEntry(new ZipEntry(file.getKey()));
				zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
			}
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

	@Test
	void loadsEachAgencysRoutesStopsAndTrips() throws Exception {
		String stopTimes = HEADER + "T1,08:00:00,08:00:00,S1,1\r\n";
		files.put("/goraleigh/gtfs.zip", gtfsZip(Map.of("routes.txt",
				"route_id,route_short_name,route_long_name,route_type,route_color\r\n7,7,South Saunders,3,1A5FAD\r\n",
				"stops.txt", "stop_id,stop_name,stop_lat,stop_lon\r\nS1,Saunders & Tryon,35.75,-78.65\r\n",
				"trips.txt", "route_id,service_id,trip_id,trip_headsign,direction_id\r\n7,WKDY,T1,To Crabtree,1\r\n",
				"calendar.txt", CALENDAR, "stop_times.txt", stopTimes)));
		files.put("/godurham/gtfs.zip", gtfsZip(Map.of("routes.txt",
				"route_id,route_short_name,route_long_name,route_type\r\n7,7,Fayetteville St,3\r\n", "stops.txt",
				"stop_id,stop_name,stop_lat,stop_lon\r\nD1,Durham Station,35.99,-78.90\r\n", "trips.txt",
				"route_id,service_id,trip_id\r\n7,DAILY,T1\r\n", "calendar.txt", CALENDAR, "stop_times.txt",
				stopTimes.replace("S1", "D1"))));
		loader.loadNow();

		assertThat(schedule.routes()).contains(new Route("goraleigh", "7", "7", "South Saunders", "1A5FAD", null),
				new Route("godurham", "7", "7", "Fayetteville St", null, null));
		assertThat(schedule.stop("goraleigh", "S1")).contains(new Stop("S1", "Saunders & Tryon", 35.75, -78.65));
		assertThat(schedule.stop("godurham", "S1")).as("stops are per agency").isEmpty();
		// Trips show through their route's directions: direction, headsign and stops.
		assertThat(schedule.directions("goraleigh", "7"))
			.containsExactly(new RouteDirection(1, "To Crabtree", List.of(new Stop("S1", "Saunders & Tryon", 35.75, -78.65))));
		assertThat(schedule.directions("godurham", "7"))
			.containsExactly(new RouteDirection(null, null, List.of(new Stop("D1", "Durham Station", 35.99, -78.90))));
	}

	@Test
	void servicesOnADateFollowTheCalendarAndItsDatedExceptions() throws Exception {
		String stopTimes = HEADER + "T1,08:00:00,08:00:00,S1,1\r\n";
		files.put("/goraleigh/gtfs.zip", gtfsZip(Map.of("routes.txt", ROUTES, "stops.txt", STOPS, "trips.txt", TRIPS,
				"calendar.txt", """
						service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date
						WKDY,1,1,1,1,1,0,0,20260817,20261231
						SUN,0,0,0,0,0,0,1,20260817,20261231
						""", "calendar_dates.txt", """
						service_id,date,exception_type
						WKDY,20261126,2
						SUN,20261126,1
						HOLIDAY,20261225,1
						""", "stop_times.txt", stopTimes)));
		// GoDurham publishes only calendar_dates.txt, which GTFS allows.
		files.put("/godurham/gtfs.zip", gtfsZip(Map.of("routes.txt", ROUTES, "stops.txt", STOPS, "trips.txt", TRIPS,
				"calendar_dates.txt", "service_id,date,exception_type\r\nDAILY,20261126,1\r\n", "stop_times.txt",
				stopTimes)));
		loader.loadNow();

		assertThat(schedule.servicesOn("goraleigh", LocalDate.of(2026, 11, 25))).as("a Wednesday")
			.containsExactly("WKDY");
		assertThat(schedule.servicesOn("goraleigh", LocalDate.of(2026, 11, 26))).as("Thanksgiving runs Sunday service")
			.containsExactly("SUN");
		assertThat(schedule.servicesOn("goraleigh", LocalDate.of(2026, 11, 29))).as("a Sunday")
			.containsExactly("SUN");
		assertThat(schedule.servicesOn("goraleigh", LocalDate.of(2026, 12, 25))).as("added on top of the calendar")
			.containsExactlyInAnyOrder("WKDY", "HOLIDAY");
		assertThat(schedule.servicesOn("goraleigh", LocalDate.of(2026, 12, 31))).as("end date is inclusive")
			.containsExactly("WKDY");
		assertThat(schedule.servicesOn("goraleigh", LocalDate.of(2027, 1, 4))).as("after the end date").isEmpty();
		assertThat(schedule.servicesOn("goraleigh", LocalDate.of(2026, 8, 16))).as("before the start date").isEmpty();
		assertThat(schedule.servicesOn("godurham", LocalDate.of(2026, 11, 26))).containsExactly("DAILY");
		assertThat(schedule.servicesOn("godurham", LocalDate.of(2026, 11, 27))).isEmpty();
	}

	@Test
	void aBrokenOrEmptyFileKeepsAllOfTheAgencysPreviousData() throws Exception {
		String stopTimes = HEADER + "T1,08:00:00,08:00:00,S1,1\r\n";
		files.put("/goraleigh/gtfs.zip", gtfsZip(stopTimes));
		files.remove("/godurham/gtfs.zip");
		loader.loadNow();
		Route route = new Route("goraleigh", "7", "7", "South Saunders", null, null);
		LocalDate day = LocalDate.of(2026, 11, 25);
		assertThat(schedule.routes()).contains(route);

		Map<String, String> good = Map.of("routes.txt", ROUTES.replace("South Saunders", "Renamed"), "stops.txt",
				STOPS, "trips.txt", TRIPS, "calendar.txt", CALENDAR, "stop_times.txt", stopTimes);
		List<Map<String, String>> broken = List.of(
				with(good, "calendar_dates.txt", "service_id,date,exception_type\r\nDAILY,20261126,9\r\n"),
				without(good, "stops.txt"), with(good, "routes.txt", "route_id,route_short_name\r\n"),
				with(good, "stops.txt", "stop_id,stop_name,stop_lat,stop_lon\r\n"),
				with(good, "trips.txt", "route_id,service_id,trip_id\r\n"), without(good, "calendar.txt"));
		for (Map<String, String> zip : broken) {
			files.put("/goraleigh/gtfs.zip", gtfsZip(zip));
			loader.loadNow();

			assertThat(schedule.routes()).as(zip.keySet().toString()).contains(route);
			assertThat(schedule.stop("goraleigh", "S1")).isPresent();
			assertThat(schedule.directions("goraleigh", "7")).isNotEmpty();
			assertThat(schedule.servicesOn("goraleigh", day)).containsExactly("DAILY");
			assertThat(find("goraleigh", "T1", 1, null)).isPresent();
		}
	}

	static Map<String, String> with(Map<String, String> files, String name, String contents) {
		Map<String, String> copy = new HashMap<>(files);
		copy.put(name, contents);
		return copy;
	}

	static Map<String, String> without(Map<String, String> files, String name) {
		Map<String, String> copy = new HashMap<>(files);
		copy.remove(name);
		return copy;
	}

}
