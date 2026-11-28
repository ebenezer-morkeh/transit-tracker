package dev.transittracker.arrivals;

import dev.transittracker.delays.TripDelay;
import dev.transittracker.schedule.Route;
import dev.transittracker.schedule.ScheduledCall;
import dev.transittracker.schedule.ServiceDay;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The arrival rule (ADR-0007): pure, so "now" and the delays come in as values. */
final class Arrivals {

	/** How far ahead a stop shows arrivals. */
	static final Duration WINDOW = Duration.ofMinutes(60);

	static final int PER_ROUTE = 3;

	private Arrivals() {
	}

	/** A scheduled call placed on its service day. */
	record Timed(ScheduledCall call, Instant scheduled) {
	}

	private record Expected(Route route, Arrival arrival, Instant at) {
	}

	/**
	 * Each call is live when its trip has a delay no older than {@code liveFor} (expected = scheduled + delay), else
	 * scheduled. Keeps arrivals expected from {@code now} to {@code now + WINDOW}, the soonest
	 * {@link #PER_ROUTE} per route, routes ordered by their first arrival.
	 */
	static List<RouteArrivals> next(List<Timed> calls, Map<String, TripDelay> delays, Instant now, Duration liveFor) {
		List<Expected> expected = new ArrayList<>();
		for (Timed t : calls) {
			TripDelay delay = delays.get(t.call().tripId());
			boolean live = delay != null && !delay.feedTimestamp().isBefore(now.minus(liveFor));
			Instant at = live ? t.scheduled().plusSeconds(delay.delaySeconds()) : t.scheduled();
			if (at.isBefore(now) || at.isAfter(now.plus(WINDOW))) {
				continue;
			}
			expected.add(new Expected(t.call().route(),
					new Arrival(t.call().tripId(), t.call().headsign(), at.atZone(ServiceDay.ZONE).toOffsetDateTime(),
							t.scheduled().atZone(ServiceDay.ZONE).toOffsetDateTime(), live,
							live ? delay.delaySeconds() : null),
					at));
		}
		expected.sort(Comparator.comparing(Expected::at).thenComparing(e -> e.arrival().tripId()));
		Map<String, RouteArrivals> byRoute = new LinkedHashMap<>();
		for (Expected e : expected) {
			Route r = e.route();
			List<Arrival> arrivals = byRoute
				.computeIfAbsent(r.routeId(),
						id -> new RouteArrivals(id, r.shortName(), r.color(), r.textColor(), new ArrayList<>()))
				.arrivals();
			if (arrivals.size() < PER_ROUTE) {
				arrivals.add(e.arrival());
			}
		}
		return List.copyOf(byRoute.values());
	}

}
