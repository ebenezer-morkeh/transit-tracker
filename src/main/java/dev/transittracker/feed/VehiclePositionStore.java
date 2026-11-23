package dev.transittracker.feed;

import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Latest state only: one row per (agency, vehicle), updated in place. */
@Repository
public class VehiclePositionStore {

	private final JdbcClient jdbc;

	VehiclePositionStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional
	public void upsert(List<VehiclePosition> positions) {
		for (VehiclePosition p : positions) {
			jdbc.sql("""
					INSERT INTO vehicle_position (agency_id, vehicle_id, trip_id, route_id, latitude, longitude,
					                              bearing, speed, feed_timestamp, updated_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
					ON CONFLICT (agency_id, vehicle_id) DO UPDATE SET
					    trip_id = EXCLUDED.trip_id, route_id = EXCLUDED.route_id,
					    latitude = EXCLUDED.latitude, longitude = EXCLUDED.longitude,
					    bearing = EXCLUDED.bearing, speed = EXCLUDED.speed,
					    feed_timestamp = EXCLUDED.feed_timestamp, updated_at = now()
					""")
				.params(p.agencyId(), p.vehicleId(), p.tripId(), p.routeId(), p.latitude(), p.longitude(), p.bearing(),
						p.speed(), Timestamp.from(p.feedTimestamp()))
				.update();
		}
	}

	public List<VehiclePosition> findAll() {
		return jdbc.sql("""
				SELECT agency_id, vehicle_id, trip_id, route_id, latitude, longitude, bearing, speed, feed_timestamp
				FROM vehicle_position ORDER BY agency_id, vehicle_id
				""")
			.query((rs, i) -> new VehiclePosition(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
					rs.getDouble(5), rs.getDouble(6), rs.getObject(7, Float.class), rs.getObject(8, Float.class),
					rs.getTimestamp(9).toInstant()))
			.list();
	}

}
