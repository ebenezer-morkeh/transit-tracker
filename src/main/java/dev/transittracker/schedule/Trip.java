package dev.transittracker.schedule;

/**
 * One row of an agency's static {@code trips.txt}. Direction (0 or 1) and headsign are null when the feed leaves them
 * blank.
 */
public record Trip(String tripId, String routeId, String serviceId, Integer directionId, String headsign) {
}
