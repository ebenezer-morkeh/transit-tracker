package dev.transittracker.feed;

import java.io.IOException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Static schedule: every agency's scheduled stop times, replaced wholesale per agency. */
@Repository
public class ScheduleStore {

	private static final int BATCH = 5000;

	private static final RowMapper<ScheduledStopTime> ROW = (rs, i) -> new ScheduledStopTime(rs.getString(1),
			rs.getInt(2), rs.getString(3), rs.getObject(4, Integer.class), rs.getObject(5, Integer.class));

	private static final String SELECT = """
			SELECT trip_id, stop_sequence, stop_id, arrival_seconds, departure_seconds
			FROM scheduled_stop_time WHERE agency_id = ? AND trip_id = ?
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
	 * keeps its previous schedule. Returns the number of rows read.
	 */
	@Transactional(rollbackFor = Exception.class)
	public int replaceAgency(String agencyId, Rows rows) throws IOException {
		jdbc.update("DELETE FROM scheduled_stop_time WHERE agency_id = ?", agencyId);
		List<ScheduledStopTime> batch = new ArrayList<>(BATCH);
		int[] count = { 0 };
		rows.forEach(row -> {
			batch.add(row);
			count[0]++;
			if (batch.size() == BATCH) {
				insert(agencyId, batch);
				batch.clear();
			}
		});
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
	 * The scheduled stop time for a realtime stop time update: matched by stop sequence, else (no sequence, or no row
	 * for it) by stop ID, taking the first visit when a trip serves the stop twice. Both may be null.
	 */
	public Optional<ScheduledStopTime> find(String agencyId, String tripId, Integer stopSequence, String stopId) {
		if (stopSequence != null) {
			Optional<ScheduledStopTime> bySequence = jdbc
				.query(SELECT + " AND stop_sequence = ?", ROW, agencyId, tripId, stopSequence)
				.stream()
				.findFirst();
			if (bySequence.isPresent() || stopId == null) {
				return bySequence;
			}
		}
		if (stopId == null) {
			return Optional.empty();
		}
		return jdbc.query(SELECT + " AND stop_id = ? ORDER BY stop_sequence LIMIT 1", ROW, agencyId, tripId, stopId)
			.stream()
			.findFirst();
	}

}
