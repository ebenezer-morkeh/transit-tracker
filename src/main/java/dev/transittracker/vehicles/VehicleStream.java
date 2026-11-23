package dev.transittracker.vehicles;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** SSE subscribers to the live vehicle list. Each gets its own filtered list after every poll cycle. */
@Component
class VehicleStream {

	private record Filter(String agencyId, String routeId) {
	}

	private record Subscriber(SseEmitter emitter, Filter filter) {
	}

	private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

	// One thread: publishes run in order and never send to an emitter concurrently. Virtual, so it never blocks JVM exit.
	private final ExecutorService publisher = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());

	private final VehicleRepository repository;

	VehicleStream(VehicleRepository repository) {
		this.repository = repository;
	}

	/** {@code agencyId} and {@code routeId} are optional, as in {@link VehicleRepository#find}. */
	SseEmitter subscribe(String agencyId, String routeId) {
		SseEmitter emitter = new SseEmitter(0L); // no timeout; dead clients are dropped when a send fails
		Subscriber subscriber = new Subscriber(emitter, new Filter(agencyId, routeId));
		subscribers.add(subscriber);
		emitter.onCompletion(() -> subscribers.remove(subscriber));
		emitter.onTimeout(() -> subscribers.remove(subscriber));
		emitter.onError(e -> subscribers.remove(subscriber));
		return emitter;
	}

	/** Called by the poller after each cycle; returns at once and sends in the background. */
	void publish() {
		publisher.execute(this::send);
	}

	private void send() {
		Map<Filter, List<VehiclePosition>> vehiclesByFilter = new HashMap<>();
		for (Subscriber s : subscribers) {
			List<VehiclePosition> vehicles = vehiclesByFilter.computeIfAbsent(s.filter(),
					f -> repository.find(f.agencyId(), f.routeId()));
			try {
				s.emitter().send(SseEmitter.event().data(vehicles, MediaType.APPLICATION_JSON));
			}
			catch (IOException | IllegalStateException e) {
				// Client went away (or emitter already completed): forget it.
				subscribers.remove(s);
				s.emitter().completeWithError(e);
			}
		}
	}

}
