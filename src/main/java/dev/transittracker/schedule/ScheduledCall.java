package dev.transittracker.schedule;

/**
 * A trip's scheduled call at one stop, with what a rider needs to recognise it. {@code seconds} is the arrival (else
 * departure) time after the service day's reference time, and may exceed 24h.
 */
public record ScheduledCall(String tripId, String headsign, Route route, int seconds) {
}
