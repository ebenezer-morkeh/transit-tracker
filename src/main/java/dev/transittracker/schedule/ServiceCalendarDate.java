package dev.transittracker.schedule;

import java.time.LocalDate;

/** One row of {@code calendar_dates.txt}: a service is added on, or removed from, one date. */
record ServiceCalendarDate(String serviceId, LocalDate date, boolean added) {
}
