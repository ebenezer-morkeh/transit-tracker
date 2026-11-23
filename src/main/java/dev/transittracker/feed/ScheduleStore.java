package dev.transittracker.feed;

import java.io.IOException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Static schedule: every agency's scheduled stop times, replaced wholesale per agency. */
@Repository
public class ScheduleStore {

	private static final int INSERT_BATCH_SIZE = 5000;

	private static final RowMapper<ScheduledStopTime> STOP_TIME_MAPPER = (rs, i) -> new ScheduledStopTime(
			rs.getString(1), rs.getInt(2), rs.getString(3), rs.getObject(4, Integer.class),
			rs.getObject(5, Integer.class));

	private static final String SELECT_TRIPS_STOP_TIMES = """
			SELECT trip_id, stop_sequence, stop_id, arrival_seconds, departure_seconds
			FROM scheduled_stop_time WHERE agency_id = ? AND trip_id = ANY (?)
			ORDER BY trip_id, stop_sequence
			""";

	private final JdbcTemplate jdbc;

	ScheduleStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A source of rows that may fail part way; see {@link #replaceAgency}. */
	public interface Rows {

		void forEach(Consumer<ScheduledStopTime> sink) throws IOException;

	}

	/**
	 * Replaces all of an agency's stop times with {@code rows}, in one transaction: if {@code rows} throws, the agency
	 * keeps its previous schedule, and so it does when {@code rows} is empty. Returns the number of rows read.
	 */
	@Transactional(rollbackFor = Exception.class)
	public int replaceAgency(String agencyId, Rows rows) throws IOException {
		jdbc.update("DELETE FROM scheduled_stop_time WHERE agency_id = ?", agencyId);
		List<ScheduledStopTime> batch = new ArrayList<>(INSERT_BATCH_SIZE);
		int[] count = { 0 };
		rows.forEach(row -> {
			batch.add(row);
			count[0]++;
			if (batch.size() == INSERT_BATCH_SIZE) {
				insert(agencyId, batch);
				batch.clear();
			}
		});
		if (count[0] == 0) {
			throw new IllegalStateException("stop_times.txt has no rows");
		}
		insert(agencyId, batch);
		return count[0];
	}

	private void insert(String agencyId, List<ScheduledStopTime> rows) {
		// shortcut: a duplicate (trip, sequence) in a vendor feed keeps the first row instead of failing the load.
		jdbc.batchUpdate("""
				INSERT INTO scheduled_stop_time (agency_id, trip_id, stop_sequence, stop_id, arrival_seconds, departure_seconds)
				VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
				""", rows, rows.size(), (ps, r) -> {
			ps.setString(1, agencyId);
			ps.setString(2, r.tripId());
			ps.setInt(3, r.stopSequence());
			ps.setString(4, r.stopId());
			ps.setObject(5, r.arrivalSeconds(), Types.INTEGER);
			ps.setObject(6, r.departureSeconds(), Types.INTEGER);
		});
	}

	/**
	 * Loads every stop time of {@code tripIds} in one query and returns a lookup for predictions on those trips: the
	 * scheduled stop time matched by stop sequence, else (no sequence, or no row for it) by stop ID, taking the first
	 * visit when a trip serves the stop twice.
	 */
	public Function<TripPrediction, Optional<ScheduledStopTime>> forTrips(String agencyId, Collection<String> tripIds) {
		Map<String, List<ScheduledStopTime>> byTrip = jdbc
			.query(SELECT_TRIPS_STOP_TIMES, STOP_TIME_MAPPER, agencyId, tripIds.toArray(String[]::new))
			.stream()
			.collect(Collectors.groupingBy(ScheduledStopTime::tripId));
		return p -> {
			List<ScheduledStopTime> stops = byTrip.getOrDefault(p.tripId(), List.of());
			return stops.stream()
				.filter(s -> p.stopSequence() != null && s.stopSequence() == p.stopSequence())
				.findFirst()
				.or(() -> stops.stream().filter(s -> p.stopId() != null && s.stopId().equals(p.stopId())).findFirst());
		};
	}

}
