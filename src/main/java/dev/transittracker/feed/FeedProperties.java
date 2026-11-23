package dev.transittracker.feed;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** The agencies to poll. Adding an agency is one more {@code transit.feeds[n]} entry. */
@ConfigurationProperties("transit")
public record FeedProperties(List<Feed> feeds) {

	/**
	 * {@code tripUpdatesUrl} is optional; without it the agency has vehicle positions but no delays. {@code staticGtfsUrl} is the
	 * agency's static GTFS zip, optional; its {@code stop_times.txt} is loaded by {@link ScheduleLoader}.
	 */
	public record Feed(String agencyId, URI vehiclePositionsUrl, URI tripUpdatesUrl, URI staticGtfsUrl) {
	}

}
