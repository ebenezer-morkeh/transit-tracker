package dev.transittracker.feed;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class VehiclesController {

	private final VehiclePositionStore store;

	VehiclesController(VehiclePositionStore store) {
		this.store = store;
	}

	@GetMapping("/api/vehicles")
	List<VehiclePosition> vehicles(@RequestParam(required = false) String agency,
			@RequestParam(required = false) String route) {
		if (route != null && agency == null) {
			// Route IDs are only unique within an agency.
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "route requires agency");
		}
		return store.find(agency, route);
	}

}
