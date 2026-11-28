package dev.transittracker.schedule;

/** One stop or station from an agency's static {@code stops.txt}. */
public record Stop(String stopId, String name, double latitude, double longitude) {
}
