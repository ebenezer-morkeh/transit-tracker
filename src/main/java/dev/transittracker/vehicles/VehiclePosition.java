package dev.transittracker.vehicles;

import java.time.Instant;

/** Latest reported position of one vehicle. Nullable: tripId, routeId, bearing, speed. */
public record VehiclePosition(String agencyId, String vehicleId, String tripId, String routeId, double latitude,
		double longitude, Float bearing, Float speed, Instant feedTimestamp) {
}
