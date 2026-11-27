package dev.transittracker.schedule;

/** One row of an agency's static {@code routes.txt}. Names, colours (hex, no {@code #}) are null when the feed leaves them blank. */
public record Route(String agencyId, String routeId, String shortName, String longName, String color,
		String textColor) {
}
