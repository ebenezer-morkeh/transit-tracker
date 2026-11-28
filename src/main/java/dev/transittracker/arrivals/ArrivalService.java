package dev.transittracker.arrivals;

import dev.transittracker.NotFoundException;
import dev.transittracker.delays.DelayService;
import dev.transittracker.ingest.FeedStatus;
import dev.transittracker.schedule.ScheduleService;
import dev.transittracker.schedule.ScheduledCall;
import dev.transittracker.schedule.ServiceDay;
import dev.transittracker.schedule.Stop;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** A stop's next arrivals, from the static schedule, current trip delays and feed status. */
@Service
public class ArrivalService {

	// shortcut: a trip more than 90 min late or 30 min early is missed; widen if feeds report bigger swings.
	private static final Duration LATE_LOOKBACK = Duration.ofMinutes(90);

	private static final Duration EARLY_LOOKAHEAD = Duration.ofMinutes(30);

	private final ScheduleService schedule;

	private final DelayService delays;

	private final FeedStatus feedStatus;

	private final Clock clock;

	ArrivalService(ScheduleService schedule, DelayService delays, FeedStatus feedStatus, Clock clock) {
		this.schedule = schedule;
		this.delays = delays;
		this.feedStatus = feedStatus;
		this.clock = clock;
	}

	public StopArrivals arrivals(String agencyId, String stopId) {
		Stop stop = schedule.stop(agencyId, stopId)
			.orElseThrow(() -> new NotFoundException("No stop " + stopId + " for agency " + agencyId));
		Instant now = clock.instant();
		LocalDate today = LocalDate.ofInstant(now, ServiceDay.ZONE);
		List<Arrivals.Timed> calls = new ArrayList<>();
		// Yesterday's service day too: its trips past 24:00:00 run this morning.
		for (LocalDate day : List.of(today.minusDays(1), today)) {
			long nowSeconds = Duration.between(ServiceDay.at(day, 0), now).toSeconds();
			int from = Math.toIntExact(nowSeconds - LATE_LOOKBACK.toSeconds());
			int to = Math.toIntExact(nowSeconds + Arrivals.WINDOW.toSeconds() + EARLY_LOOKAHEAD.toSeconds());
			for (ScheduledCall call : schedule.callsAt(agencyId, stopId, day, from, to)) {
				calls.add(new Arrivals.Timed(call, ServiceDay.at(day, call.seconds())));
			}
		}
		var tripDelays = delays.forTrips(agencyId, calls.stream().map(c -> c.call().tripId()).toList());
		return new StopArrivals(agencyId, stop.stopId(), stop.name(), stop.latitude(), stop.longitude(),
				feedStatus.liveDataUnavailable(agencyId), Arrivals.next(calls, tripDelays, now, FeedStatus.LIVE_FOR));
	}

}
