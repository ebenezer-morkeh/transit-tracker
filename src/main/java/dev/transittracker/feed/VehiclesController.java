package dev.transittracker.feed;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
class VehiclesController {

	private final VehiclePositionStore store;

	private final VehicleStream stream;

	VehiclesController(VehiclePositionStore store, VehicleStream stream) {
		this.store = store;
		this.stream = stream;
	}

	@GetMapping("/api/vehicles")
	List<VehiclePosition> vehicles(@RequestParam(required = false) String agency,
			@RequestParam(required = false) String route) {
		validate(agency, route);
		return store.find(agency, route);
	}

	@GetMapping("/api/vehicles/stream")
	SseEmitter stream(@RequestParam(required = false) String agency, @RequestParam(required = false) String route) {
		validate(agency, route);
		return stream.subscribe(agency, route);
	}

	private static void validate(String agency, String route) {
		if ((agency != null && agency.isBlank()) || (route != null && route.isBlank())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "agency and route must not be blank");
		}
		if (route != null && agency == null) {
			// Route IDs are only unique within an agency.
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "route requires agency");
		}
	}

}
