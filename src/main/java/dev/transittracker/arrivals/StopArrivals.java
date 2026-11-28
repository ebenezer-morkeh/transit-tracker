package dev.transittracker.arrivals;

import java.util.List;

/**
 * A stop and its next arrivals by route, routes ordered by their first arrival. {@code liveDataUnavailable}: the
 * agency's trip updates haven't polled successfully lately, so every arrival is scheduled.
 */
public record StopArrivals(String agencyId, String stopId, String name, double latitude, double longitude,
		boolean liveDataUnavailable, List<RouteArrivals> routes) {
}
