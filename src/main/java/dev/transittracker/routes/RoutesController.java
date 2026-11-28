package dev.transittracker.routes;

import dev.transittracker.schedule.Route;
import dev.transittracker.schedule.RouteDirection;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Routes", description = "Routes from each agency's static schedule, with their stops")
class RoutesController {

	private final RouteService routes;

	RoutesController(RouteService routes) {
		this.routes = routes;
	}

	@GetMapping("/api/routes")
	@Operation(summary = "List every route",
			description = "All agencies' routes, by agency then short name in natural order (2 before 10). Colours are "
					+ "hex without #, null when the agency sets none.")
	List<Route> routes() {
		return routes.routes();
	}

	@GetMapping("/api/agencies/{agency}/routes/{route}/stops")
	@Operation(summary = "A route's stops by direction",
			description = "One entry per direction (directionId null when the agency sets none), labelled with its most "
					+ "common headsign, with stops in the order of that direction's longest trip.")
	@ApiResponse(responseCode = "404", description = "Unknown agency or route")
	List<RouteDirection> stops(@Parameter(description = "Agency ID", example = "goraleigh") @PathVariable String agency,
			@Parameter(description = "Route ID within the agency", example = "1") @PathVariable String route) {
		return routes.directions(agency, route);
	}

}
