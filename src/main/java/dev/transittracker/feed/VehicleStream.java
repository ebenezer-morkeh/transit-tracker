package dev.transittracker.feed;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** SSE subscribers to the live vehicle list. Each gets its own filtered list after every poll cycle. */
@Component
public class VehicleStream {

	private record Subscriber(SseEmitter emitter, String agencyId, String routeId) {
	}

	private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

	private final VehiclePositionStore store;

	VehicleStream(VehiclePositionStore store) {
		this.store = store;
	}

	/** {@code agencyId} and {@code routeId} are optional, as in {@link VehiclePositionStore#find}. */
	public SseEmitter subscribe(String agencyId, String routeId) {
		SseEmitter emitter = new SseEmitter(0L); // no timeout; dead clients are dropped when a send fails
		Subscriber subscriber = new Subscriber(emitter, agencyId, routeId);
		subscribers.add(subscriber);
		emitter.onCompletion(() -> subscribers.remove(subscriber));
		emitter.onTimeout(() -> subscribers.remove(subscriber));
		emitter.onError(e -> subscribers.remove(subscriber));
		return emitter;
	}

	/** Called by the poller after each cycle. */
	public void publish() {
		for (Subscriber s : subscribers) {
			try {
				s.emitter().send(SseEmitter.event().data(store.find(s.agencyId(), s.routeId()), MediaType.APPLICATION_JSON));
			}
			catch (IOException | IllegalStateException e) {
				// Client went away (or emitter already completed): forget it.
				subscribers.remove(s);
				s.emitter().completeWithError(e);
			}
		}
	}

}
