package dev.transittracker.schedule;

import dev.transittracker.ingest.TripPrediction;

import java.io.IOException;
import java.sql.Types;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Static schedule: every agency's routes, stops, trips, service calendars and scheduled stop times, replaced wholesale per agency. */
@Repository
class ScheduleRepository {

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

	ScheduleRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** A source of rows that may fail part way; see {@link #replaceAgency}. */
	interface Rows<T> {

		void forEach(Consumer<T> sink) throws IOException;

	}

	/** One agency's static GTFS files, each read as a stream of rows. */
	interface StaticGtfs {

		Rows<Route> routes();

		Rows<Stop> stops();

		Rows<Trip> trips();

		Rows<ServiceCalendar> calendars();

		Rows<ServiceCalendarDate> calendarDates();

		Rows<ScheduledStopTime> stopTimes();

	}

	private static final List<String> STATIC_TABLES = List.of("route", "stop", "trip", "service_calendar",
			"service_calendar_date", "scheduled_stop_time");

	/**
	 * Replaces all of an agency's static GTFS data with {@code gtfs}, in one transaction: if any file throws, the
	 * agency keeps its previous data, and so it does when routes, stops, trips, stop times or both calendars are empty. Returns the rows read per file.
	 */
	@Transactional(rollbackFor = Exception.class)
	public Map<String, Integer> replaceAgency(String agencyId, StaticGtfs gtfs) throws IOException {
		for (String table : STATIC_TABLES) {
			jdbc.update("DELETE FROM " + table + " WHERE agency_id = ?", agencyId);
		}
		Map<String, Integer> counts = new LinkedHashMap<>();
		counts.put("routes", insertAll(gtfs.routes(), """
				INSERT INTO route (agency_id, route_id, short_name, long_name, color, text_color)
				VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
				""", (ps, r) -> {
			ps.setString(1, agencyId);
			ps.setString(2, r.routeId());
			ps.setString(3, r.shortName());
			ps.setString(4, r.longName());
			ps.setString(5, r.color());
			ps.setString(6, r.textColor());
		}));
		counts.put("stops", insertAll(gtfs.stops(), """
				INSERT INTO stop (agency_id, stop_id, name, latitude, longitude)
				VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
				""", (ps, r) -> {
			ps.setString(1, agencyId);
			ps.setString(2, r.stopId());
			ps.setString(3, r.name());
			ps.setDouble(4, r.latitude());
			ps.setDouble(5, r.longitude());
		}));
		counts.put("trips", insertAll(gtfs.trips(), """
				INSERT INTO trip (agency_id, trip_id, route_id, service_id, direction_id, headsign)
				VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
				""", (ps, r) -> {
			ps.setString(1, agencyId);
			ps.setString(2, r.tripId());
			ps.setString(3, r.routeId());
			ps.setString(4, r.serviceId());
			ps.setObject(5, r.directionId(), Types.SMALLINT);
			ps.setString(6, r.headsign());
		}));
		counts.put("calendars", insertAll(gtfs.calendars(), """
				INSERT INTO service_calendar (agency_id, service_id, monday, tuesday, wednesday, thursday, friday,
				    saturday, sunday, start_date, end_date)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
				""", (ps, r) -> {
			ps.setString(1, agencyId);
			ps.setString(2, r.serviceId());
			for (DayOfWeek day : DayOfWeek.values()) {
				ps.setBoolean(2 + day.getValue(), r.days().contains(day));
			}
			ps.setObject(10, r.startDate());
			ps.setObject(11, r.endDate());
		}));
		counts.put("calendarDates", insertAll(gtfs.calendarDates(), """
				INSERT INTO service_calendar_date (agency_id, service_id, date, added)
				VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING
				""", (ps, r) -> {
			ps.setString(1, agencyId);
			ps.setString(2, r.serviceId());
			ps.setObject(3, r.date());
			ps.setBoolean(4, r.added());
		}));
		// shortcut: a duplicate (trip, sequence) in a vendor feed keeps the first row instead of failing the load.
		int stopTimes = insertAll(gtfs.stopTimes(), """
				INSERT INTO scheduled_stop_time (agency_id, trip_id, stop_sequence, stop_id, arrival_seconds, departure_seconds)
				VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
				""", (ps, r) -> {
			ps.setString(1, agencyId);
			ps.setString(2, r.tripId());
			ps.setInt(3, r.stopSequence());
			ps.setString(4, r.stopId());
			ps.setObject(5, r.arrivalSeconds(), Types.INTEGER);
			ps.setObject(6, r.departureSeconds(), Types.INTEGER);
		});
		counts.put("stopTimes", stopTimes);
		// An empty file means a broken publish, not an agency with no service: keep the previous data.
		for (String file : List.of("routes", "stops", "trips", "stopTimes")) {
			if (counts.get(file) == 0) {
				throw new IllegalStateException(file + " has no rows");
			}
		}
		if (counts.get("calendars") + counts.get("calendarDates") == 0) {
			throw new IllegalStateException("calendar.txt and calendar_dates.txt have no rows");
		}
		return counts;
	}

	/** Batch-inserts every row; returns how many were read. */
	private <T> int insertAll(Rows<T> rows, String sql, ParameterizedPreparedStatementSetter<T> setter)
			throws IOException {
		List<T> batch = new ArrayList<>(INSERT_BATCH_SIZE);
		int[] count = { 0 };
		rows.forEach(row -> {
			batch.add(row);
			count[0]++;
			if (batch.size() == INSERT_BATCH_SIZE) {
				jdbc.batchUpdate(sql, batch, batch.size(), setter);
				batch.clear();
			}
		});
		if (!batch.isEmpty()) {
			jdbc.batchUpdate(sql, batch, batch.size(), setter);
		}
		return count[0];
	}

	List<Route> routes() {
		return jdbc.query("""
				SELECT agency_id, route_id, short_name, long_name, color, text_color FROM route
				ORDER BY agency_id, route_id
				""", (rs, i) -> new Route(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
				rs.getString(5), rs.getString(6)));
	}

	/** Services running on {@code date}: the calendar's weekday and date range, minus removed dates, plus added dates. */
	Set<String> servicesOn(String agencyId, LocalDate date) {
		String day = GtfsFiles.dayColumn(date.getDayOfWeek()); // an enum name, not user input
		return new HashSet<>(jdbc.queryForList("""
				SELECT service_id FROM service_calendar
				WHERE agency_id = ? AND ? BETWEEN start_date AND end_date AND %s
				  AND service_id NOT IN (SELECT service_id FROM service_calendar_date
				                         WHERE agency_id = ? AND date = ? AND NOT added)
				UNION
				SELECT service_id FROM service_calendar_date WHERE agency_id = ? AND date = ? AND added
				""".formatted(day), String.class, agencyId, date, agencyId, date, agencyId, date));
	}

	Optional<Stop> stop(String agencyId, String stopId) {
		return jdbc.query("SELECT stop_id, name, latitude, longitude FROM stop WHERE agency_id = ? AND stop_id = ?",
				(rs, i) -> new Stop(rs.getString(1), rs.getString(2), rs.getDouble(3), rs.getDouble(4)), agencyId,
				stopId)
			.stream()
			.findFirst();
	}

	Optional<Trip> trip(String agencyId, String tripId) {
		return jdbc.query("""
				SELECT trip_id, route_id, service_id, direction_id, headsign FROM trip
				WHERE agency_id = ? AND trip_id = ?
				""", (rs, i) -> new Trip(rs.getString(1), rs.getString(2), rs.getString(3),
				rs.getObject(4, Integer.class), rs.getString(5)), agencyId, tripId)
			.stream()
			.findFirst();
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
