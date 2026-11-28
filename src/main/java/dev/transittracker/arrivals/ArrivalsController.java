package dev.transittracker.arrivals;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Arrivals", description = "When the next buses reach a stop")
class ArrivalsController {

	private final ArrivalService arrivals;

	ArrivalsController(ArrivalService arrivals) {
		this.arrivals = arrivals;
	}

	@GetMapping("/api/agencies/{agency}/stops/{stop}/arrivals")
	@Operation(summary = "A stop's next arrivals",
			description = "Arrivals in the next 60 minutes, up to 3 per route, routes ordered by first arrival. An "
					+ "arrival is live (scheduled time plus the trip's current delay, if under 2 minutes old) or "
					+ "scheduled. Times are ISO-8601 in America/New_York.")
	@ApiResponse(responseCode = "404", description = "Unknown agency or stop")
	StopArrivals arrivals(@Parameter(description = "Agency ID", example = "goraleigh") @PathVariable String agency,
			@Parameter(description = "Stop ID within the agency", example = "1234") @PathVariable String stop) {
		return arrivals.arrivals(agency, stop);
	}

}
