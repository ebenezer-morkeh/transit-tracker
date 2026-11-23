package dev.transittracker.feed;

/**
 * One row of a static GTFS {@code stop_times.txt}. Times are seconds after local midnight of the service day and may
 * exceed 86400 (trips past midnight). Null when the feed leaves the time blank (untimed stop).
 */
public record ScheduledStopTime(String tripId, int stopSequence, String stopId, Integer arrivalSeconds,
		Integer departureSeconds) {
}
