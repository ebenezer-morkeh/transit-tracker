package dev.transittracker.feed;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** The agencies to poll. Adding an agency is one more {@code transit.feeds[n]} entry. */
@ConfigurationProperties("transit")
public record FeedProperties(List<Feed> feeds) {

	/** {@code tripUpdatesUrl} is optional and not fetched yet (delays design pending). */
	public record Feed(String agencyId, URI vehiclePositionsUrl, URI tripUpdatesUrl) {
	}

}
