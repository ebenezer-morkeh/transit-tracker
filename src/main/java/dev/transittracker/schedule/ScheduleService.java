package dev.transittracker.schedule;

import dev.transittracker.ingest.TripPrediction;
import java.util.Collection;
import java.util.Optional;
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

}
