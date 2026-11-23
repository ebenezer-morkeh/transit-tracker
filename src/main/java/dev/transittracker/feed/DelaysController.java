package dev.transittracker.feed;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DelaysController {

	private final TripDelayStore store;

	DelaysController(TripDelayStore store) {
		this.store = store;
	}

	@GetMapping("/api/agencies/{agency}/routes/{route}/delays")
	List<TripDelay> delays(@PathVariable String agency, @PathVariable String route) {
		return store.find(agency, route);
	}

}
