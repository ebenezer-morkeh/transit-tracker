package dev.transittracker.schedule;

import java.util.List;

/**
 * One direction of a route: its GTFS direction ID (null when the feed sets none), its most common headsign, and the
 * stops of its trip with the most stops, in order.
 */
public record RouteDirection(Integer directionId, String headsign, List<Stop> stops) {
}
