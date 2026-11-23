package dev.transittracker.vehicles;

import dev.transittracker.vehicles.VehicleService.InvalidFilterException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@Tag(name = "Vehicles", description = "Live vehicle positions across the Triangle agencies")
class VehiclesController {

	private final VehicleService vehicles;

	VehiclesController(VehicleService vehicles) {
		this.vehicles = vehicles;
	}

	@GetMapping("/api/vehicles")
	@Operation(summary = "List vehicles in service", description = "Latest position of every vehicle, optionally "
			+ "filtered by agency and route. A route filter requires an agency, since route IDs repeat across agencies.")
	@ApiResponse(responseCode = "200", description = "Vehicles (empty list if none match)")
	@ApiResponse(responseCode = "400", description = "Blank filter, or route without agency",
			content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
	List<VehiclePosition> vehicles(
			@Parameter(description = "Agency ID", example = "goraleigh") @RequestParam(required = false) String agency,
			@Parameter(description = "Route ID within the agency", example = "1") @RequestParam(
					required = false) String route) {
		return vehicles.find(agency, route);
	}

	@GetMapping(path = "/api/vehicles/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@Operation(summary = "Stream vehicle updates (SSE)",
			description = "Server-Sent Events: after every poll (~15s) each subscriber gets its filtered vehicle list. "
					+ "Swagger UI can't display a stream; use curl -N.")
	@ApiResponse(responseCode = "400", description = "Blank filter, or route without agency")
	SseEmitter stream(
			@Parameter(description = "Agency ID", example = "goraleigh") @RequestParam(required = false) String agency,
			@Parameter(description = "Route ID within the agency", example = "1") @RequestParam(
					required = false) String route) {
		return vehicles.subscribe(agency, route);
	}

	@ExceptionHandler(InvalidFilterException.class)
	ProblemDetail invalidFilter(InvalidFilterException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

}
