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

		assertThat(trips).hasSize(112).allSatisfy(t -> assertThat(t.stopDelay()).isNull());
		// First stop has only a departure, and it is after the feed timestamp
		assertThat(trips.getFirst()).isEqualTo(new TripPrediction("goraleigh", "6695010", "21", null,
				LocalDate.of(2026, 11, 22), 1, "127", Instant.ofEpochSecond(1795401000), true, null, null,
				Instant.ofEpochSecond(1795399264)));
	}

	@Test
	void goDurhamFixtureCarriesFeedDelays() throws Exception {
		List<TripPrediction> trips = FeedParser.parseTripUpdates(fixture("godurham-trips.pb"), "godurham");

		// 17 of 37 trips list only stops already passed and no trip delay, so they are left out
		assertThat(trips).hasSize(20);
		assertThat(trips.getFirst()).isEqualTo(new TripPrediction("godurham", "t7EE-b179F-sl3-v65", "6", "1902",
				LocalDate.of(2026, 11, 22), 1640, "6609", Instant.parse("2026-11-23T02:02:23Z"), false, 303, null,
				Instant.parse("2026-11-23T02:01:48Z")));
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
	void delayOnlyStopsCountAsUpcomingAfterPassedStops() throws Exception {
		// GoDurham style: passed stops carry times, future stops carry only a delay.
		byte[] bytes = feed(trip("t", "1").addStopTimeUpdate(arrival(1, 90))
			.addStopTimeUpdate(StopTimeUpdate.newBuilder()
				.setStopSequence(2)
				.setArrival(StopTimeEvent.newBuilder().setDelay(60)))
			.addStopTimeUpdate(StopTimeUpdate.newBuilder()
				.setStopSequence(3)
				.setArrival(StopTimeEvent.newBuilder().setDelay(70))));

		TripPrediction p = FeedParser.parseTripUpdates(bytes, "x").getFirst();
		assertThat(p.stopSequence()).isEqualTo(2);
		assertThat(p.stopDelay()).isEqualTo(60);
		assertThat(p.predictedTime()).isNull();
	}

	@Test
	void onlyPassedStopsFallBackToTheTripDelayElseTheTripIsSkipped() throws Exception {
		byte[] bytes = feed(trip("withDelay", "1").setDelay(30).addStopTimeUpdate(arrival(1, 50))
			.addStopTimeUpdate(arrival(2, 60)), trip("noDelay", "1").addStopTimeUpdate(arrival(1, 50)));

		assertThat(FeedParser.parseTripUpdates(bytes, "x")).containsExactly(new TripPrediction("x", "withDelay", "1",
				null, null, null, null, null, false, null, 30, Instant.ofEpochSecond(100)));
	}

	@Test
	void emptyArrivalUsesTheDeparture() throws Exception {
		byte[] bytes = feed(trip("t", "1").addStopTimeUpdate(StopTimeUpdate.newBuilder()
			.setStopSequence(4)
			.setArrival(StopTimeEvent.newBuilder())
			.setDeparture(StopTimeEvent.newBuilder().setTime(150))));

		TripPrediction p = FeedParser.parseTripUpdates(bytes, "x").getFirst();
		assertThat(p.stopSequence()).isEqualTo(4);
		assertThat(p.predictedTime()).isEqualTo(Instant.ofEpochSecond(150));
		assertThat(p.departure()).isTrue();
	}

	@Test
	void tripWithNoStopTimeUpdatesKeepsItsTripDelay() throws Exception {
		byte[] bytes = feed(trip("t", "1").setDelay(42));

		assertThat(FeedParser.parseTripUpdates(bytes, "x")).containsExactly(new TripPrediction("x", "t", "1", null,
				null, null, null, null, false, null, 42, Instant.ofEpochSecond(100)));
	}

	@Test
	void skipsTripsMissingTripIdOrRoute() throws Exception {
		byte[] bytes = feed(trip("t", "").setDelay(1), trip("", "1").setDelay(1), trip("ok", "1").setDelay(1));

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
