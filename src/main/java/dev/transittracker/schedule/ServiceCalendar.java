package dev.transittracker.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * One row of {@code calendar.txt}: a service runs on {@code days} from {@code startDate} to {@code endDate}, inclusive.
 */
record ServiceCalendar(String serviceId, Set<DayOfWeek> days, LocalDate startDate, LocalDate endDate) {
}
