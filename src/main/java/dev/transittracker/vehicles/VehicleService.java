package dev.transittracker.vehicles;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Live vehicle positions: queries, the SSE stream, and saving each poll's results. */
@Service
public class VehicleService {

	private final VehicleRepository repository;

	private final VehicleStream stream;

	VehicleService(VehicleRepository repository, VehicleStream stream) {
		this.repository = repository;
		this.stream = stream;
	}

	/** {@code agencyId} and {@code routeId} are optional; a route needs an agency. */
	public List<VehiclePosition> find(String agencyId, String routeId) {
		validate(agencyId, routeId);
		return repository.find(agencyId, routeId);
	}

	/** Same filters as {@link #find}; the emitter gets the filtered list after every poll. */
	public SseEmitter subscribe(String agencyId, String routeId) {
		validate(agencyId, routeId);
		return stream.subscribe(agencyId, routeId);
	}

	/** Replaces an agency's vehicles with one poll's results; vehicles not in the poll are removed. */
	public void replaceAgency(String agencyId, List<VehiclePosition> positions) {
		repository.replaceAgency(agencyId, positions);
	}

	/** Notifies stream subscribers in the background. */
	public void publishUpdates() {
		stream.publish();
	}

	private static void validate(String agencyId, String routeId) {
		if ((agencyId != null && agencyId.isBlank()) || (routeId != null && routeId.isBlank())) {
			throw new InvalidFilterException("agency and route must not be blank");
		}
		if (routeId != null && agencyId == null) {
			// Route IDs are only unique within an agency.
			throw new InvalidFilterException("route requires agency");
		}
	}

	/** A bad agency/route filter; the controller maps it to 400. */
	public static class InvalidFilterException extends RuntimeException {

		InvalidFilterException(String message) {
			super(message);
		}

	}

}
