package dev.transittracker.delays;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Delays", description = "How late each active trip is running")
class DelaysController {

	private final DelayService delays;

	DelaysController(DelayService delays) {
		this.delays = delays;
	}

	@GetMapping("/api/agencies/{agency}/routes/{route}/delays")
	@Operation(summary = "Current delays on a route",
			description = "One entry per active trip, most delayed first. delaySeconds: positive is late, negative is "
					+ "early. Uses the feed's delay when present, else predicted minus scheduled time.")
	List<TripDelay> delays(@Parameter(description = "Agency ID", example = "goraleigh") @PathVariable String agency,
			@Parameter(description = "Route ID within the agency", example = "1") @PathVariable String route) {
		return delays.find(agency, route);
	}

}
