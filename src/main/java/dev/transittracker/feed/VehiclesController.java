package dev.transittracker.feed;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class VehiclesController {

	private final VehiclePositionStore store;

	VehiclesController(VehiclePositionStore store) {
		this.store = store;
	}

	@GetMapping("/api/vehicles")
	List<VehiclePosition> vehicles() {
		return store.findAll();
	}

}
