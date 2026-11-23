package dev.transittracker.feed;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import java.time.Instant;
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

	private static String emptyToNull(String s) {
		return s.isEmpty() ? null : s;
	}

}
