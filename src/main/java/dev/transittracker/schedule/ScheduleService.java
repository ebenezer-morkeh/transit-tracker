package dev.transittracker.schedule;

import dev.transittracker.ingest.TripPrediction;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/** Read access to the static schedule for other features; {@link ScheduleLoader} keeps it current. */
@Service
public class ScheduleService {

	private final ScheduleRepository repository;

	ScheduleService(ScheduleRepository repository) {
		this.repository = repository;
	}

	/**
	 * One query for all of a poll's trips; the returned lookup finds a prediction's scheduled stop by trip + stop
	 * sequence, else stop ID.
	 */
	public Function<TripPrediction, Optional<ScheduledStopTime>> forTrips(String agencyId, Collection<String> tripIds) {
		return repository.forTrips(agencyId, tripIds);
	}

	/** Every agency's routes. */
	public List<Route> routes() {
		return repository.routes();
	}

	public Optional<Stop> stop(String agencyId, String stopId) {
		return repository.stop(agencyId, stopId);
	}

	public Optional<Trip> trip(String agencyId, String tripId) {
		return repository.trip(agencyId, tripId);
	}

	/** Service IDs that run on {@code date} (a service day), from calendar.txt and calendar_dates.txt. */
	public Set<String> servicesOn(String agencyId, LocalDate date) {
		return repository.servicesOn(agencyId, date);
	}

}
