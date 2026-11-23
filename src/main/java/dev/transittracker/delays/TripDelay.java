package dev.transittracker.delays;

import java.time.Instant;

/** A trip's current delay in seconds (positive is late). Also the JSON shape of the delays endpoint. */
public record TripDelay(String agencyId, String tripId, String routeId, String vehicleId, int delaySeconds,
		Instant feedTimestamp) {
}
