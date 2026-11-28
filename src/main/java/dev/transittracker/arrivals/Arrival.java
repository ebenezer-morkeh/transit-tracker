package dev.transittracker.arrivals;

import java.time.OffsetDateTime;

/**
 * When one trip is expected at the stop (see GLOSSARY.md). Live: scheduled time plus the trip's current delay
 * ({@code delaySeconds}, positive late); scheduled: {@code delaySeconds} is null and expected equals scheduled.
 */
public record Arrival(String tripId, String headsign, OffsetDateTime expectedTime, OffsetDateTime scheduledTime,
		boolean live, Integer delaySeconds) {
}
