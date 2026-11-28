package dev.transittracker.arrivals;

import java.util.List;

/** A route's next arrivals at a stop, soonest first. Colours are hex without #. */
public record RouteArrivals(String routeId, String shortName, String color, String textColor,
		List<Arrival> arrivals) {
}
