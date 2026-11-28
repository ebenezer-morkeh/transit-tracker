package dev.transittracker.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** Service day arithmetic (see GLOSSARY.md). All five agencies run on America/New_York. */
public final class ServiceDay {

	public static final ZoneId ZONE = ZoneId.of("America/New_York");

	private ServiceDay() {
	}

	/**
	 * The instant of GTFS time {@code seconds} (may exceed 24h) on {@code serviceDate}. GTFS measures from noon minus
	 * 12h: local midnight, except on DST change days, where it keeps wall-clock times right.
	 */
	public static Instant at(LocalDate serviceDate, int seconds) {
		return serviceDate.atTime(LocalTime.NOON).atZone(ZONE).minusHours(12).plusSeconds(seconds).toInstant();
	}

}
