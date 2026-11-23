package dev.transittracker.feed;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;

/** HTTP shared by {@link FeedPoller} and {@link ScheduleLoader}: agency URLs redirect, and only a 200 is a feed. */
final class FeedHttp {

	private FeedHttp() {
	}

	static HttpClient client(Duration connectTimeout) {
		return HttpClient.newBuilder().connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NORMAL).build();
	}

	/** The body of a 200 response; any other status throws. */
	static <T> T okBody(HttpResponse<T> response) {
		if (response.statusCode() != 200) {
			throw new IllegalStateException("HTTP " + response.statusCode());
		}
		return response.body();
	}

}
