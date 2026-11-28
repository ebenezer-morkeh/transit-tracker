package dev.transittracker;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.transittracker.schedule.ScheduleLoader;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
class RoutesApiTests {

	static final StubFeedServer feeds = new StubFeedServer();

	@Autowired
	MockMvc mvc;

	@Autowired
	ScheduleLoader loader;

	@DynamicPropertySource
	static void feeds(DynamicPropertyRegistry registry) {
		String[] agencies = { "goraleigh", "godurham" };
		for (int i = 0; i < agencies.length; i++) {
			String agency = agencies[i];
			registry.add("transit.feeds[" + i + "].agency-id", () -> agency);
			registry.add("transit.feeds[" + i + "].vehicle-positions-url", () -> feeds.url("/" + agency + "/vehicles"));
			registry.add("transit.feeds[" + i + "].static-gtfs-url", () -> feeds.url("/" + agency + "/gtfs.zip"));
		}
	}

	@AfterAll
	static void stop() {
		feeds.stop();
	}

	@BeforeEach
	void loadSchedules() throws Exception {
		feeds.files.put("/goraleigh/gtfs.zip", StaticScheduleTests.gtfsZip(Map.of("routes.txt", """
				route_id,route_short_name,route_long_name,route_type,route_color,route_text_color
				R10,10,Longview,3,AA0000,FFFFFF
				R2,2,Falls of Neuse,3,0000AA,FFFFFF
				R100X,100X,Airport,3,,
				""", "stops.txt", """
				stop_id,stop_name,stop_lat,stop_lon
				A,Stop A,35.1,-78.1
				B,Stop B,35.2,-78.2
				C,Stop C,35.3,-78.3
				""", "trips.txt", """
				route_id,service_id,trip_id,trip_headsign,direction_id
				R2,DAILY,T1,To Crabtree,0
				R2,DAILY,T2,To Crabtree,0
				R2,DAILY,T3,To Crabtree Express,0
				R2,DAILY,T4,To Downtown,1
				R2,DAILY,T6,,
				R10,DAILY,T5,Longview,
				""", "calendar.txt", StaticScheduleTests.CALENDAR, "stop_times.txt", """
				trip_id,arrival_time,departure_time,stop_id,stop_sequence
				T1,08:00:00,08:00:00,A,1
				T1,08:05:00,08:05:00,B,2
				T1,08:10:00,08:10:00,C,3
				T2,09:00:00,09:00:00,A,1
				T2,09:05:00,09:05:00,B,2
				T3,10:00:00,10:00:00,A,1
				T3,10:10:00,10:10:00,C,2
				T4,11:00:00,11:00:00,C,1
				T4,11:05:00,11:05:00,B,2
				T4,11:10:00,11:10:00,A,3
				T5,12:00:00,12:00:00,B,1
				T6,13:00:00,13:00:00,C,1
				""")));
		// GoDurham also has a route R2: route IDs are only unique within an agency.
		feeds.files.put("/godurham/gtfs.zip", StaticScheduleTests.gtfsZip(Map.of("routes.txt",
				"route_id,route_short_name,route_long_name,route_type\r\nR2,2,Durham Two,3\r\n", "stops.txt",
				StaticScheduleTests.STOPS, "trips.txt",
				"route_id,service_id,trip_id,trip_headsign\r\nR2,DAILY,D1,Durham Station\r\n",
				"calendar.txt", StaticScheduleTests.CALENDAR, "stop_times.txt",
				StaticScheduleTests.HEADER + "D1,08:00:00,08:00:00,S2,1\r\n")));
		loader.loadNow();
	}

	@Test
	void routesListEveryAgencySortedByAgencyThenShortNameNaturally() throws Exception {
		mvc.perform(get("/api/routes"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].agencyId", contains("godurham", "goraleigh", "goraleigh", "goraleigh")))
			.andExpect(jsonPath("$[*].shortName", contains("2", "2", "10", "100X")))
			.andExpect(jsonPath("$[0].longName").value("Durham Two"))
			.andExpect(jsonPath("$[1].routeId").value("R2"))
			.andExpect(jsonPath("$[1].longName").value("Falls of Neuse"))
			.andExpect(jsonPath("$[1].color").value("0000AA"))
			.andExpect(jsonPath("$[1].textColor").value("FFFFFF"));
	}

	@Test
	void aRoutesStopsComeByDirectionInTheOrderOfItsLongestTrip() throws Exception {
		mvc.perform(get("/api/agencies/goraleigh/routes/R2/stops"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].directionId", contains(0, 1)))
			.andExpect(jsonPath("$[0].headsign").value("To Crabtree"))
			.andExpect(jsonPath("$[0].stops[*].stopId", contains("A", "B", "C")))
			.andExpect(jsonPath("$[0].stops[0].name").value("Stop A"))
			.andExpect(jsonPath("$[0].stops[0].latitude").value(35.1))
			.andExpect(jsonPath("$[0].stops[0].longitude").value(-78.1))
			.andExpect(jsonPath("$[1].headsign").value("To Downtown"))
			.andExpect(jsonPath("$[1].stops[*].stopId", contains("C", "B", "A")));
	}

	@Test
	void twoAgenciesWithTheSameRouteIdKeepTheirOwnStops() throws Exception {
		mvc.perform(get("/api/agencies/godurham/routes/R2/stops"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].headsign", contains("Durham Station")))
			.andExpect(jsonPath("$[0].stops[*].stopId", contains("S2")));
	}

	@Test
	void aRouteWithoutDirectionsHasOne() throws Exception {
		mvc.perform(get("/api/agencies/goraleigh/routes/R10/stops"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].headsign", contains("Longview")))
			.andExpect(jsonPath("$[0].directionId").doesNotExist())
			.andExpect(jsonPath("$[0].stops[*].stopId", contains("B")));
	}

	@Test
	void unknownAgencyOrRouteIsNotFound() throws Exception {
		mvc.perform(get("/api/agencies/nope/routes/R2/stops")).andExpect(status().isNotFound());
		mvc.perform(get("/api/agencies/goraleigh/routes/nope/stops")).andExpect(status().isNotFound());
		mvc.perform(get("/api/agencies/godurham/routes/R10/stops")) // routes are per agency
			.andExpect(status().isNotFound());
	}

}
