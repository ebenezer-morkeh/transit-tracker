package dev.transittracker;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
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
import dev.transittracker.ingest.FeedPoller;
import dev.transittracker.schedule.ScheduleLoader;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@Import({ TestcontainersConfiguration.class, ArrivalsApiTests.FixedClock.class })
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArrivalsApiTests {

	/** Wednesday 2026-11-25, 08:00 in Raleigh (EST, UTC-5). */
	static final Instant NOW = Instant.parse("2026-11-25T13:00:00Z");

	/** "Now" for the app; starts at {@link #NOW} for each test, which may move it. */
	static final class TestClock extends Clock {

		volatile Instant now = NOW;

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			throw new UnsupportedOperationException();
		}

	}

	static final TestClock clock = new TestClock();

	@TestConfiguration
	static class FixedClock {

		@Bean
		@Primary
		Clock testClock() {
			return clock;
		}

	}

	static final StubFeedServer feeds = new StubFeedServer();

	@Autowired
	MockMvc mvc;

	@Autowired
	ScheduleLoader loader;

	@Autowired
	FeedPoller poller;

	@DynamicPropertySource
	static void feeds(DynamicPropertyRegistry registry) {
		String[] agencies = { "goraleigh", "godurham" };
		for (int i = 0; i < agencies.length; i++) {
			String agency = agencies[i];
			registry.add("transit.feeds[" + i + "].agency-id", () -> agency);
			registry.add("transit.feeds[" + i + "].vehicle-positions-url", () -> feeds.url("/" + agency + "/vehicles"));
			registry.add("transit.feeds[" + i + "].trip-updates-url", () -> feeds.url("/" + agency + "/trips"));
			registry.add("transit.feeds[" + i + "].static-gtfs-url", () -> feeds.url("/" + agency + "/gtfs.zip"));
		}
	}

	@AfterAll
	static void stop() {
		feeds.stop();
	}

	@BeforeEach
	void loadSchedules() throws Exception {
		clock.now = NOW;
		feeds.files.put("/goraleigh/gtfs.zip", StaticScheduleTests.gtfsZip(Map.of("routes.txt", """
				route_id,route_short_name,route_long_name,route_type,route_color,route_text_color
				R1,1,Capital,3,AA0000,FFFFFF
				R2,2,Falls of Neuse,3,0000AA,FFFFFF
				""", "stops.txt", """
				stop_id,stop_name,stop_lat,stop_lon
				S,Main & 1st,35.78,-78.64
				OTHER,Elsewhere,35.70,-78.60
				""", "trips.txt", """
				route_id,service_id,trip_id,trip_headsign
				R1,WKDY,L1,Downtown
				R1,WKDY,L2,Downtown
				R1,WKDY,P1,Downtown
				R1,WKDY,E1,Downtown
				R1,WKDY,E2,Downtown
				R2,WKDY,F1,Crabtree
				R2,WKDY,N1,Crabtree Late
				R2,HOL,H1,Crabtree
				R2,SAT,X1,Crabtree
				R2,WKDY,L3,Crabtree
				""", "calendar.txt", """
				service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date
				WKDY,1,1,1,1,1,0,0,20260101,20271231
				SAT,0,0,0,0,0,1,0,20260101,20271231
				""", "calendar_dates.txt", """
				service_id,date,exception_type
				HOL,20261125,1
				""", "stop_times.txt", """
				trip_id,arrival_time,departure_time,stop_id,stop_sequence
				L1,07:58:00,07:58:00,S,1
				L2,08:10:00,08:10:00,S,1
				P1,07:50:00,07:50:00,S,1
				E1,08:20:00,08:20:00,S,1
				E2,08:30:00,08:30:00,S,1
				F1,09:30:00,09:30:00,S,1
				N1,32:05:00,32:05:00,S,1
				H1,08:15:00,08:15:00,S,1
				X1,08:12:00,08:12:00,S,1
				L3,09:10:00,09:10:00,S,1
				L3,09:20:00,09:20:00,OTHER,2
				""")));
		feeds.files.put("/godurham/gtfs.zip",
				StaticScheduleTests.gtfsZip(StaticScheduleTests.HEADER + "T1,08:30:00,08:30:00,S1,1\r\n"));
		loader.loadNow();
	}

	static byte[] tripUpdates(Instant feedTime, String... tripIdsAndDelays) {
		FeedMessage.Builder msg = FeedMessage.newBuilder()
			.setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").setTimestamp(feedTime.getEpochSecond()));
		for (int i = 0; i < tripIdsAndDelays.length; i += 2) {
			String tripId = tripIdsAndDelays[i];
			msg.addEntity(FeedEntity.newBuilder()
				.setId(tripId)
				.setTripUpdate(TripUpdate.newBuilder()
					.setTrip(TripDescriptor.newBuilder().setTripId(tripId).setRouteId("R"))
					.addStopTimeUpdate(StopTimeUpdate.newBuilder()
						.setStopSequence(1)
						.setArrival(StopTimeEvent.newBuilder().setDelay(Integer.parseInt(tripIdsAndDelays[i + 1]))))));
		}
		return msg.build().toByteArray();
	}

	@Test
	void aStopsNextHourOfArrivalsLiveOrScheduledUpToThreePerRoute() throws Exception {
		feeds.files.put("/goraleigh/trips", tripUpdates(NOW.minusSeconds(30), "L1", "300", "L3", "-720"));
		feeds.files.remove("/godurham/trips");
		poller.pollOnce();

		mvc.perform(get("/api/agencies/goraleigh/stops/S/arrivals"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.agencyId").value("goraleigh"))
			.andExpect(jsonPath("$.stopId").value("S"))
			.andExpect(jsonPath("$.name").value("Main & 1st"))
			.andExpect(jsonPath("$.latitude").value(35.78))
			.andExpect(jsonPath("$.longitude").value(-78.64))
			.andExpect(jsonPath("$.liveDataUnavailable").value(false))
			.andExpect(jsonPath("$.routes[*].routeId", contains("R1", "R2")))
			.andExpect(jsonPath("$.routes[0].shortName").value("1"))
			.andExpect(jsonPath("$.routes[0].color").value("AA0000"))
			.andExpect(jsonPath("$.routes[0].textColor").value("FFFFFF"))
			// L1 is 5 min late; P1 has gone; E2 is the 4th on route 1.
			.andExpect(jsonPath("$.routes[0].arrivals[*].tripId", contains("L1", "L2", "E1")))
			.andExpect(jsonPath("$.routes[0].arrivals[*].live", contains(true, false, false)))
			.andExpect(jsonPath("$.routes[0].arrivals[0].expectedTime").value("2026-11-25T08:03:00-05:00"))
			.andExpect(jsonPath("$.routes[0].arrivals[0].scheduledTime").value("2026-11-25T07:58:00-05:00"))
			.andExpect(jsonPath("$.routes[0].arrivals[0].delaySeconds").value(300))
			.andExpect(jsonPath("$.routes[0].arrivals[0].headsign").value("Downtown"))
			.andExpect(jsonPath("$.routes[0].arrivals[1].delaySeconds").doesNotExist())
			// N1 is yesterday's 32:05; H1 runs today by calendar_dates; L3 is 12 min early; X1 is Saturday only;
			// F1 is more than an hour away.
			.andExpect(jsonPath("$.routes[1].arrivals[*].tripId", contains("N1", "H1", "L3")))
			.andExpect(jsonPath("$.routes[1].arrivals[0].expectedTime").value("2026-11-25T08:05:00-05:00"))
			.andExpect(jsonPath("$.routes[1].arrivals[2].expectedTime").value("2026-11-25T08:58:00-05:00"))
			.andExpect(jsonPath("$.routes[1].arrivals[2].delaySeconds").value(-720));
	}

	@Test
	void aTripDelayOlderThanTwoMinutesIsScheduled() throws Exception {
		feeds.files.put("/goraleigh/trips", tripUpdates(NOW.minusSeconds(121), "L1", "300"));
		poller.pollOnce();

		// L1's 07:58 has gone, so route 2's 08:05 now comes first.
		mvc.perform(get("/api/agencies/goraleigh/stops/S/arrivals"))
			.andExpect(jsonPath("$.routes[*].routeId", contains("R2", "R1")))
			.andExpect(jsonPath("$.routes[1].arrivals[*].tripId", contains("L2", "E1", "E2")))
			.andExpect(jsonPath("$.routes[1].arrivals[*].live", contains(false, false, false)));
	}

	@Test
	void anAgencyWhoseTripFeedHasNotSucceededHasLiveDataUnavailable() throws Exception {
		feeds.files.remove("/godurham/trips");
		poller.pollOnce();

		mvc.perform(get("/api/agencies/godurham/stops/S1/arrivals"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.liveDataUnavailable").value(true))
			.andExpect(jsonPath("$.routes[0].arrivals[*].tripId", contains("T1")))
			.andExpect(jsonPath("$.routes[0].arrivals[0].live").value(false));
	}

	@Test
	void liveDataGoesUnavailableTwoMinutesAfterTheLastSuccessfulPoll() throws Exception {
		feeds.files.put("/goraleigh/trips", tripUpdates(NOW, "L1", "300"));
		poller.pollOnce();
		mvc.perform(get("/api/agencies/goraleigh/stops/S/arrivals"))
			.andExpect(jsonPath("$.liveDataUnavailable").value(false));

		// The feed starts failing; two minutes later its last good poll is too old.
		feeds.files.remove("/goraleigh/trips");
		clock.now = NOW.plusSeconds(121);
		poller.pollOnce();

		mvc.perform(get("/api/agencies/goraleigh/stops/S/arrivals"))
			.andExpect(jsonPath("$.liveDataUnavailable").value(true))
			.andExpect(jsonPath("$.routes[*].arrivals[*].live", everyItem(is(false))));
	}

	@Test
	void aStopWithNothingDueHasNoRoutes() throws Exception {
		mvc.perform(get("/api/agencies/goraleigh/stops/OTHER/arrivals"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.routes", hasSize(0)));
	}

	@Test
	void unknownAgencyOrStopIsNotFound() throws Exception {
		mvc.perform(get("/api/agencies/nope/stops/S/arrivals")).andExpect(status().isNotFound());
		mvc.perform(get("/api/agencies/goraleigh/stops/nope/arrivals")).andExpect(status().isNotFound());
	}

}
