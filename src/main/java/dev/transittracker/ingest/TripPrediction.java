package dev.transittracker.ingest;

import dev.transittracker.delays.TripDelay;
import dev.transittracker.delays.TripDelays;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A trip update's next upcoming stop event, as the feed states it. {@code departure} says whether the event is the
 * stop's departure (no arrival given). {@code startDate}, the stop fields, {@code predictedTime} and both delays are
 * optional; {@link TripDelays} turns this into a {@link TripDelay}.
 */
public record TripPrediction(String agencyId, String tripId, String routeId, String vehicleId, LocalDate startDate,
		Integer stopSequence, String stopId, Instant predictedTime, boolean departure, Integer stopDelay,
		Integer tripDelay, Instant feedTimestamp) {
}
