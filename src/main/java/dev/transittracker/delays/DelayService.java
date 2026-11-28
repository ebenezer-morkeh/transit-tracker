package dev.transittracker.delays;

import dev.transittracker.ingest.TripPrediction;
import dev.transittracker.schedule.ScheduleService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Trip delays: queries, and turning each poll's trip predictions into delays. */
@Service
public class DelayService {

	private final TripDelayRepository repository;

	private final ScheduleService schedule;

	DelayService(TripDelayRepository repository, ScheduleService schedule) {
		this.repository = repository;
		this.schedule = schedule;
	}

	/** Active trips on a route, most delayed first. */
	public List<TripDelay> find(String agencyId, String routeId) {
		return repository.find(agencyId, routeId);
	}

	/** The current trip delays of {@code tripIds}, by trip ID; trips without one are absent. */
	public Map<String, TripDelay> forTrips(String agencyId, Collection<String> tripIds) {
		return repository.forTrips(agencyId, tripIds)
			.stream()
			.collect(Collectors.toMap(TripDelay::tripId, Function.identity()));
	}

	/**
	 * Resolves each prediction's delay (see {@link TripDelays#resolve}) using one schedule query for the whole poll,
	 * then replaces the agency's delays; trips not in the poll are removed. Returns how many delays were saved.
	 */
	public int replaceAgency(String agencyId, List<TripPrediction> predictions) {
		var scheduledStopTime = schedule.forTrips(agencyId, predictions.stream().map(TripPrediction::tripId).toList());
		List<TripDelay> delays = predictions.stream()
			.map(p -> TripDelays.resolve(p, scheduledStopTime))
			.flatMap(Optional::stream)
			.toList();
		repository.replaceAgency(agencyId, delays);
		return delays.size();
	}

}
