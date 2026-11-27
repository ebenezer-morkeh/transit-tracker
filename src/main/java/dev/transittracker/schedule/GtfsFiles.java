package dev.transittracker.schedule;

import java.io.IOException;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Pure parsers for the static GTFS files besides {@code stop_times.txt} (see {@link StopTimesParser}). */
final class GtfsFiles {

	private GtfsFiles() {
	}

	static void routes(String agencyId, InputStream in, Consumer<Route> sink) throws IOException {
		GtfsCsv.read(in, "routes.txt", List.of("route_id"), r -> sink.accept(new Route(agencyId,
				r.required("route_id"), r.optional("route_short_name"), r.optional("route_long_name"),
				r.optional("route_color"), r.optional("route_text_color"))));
	}

	/** Stops and stations; generic nodes and boarding areas (location types 3, 4) have no place a rider waits, so they're skipped. */
	static void stops(InputStream in, Consumer<Stop> sink) throws IOException {
		GtfsCsv.read(in, "stops.txt", List.of("stop_id"), r -> {
			String type = r.optional("location_type");
			if ("3".equals(type) || "4".equals(type)) {
				return;
			}
			sink.accept(new Stop(r.required("stop_id"), r.required("stop_name"),
					Double.parseDouble(r.required("stop_lat")), Double.parseDouble(r.required("stop_lon"))));
		});
	}

	static void trips(InputStream in, Consumer<Trip> sink) throws IOException {
		GtfsCsv.read(in, "trips.txt", List.of("route_id", "service_id", "trip_id"), r -> {
			String direction = r.optional("direction_id");
			sink.accept(new Trip(r.required("trip_id"), r.required("route_id"), r.required("service_id"),
					direction == null ? null : Integer.valueOf(direction), r.optional("trip_headsign")));
		});
	}

	static void calendars(InputStream in, Consumer<ServiceCalendar> sink) throws IOException {
		List<String> columns = new ArrayList<>(List.of("service_id", "start_date", "end_date"));
		for (DayOfWeek day : DayOfWeek.values()) {
			columns.add(dayColumn(day));
		}
		GtfsCsv.read(in, "calendar.txt", columns, r -> {
			Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
			for (DayOfWeek day : DayOfWeek.values()) {
				if ("1".equals(r.required(dayColumn(day)))) {
					days.add(day);
				}
			}
			sink.accept(new ServiceCalendar(r.required("service_id"), days, date(r.required("start_date")),
					date(r.required("end_date"))));
		});
	}

	static void calendarDates(InputStream in, Consumer<ServiceCalendarDate> sink) throws IOException {
		GtfsCsv.read(in, "calendar_dates.txt", List.of("service_id", "date", "exception_type"), r -> {
			boolean added = switch (r.required("exception_type")) {
				case "1" -> true;
				case "2" -> false;
				default -> throw new IllegalArgumentException("exception_type must be 1 or 2");
			};
			sink.accept(new ServiceCalendarDate(r.required("service_id"), date(r.required("date")), added));
		});
	}

	/** {@code calendar.txt}'s column for a weekday: "monday" ... "sunday". */
	static String dayColumn(DayOfWeek day) {
		return day.name().toLowerCase(Locale.ROOT);
	}

	private static LocalDate date(String yyyymmdd) {
		return LocalDate.parse(yyyymmdd, DateTimeFormatter.BASIC_ISO_DATE);
	}

}
