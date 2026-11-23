package dev.transittracker.feed;

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
			.map(e -> toRecord(e.getVehicle(), agencyId, headerTimestamp))
			.toList();
	}

	// Entity ids are not stable across responses (GoRaleigh numbers them 1..N), so key on the vehicle descriptor.
	private static boolean hasVehicleIdAndPosition(FeedEntity e) {
		return e.hasVehicle() && e.getVehicle().hasPosition() && !e.getVehicle().getVehicle().getId().isEmpty();
	}

	private static VehiclePosition toRecord(com.google.transit.realtime.GtfsRealtime.VehiclePosition v,
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
	 * update: the first (skipped and no-data stops aside) whose predicted time is at or after the feed timestamp, else
	 * the first one.
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
			.toList();
	}

	private static TripPrediction toPrediction(TripUpdate u, String agencyId, long headerTimestamp) {
		long timestamp = u.hasTimestamp() ? u.getTimestamp() : headerTimestamp;
		List<StopTimeUpdate> stops = u.getStopTimeUpdateList()
			.stream()
			.filter(s -> s.getScheduleRelationship() != StopTimeUpdate.ScheduleRelationship.SKIPPED
					&& s.getScheduleRelationship() != StopTimeUpdate.ScheduleRelationship.NO_DATA)
			.toList();
		StopTimeUpdate next = stops.stream()
			.filter(s -> event(s).hasTime() && event(s).getTime() >= timestamp)
			.findFirst()
			.orElse(stops.isEmpty() ? null : stops.getFirst());
		var trip = u.getTrip();
		Integer tripDelay = u.hasDelay() ? u.getDelay() : null;
		Instant feedTimestamp = Instant.ofEpochSecond(timestamp);
		String vehicleId = emptyToNull(u.getVehicle().getId());
		if (next == null) {
			return new TripPrediction(agencyId, trip.getTripId(), trip.getRouteId(), vehicleId,
					startDate(trip.getStartDate()), null, null, null, false, null, tripDelay, feedTimestamp);
		}
		StopTimeEvent event = event(next);
		return new TripPrediction(agencyId, trip.getTripId(), trip.getRouteId(), vehicleId,
				startDate(trip.getStartDate()), next.hasStopSequence() ? next.getStopSequence() : null,
				emptyToNull(next.getStopId()), event.hasTime() ? Instant.ofEpochSecond(event.getTime()) : null,
				!next.hasArrival(), event.hasDelay() ? event.getDelay() : null, tripDelay, feedTimestamp);
	}

	private static StopTimeEvent event(StopTimeUpdate s) {
		return s.hasArrival() ? s.getArrival() : s.getDeparture();
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
