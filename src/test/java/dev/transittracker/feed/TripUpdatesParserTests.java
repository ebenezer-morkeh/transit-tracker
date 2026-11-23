package dev.transittracker.feed;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeEvent;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TripUpdatesParserTests {

	static byte[] fixture(String name) throws Exception {
		try (var in = TripUpdatesParserTests.class.getResourceAsStream("/fixtures/" + name)) {
			return in.readAllBytes();
		}
	}

	@Test
	void goRaleighFixtureHasPredictedTimesOnly() throws Exception {
		List<TripPrediction> trips = FeedParser.parseTripUpdates(fixture("goraleigh-trips.pb"), "goraleigh");

		assertThat(trips).hasSize(114).allSatisfy(t -> assertThat(t.stopDelay()).isNull());
		// First stop has only a departure, and it is after the feed timestamp
		assertThat(trips.getFirst()).isEqualTo(new TripPrediction("goraleigh", "6695010", "21", null,
				LocalDate.of(2026, 11, 22), 1, "127", Instant.ofEpochSecond(1795401000), true, null, null,
				Instant.ofEpochSecond(1795399264)));
	}

	@Test
	void goDurhamFixtureCarriesFeedDelays() throws Exception {
		List<TripPrediction> trips = FeedParser.parseTripUpdates(fixture("godurham-trips.pb"), "godurham");

		assertThat(trips).hasSize(37);
		// Its only stop time update is before the trip's timestamp, so it is the fallback "first one"
		assertThat(trips.getFirst()).isEqualTo(new TripPrediction("godurham", "t7A5-b1781-sl3-v65", "6", "1801",
				LocalDate.of(2026, 11, 22), 3339, "995952", Instant.ofEpochSecond(1795398498), false, -261, null,
				Instant.ofEpochSecond(1795399313)));
	}

	@Test
	void picksTheFirstStopTimeUpdateAtOrAfterTheFeedTimestampAndSkipsSkippedStops() throws Exception {
		byte[] bytes = feed(trip("t", "1").addStopTimeUpdate(arrival(1, 90))
			.addStopTimeUpdate(arrival(2, 150).setScheduleRelationship(StopTimeUpdate.ScheduleRelationship.SKIPPED))
			.addStopTimeUpdate(arrival(3, 100).setDeparture(StopTimeEvent.newBuilder().setTime(110)))
			.addStopTimeUpdate(arrival(4, 200)));

		TripPrediction p = FeedParser.parseTripUpdates(bytes, "x").getFirst();
		assertThat(p.stopSequence()).isEqualTo(3);
		assertThat(p.predictedTime()).isEqualTo(Instant.ofEpochSecond(100));
		assertThat(p.departure()).isFalse();
	}

	@Test
	void tripWithNoStopTimeUpdatesKeepsItsTripDelay() throws Exception {
		byte[] bytes = feed(trip("t", "1").setDelay(42));

		assertThat(FeedParser.parseTripUpdates(bytes, "x")).containsExactly(new TripPrediction("x", "t", "1", null,
				null, null, null, null, false, null, 42, Instant.ofEpochSecond(100)));
	}

	@Test
	void skipsTripsMissingTripIdOrRoute() throws Exception {
		byte[] bytes = feed(trip("t", ""), trip("", "1"), trip("ok", "1"));

		assertThat(FeedParser.parseTripUpdates(bytes, "x")).extracting(TripPrediction::tripId).containsExactly("ok");
	}

	@Test
	void emptyFeedReturnsEmptyList() throws Exception {
		assertThat(FeedParser.parseTripUpdates(feed(), "x")).isEmpty();
	}

	static TripUpdate.Builder trip(String tripId, String routeId) {
		return TripUpdate.newBuilder().setTrip(TripDescriptor.newBuilder().setTripId(tripId).setRouteId(routeId));
	}

	static StopTimeUpdate.Builder arrival(int sequence, long time) {
		return StopTimeUpdate.newBuilder()
			.setStopSequence(sequence)
			.setArrival(StopTimeEvent.newBuilder().setTime(time));
	}

	static byte[] feed(TripUpdate.Builder... trips) {
		FeedMessage.Builder msg = FeedMessage.newBuilder()
			.setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").setTimestamp(100));
		for (int i = 0; i < trips.length; i++) {
			msg.addEntity(FeedEntity.newBuilder().setId(String.valueOf(i)).setTripUpdate(trips[i]));
		}
		return msg.build().toByteArray();
	}

}
