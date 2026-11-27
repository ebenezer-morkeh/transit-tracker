package dev.transittracker.schedule;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

/** Streaming parser for GTFS {@code stop_times.txt}; CSV handling is {@link GtfsCsv}. */
final class StopTimesParser {

	private StopTimesParser() {
	}

	/** Calls {@code sink} once per row. Throws {@link IllegalArgumentException} on a missing column or bad value. */
	static void parse(InputStream in, Consumer<ScheduledStopTime> sink) throws IOException {
		GtfsCsv.read(in, "stop_times.txt",
				List.of("trip_id", "arrival_time", "departure_time", "stop_id", "stop_sequence"),
				r -> sink.accept(new ScheduledStopTime(r.raw("trip_id"), Integer.parseInt(r.required("stop_sequence")),
						r.raw("stop_id"), seconds(r.raw("arrival_time")), seconds(r.raw("departure_time")))));
	}

	/** "H:MM:SS" or "HH:MM:SS", hours may exceed 23. Blank means untimed. */
	static Integer seconds(String time) {
		String t = time.trim();
		if (t.isEmpty()) {
			return null;
		}
		String[] hms = t.split(":");
		if (hms.length != 3) {
			throw new IllegalArgumentException("bad time '" + time + "'");
		}
		return Integer.parseInt(hms[0]) * 3600 + Integer.parseInt(hms[1]) * 60 + Integer.parseInt(hms[2]);
	}

}
