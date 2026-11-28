package dev.transittracker.delays;

import dev.transittracker.ingest.TripPrediction;
import dev.transittracker.schedule.ScheduledStopTime;
import dev.transittracker.schedule.ServiceDay;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Function;

/** The delay rule (spec, Data model): pure, no I/O. */
public final class TripDelays {

	private TripDelays() {
	}

	/**
	 * Feed stop delay if set, else predicted time minus the scheduled time {@code schedule} finds, else the trip-level
	 * delay. Empty when there is none of these.
	 */
	public static Optional<TripDelay> resolve(TripPrediction p,
			Function<TripPrediction, Optional<ScheduledStopTime>> schedule) {
		Integer delay = p.stopDelay();
		if (delay == null && p.predictedTime() != null) {
			LocalDate serviceDate = p.startDate() != null ? p.startDate()
					: LocalDate.ofInstant(p.feedTimestamp(), ServiceDay.ZONE);
			delay = schedule.apply(p)
				.map(s -> scheduledSeconds(p, s))
				.map(seconds -> secondsLate(serviceDate, seconds, p.predictedTime()))
				.orElse(null);
		}
		if (delay == null) {
			delay = p.tripDelay();
		}
		return Optional.ofNullable(delay)
			.map(d -> new TripDelay(p.agencyId(), p.tripId(), p.routeId(), p.vehicleId(), d, p.feedTimestamp()));
	}

	/** The scheduled time of the prediction's event (departure or arrival), else the other one; null if untimed. */
	private static Integer scheduledSeconds(TripPrediction prediction, ScheduledStopTime scheduled) {
		if (prediction.departure() && scheduled.departureSeconds() != null) {
			return scheduled.departureSeconds();
		}
		return scheduled.arrivalSeconds() != null ? scheduled.arrivalSeconds() : scheduled.departureSeconds();
	}

	/**
	 * {@code predicted} minus the GTFS time {@code scheduledSeconds} (may exceed 24h) on {@code serviceDate}; see
	 * {@link ServiceDay#at}.
	 */
	public static int secondsLate(LocalDate serviceDate, int scheduledSeconds, Instant predicted) {
		Instant scheduled = ServiceDay.at(serviceDate, scheduledSeconds);
		return Math.toIntExact(predicted.getEpochSecond() - scheduled.getEpochSecond());
	}

}
