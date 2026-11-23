package dev.transittracker.ingest;

import dev.transittracker.vehicles.VehiclePosition;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeEvent;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/** Pure GTFS-realtime parsing: bytes in, records out. No I/O, no Spring. */
public final class FeedParser {

	private FeedParser() {
	}

	public static List<VehiclePosition> parseVehiclePositions(byte[] bytes, String agencyId)
			throws InvalidProtocolBufferException {
		FeedMessage feed = FeedMessage.parseFrom(bytes);
		long headerTimestamp = feed.getHeader().getTimestamp();
		return feed.getEntityList()
			.stream()
			.filter(FeedParser::hasVehicleIdAndPosition)
			.map(e -> toVehiclePosition(e.getVehicle(), agencyId, headerTimestamp))
			.toList();
	}

	// Entity ids are not stable across responses (GoRaleigh numbers them 1..N), so key on the vehicle descriptor.
	private static boolean hasVehicleIdAndPosition(FeedEntity e) {
		return e.hasVehicle() && e.getVehicle().hasPosition() && !e.getVehicle().getVehicle().getId().isEmpty();
	}

	private static VehiclePosition toVehiclePosition(com.google.transit.realtime.GtfsRealtime.VehiclePosition v,
			String agencyId, long headerTimestamp) {
		var trip = v.getTrip();
		var pos = v.getPosition();
		return new VehiclePosition(agencyId, v.getVehicle().getId(), emptyToNull(trip.getTripId()),
				emptyToNull(trip.getRouteId()), pos.getLatitude(), pos.getLongitude(),
				pos.hasBearing() ? pos.getBearing() : null, pos.hasSpeed() ? pos.getSpeed() : null,
				Instant.ofEpochSecond(v.hasTimestamp() ? v.getTimestamp() : headerTimestamp));
	}

	/**
	 * One {@link TripPrediction} per trip update with a trip and route ID, built from its next upcoming stop time
	 * update: the first (skipped and no-data stops aside) whose event has no time (feeds list only future stops) or a
	 * time at or after the feed timestamp. A trip with no upcoming stop keeps only its trip-level delay, and is left out
	 * when it has none.
	 */
	public static List<TripPrediction> parseTripUpdates(byte[] bytes, String agencyId)
			throws InvalidProtocolBufferException {
		FeedMessage feed = FeedMessage.parseFrom(bytes);
		long headerTimestamp = feed.getHeader().getTimestamp();
		return feed.getEntityList()
			.stream()
			.filter(e -> e.hasTripUpdate() && !e.getTripUpdate().getTrip().getTripId().isEmpty()
					&& !e.getTripUpdate().getTrip().getRouteId().isEmpty())
			.map(e -> toPrediction(e.getTripUpdate(), agencyId, headerTimestamp))
			.flatMap(Optional::stream)
			.toList();
	}

	private static Optional<TripPrediction> toPrediction(TripUpdate u, String agencyId, long headerTimestamp) {
		long timestamp = u.hasTimestamp() ? u.getTimestamp() : headerTimestamp;
		StopTimeUpdate next = u.getStopTimeUpdateList()
			.stream()
			.filter(s -> s.getScheduleRelationship() != StopTimeUpdate.ScheduleRelationship.SKIPPED
					&& s.getScheduleRelationship() != StopTimeUpdate.ScheduleRelationship.NO_DATA)
			.filter(s -> !event(s).hasTime() || event(s).getTime() >= timestamp)
			.findFirst()
			.orElse(null);
		var trip = u.getTrip();
		Integer tripDelay = u.hasDelay() ? u.getDelay() : null;
		Instant feedTimestamp = Instant.ofEpochSecond(timestamp);
		String vehicleId = emptyToNull(u.getVehicle().getId());
		if (next == null) {
			return tripDelay == null ? Optional.empty()
					: Optional.of(new TripPrediction(agencyId, trip.getTripId(), trip.getRouteId(), vehicleId,
							startDate(trip.getStartDate()), null, null, null, false, null, tripDelay, feedTimestamp));
		}
		StopTimeEvent event = event(next);
		boolean departure = !hasTimeOrDelay(next.getArrival());
		return Optional.of(new TripPrediction(agencyId, trip.getTripId(), trip.getRouteId(), vehicleId,
				startDate(trip.getStartDate()), next.hasStopSequence() ? next.getStopSequence() : null,
				emptyToNull(next.getStopId()), event.hasTime() ? Instant.ofEpochSecond(event.getTime()) : null,
				departure, event.hasDelay() ? event.getDelay() : null, tripDelay, feedTimestamp));
	}

	/** The arrival if it has a time or delay, else the departure. */
	private static StopTimeEvent event(StopTimeUpdate s) {
		return hasTimeOrDelay(s.getArrival()) ? s.getArrival() : s.getDeparture();
	}

	private static boolean hasTimeOrDelay(StopTimeEvent e) {
		return e.hasTime() || e.hasDelay();
	}

	private static LocalDate startDate(String yyyymmdd) {
		try {
			return yyyymmdd.isEmpty() ? null : LocalDate.parse(yyyymmdd, DateTimeFormatter.BASIC_ISO_DATE);
		}
		catch (DateTimeParseException e) {
			return null;
		}
	}

	private static String emptyToNull(String s) {
		return s.isEmpty() ? null : s;
	}

}
