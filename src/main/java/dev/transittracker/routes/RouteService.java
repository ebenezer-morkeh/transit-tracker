package dev.transittracker.routes;

import dev.transittracker.NotFoundException;
import dev.transittracker.schedule.Route;
import dev.transittracker.schedule.RouteDirection;
import dev.transittracker.schedule.ScheduleService;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Routes for riders to pick from, and each route's directions and stops. */
@Service
public class RouteService {

	private static final Pattern LEADING_NUMBER = Pattern.compile("^(\\d+)(.*)$");

	/** Route names in the order riders expect: 2 before 10 before 100X, then anything not starting with a number. */
	static final Comparator<String> NATURAL = (a, b) -> {
		Matcher ma = LEADING_NUMBER.matcher(a);
		Matcher mb = LEADING_NUMBER.matcher(b);
		boolean na = ma.matches();
		boolean nb = mb.matches();
		if (na && nb) {
			int byNumber = new BigInteger(ma.group(1)).compareTo(new BigInteger(mb.group(1)));
			return byNumber != 0 ? byNumber : ma.group(2).compareTo(mb.group(2));
		}
		return na != nb ? (na ? -1 : 1) : a.compareTo(b);
	};

	private final ScheduleService schedule;

	RouteService(ScheduleService schedule) {
		this.schedule = schedule;
	}

	/** Every agency's routes, by agency, then short name (route ID when it has none) in natural order. */
	public List<Route> routes() {
		return schedule.routes()
			.stream()
			.sorted(Comparator.comparing(Route::agencyId)
				.thenComparing(r -> r.shortName() != null ? r.shortName() : r.routeId(), NATURAL))
			.toList();
	}

	public List<RouteDirection> directions(String agencyId, String routeId) {
		if (schedule.route(agencyId, routeId).isEmpty()) {
			throw new NotFoundException("No route " + routeId + " for agency " + agencyId);
		}
		return schedule.directions(agencyId, routeId);
	}

}
