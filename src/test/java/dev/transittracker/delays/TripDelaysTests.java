package dev.transittracker.delays;

import dev.transittracker.ingest.TripPrediction;
import dev.transittracker.schedule.ScheduledStopTime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TripDelaysTests {

	static final LocalDate NOV_22 = LocalDate.of(2026, 11, 22);

	@Test
	void lateAgainstScheduledTimeOnTheServiceDate() {
		// 21:00:00 scheduled, predicted 21:02 EST
		assertThat(TripDelays.secondsLate(NOV_22, 21 * 3600, Instant.parse("2026-11-23T02:02:00Z"))).isEqualTo(120);
	}

	@Test
	void timesPast24HoursFallOnTheNextCalendarDay() {
		// 25:10:00 on Nov 22 is 01:10 EST on Nov 23; predicted a minute early
		assertThat(TripDelays.secondsLate(NOV_22, 25 * 3600 + 600, Instant.parse("2026-11-23T06:09:00Z")))
			.isEqualTo(-60);
	}

	@Test
	void dstChangeDaysKeepWallClockTimes() {
		// Spring forward 2027-03-14: 08:00:00 is 08:00 EDT (12:00Z)
		assertThat(TripDelays.secondsLate(LocalDate.of(2027, 3, 14), 8 * 3600, Instant.parse("2027-03-14T12:05:00Z")))
			.isEqualTo(300);
		// Fall back 2026-11-01: 08:00:00 is 08:00 EST (13:00Z)
		assertThat(TripDelays.secondsLate(LocalDate.of(2026, 11, 1), 8 * 3600, Instant.parse("2026-11-01T13:00:00Z")))
			.isEqualTo(0);
	}

	static TripPrediction prediction(LocalDate startDate, Instant predicted, Integer stopDelay, Integer tripDelay,
			boolean departure) {
		return new TripPrediction("goraleigh", "t1", "6", "1809", startDate, 2, "128", predicted, departure, stopDelay,
				tripDelay, Instant.parse("2026-11-23T02:00:00Z"));
	}

	static final ScheduledStopTime STOP = new ScheduledStopTime("t1", 2, "128", 21 * 3600, 21 * 3600 + 60);

	@Test
	void feedStopDelayWinsOverTheSchedule() {
		TripPrediction p = prediction(NOV_22, Instant.parse("2026-11-23T02:10:00Z"), 45, 99, false);

		assertThat(TripDelays.resolve(p, x -> Optional.of(STOP)))
			.contains(new TripDelay("goraleigh", "t1", "6", "1809", 45, Instant.parse("2026-11-23T02:00:00Z")));
	}

	@Test
	void computesFromTheScheduledArrivalOrDepartureMatchingThePredictedEvent() {
		Instant predicted = Instant.parse("2026-11-23T02:03:00Z");

		assertThat(TripDelays.resolve(prediction(NOV_22, predicted, null, null, false), x -> Optional.of(STOP)))
			.map(TripDelay::delaySeconds)
			.contains(180);
		assertThat(TripDelays.resolve(prediction(NOV_22, predicted, null, null, true), x -> Optional.of(STOP)))
			.map(TripDelay::delaySeconds)
			.contains(120);
		// Untimed departure: use arrival
		assertThat(TripDelays.resolve(prediction(NOV_22, predicted, null, null, true),
				x -> Optional.of(new ScheduledStopTime("t1", 2, "128", 21 * 3600, null))))
			.map(TripDelay::delaySeconds)
			.contains(180);
	}

	@Test
	void withoutStartDateTheServiceDateIsTodayInNewYorkAtTheFeedTimestamp() {
		// Feed timestamp 02:00Z Nov 23 is 21:00 Nov 22 in New York
		TripPrediction p = prediction(null, Instant.parse("2026-11-23T02:03:00Z"), null, null, false);

		assertThat(TripDelays.resolve(p, x -> Optional.of(STOP))).map(TripDelay::delaySeconds).contains(180);
	}

	@Test
	void fallsBackToTripDelayThenSkips() {
		Instant predicted = Instant.parse("2026-11-23T02:03:00Z");

		assertThat(TripDelays.resolve(prediction(NOV_22, predicted, null, -30, false), x -> Optional.empty()))
			.map(TripDelay::delaySeconds)
			.contains(-30);
		assertThat(TripDelays.resolve(prediction(NOV_22, predicted, null, null, false), x -> Optional.empty()))
			.isEmpty();
		// No predicted time: nothing to compute from
		assertThat(TripDelays.resolve(prediction(NOV_22, null, null, null, false), x -> Optional.of(STOP))).isEmpty();
	}

}
