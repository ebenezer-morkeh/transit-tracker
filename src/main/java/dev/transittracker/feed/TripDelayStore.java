package dev.transittracker.feed;

import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Latest state only: one row per (agency, trip), updated in place. */
@Repository
public class TripDelayStore {

	private final JdbcClient jdbc;

	TripDelayStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Saves an agency's latest poll and removes its trips that were not in it. */
	@Transactional
	public void replaceAgency(String agencyId, List<TripDelay> delays) {
		for (TripDelay d : delays) {
			jdbc.sql("""
					INSERT INTO trip_delay (agency_id, trip_id, route_id, vehicle_id, delay_seconds, feed_timestamp, updated_at)
					VALUES (?, ?, ?, ?, ?, ?, now())
					ON CONFLICT (agency_id, trip_id) DO UPDATE SET
					    route_id = EXCLUDED.route_id, vehicle_id = EXCLUDED.vehicle_id,
					    delay_seconds = EXCLUDED.delay_seconds, feed_timestamp = EXCLUDED.feed_timestamp, updated_at = now()
					""")
				.params(d.agencyId(), d.tripId(), d.routeId(), d.vehicleId(), d.delaySeconds(),
						Timestamp.from(d.feedTimestamp()))
				.update();
		}
		jdbc.sql("DELETE FROM trip_delay WHERE agency_id = ? AND trip_id <> ALL (?)")
			.params(agencyId, delays.stream().map(TripDelay::tripId).toArray(String[]::new))
			.update();
	}

	/** Most delayed first. */
	public List<TripDelay> find(String agencyId, String routeId) {
		return jdbc.sql("""
				SELECT agency_id, trip_id, route_id, vehicle_id, delay_seconds, feed_timestamp
				FROM trip_delay WHERE agency_id = ? AND route_id = ?
				ORDER BY delay_seconds DESC, trip_id
				""")
			.params(agencyId, routeId)
			.query((rs, i) -> new TripDelay(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
					rs.getInt(5), rs.getTimestamp(6).toInstant()))
			.list();
	}

}
