package dev.transittracker.ingest;

import dev.transittracker.vehicles.VehiclePosition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.google.transit.realtime.GtfsRealtime;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.Position;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeedParserTests {

	static byte[] fixture() throws Exception {
		try (var in = FeedParserTests.class.getResourceAsStream("/fixtures/goraleigh-vehicles.pb")) {
			return in.readAllBytes();
		}
	}

	@Test
	void parsesEveryVehicleInTheGoRaleighFixture() throws Exception {
		List<VehiclePosition> vehicles = FeedParser.parseVehiclePositions(fixture(), "goraleigh");

		assertThat(vehicles).hasSize(49).allSatisfy(v -> assertThat(v.agencyId()).isEqualTo("goraleigh"));
		VehiclePosition first = vehicles.getFirst();
		assertThat(first.vehicleId()).isEqualTo("2110");
		assertThat(first.tripId()).isEqualTo("4227010");
		assertThat(first.routeId()).isEqualTo("18L");
		assertThat(first.latitude()).isCloseTo(35.766834, within(1e-5));
		assertThat(first.longitude()).isCloseTo(-78.56409, within(1e-5));
		assertThat(first.bearing()).isEqualTo(22.0f);
		assertThat(first.speed()).isEqualTo(0.0f);
		assertThat(first.feedTimestamp()).isEqualTo(Instant.ofEpochSecond(1795397431));
	}

	@Test
	void skipsVehicleWithNoPosition() throws Exception {
		byte[] bytes = feed(entity("a", GtfsRealtime.VehiclePosition.newBuilder().setVehicle(vehicle("1"))),
				entity("b", GtfsRealtime.VehiclePosition.newBuilder().setVehicle(vehicle("2")).setPosition(position())));

		assertThat(FeedParser.parseVehiclePositions(bytes, "x")).extracting(VehiclePosition::vehicleId)
			.containsExactly("2");
	}

	@Test
	void allowsMissingTripRouteBearingAndSpeedAndFallsBackToHeaderTimestamp() throws Exception {
		byte[] bytes = feed(entity("a", GtfsRealtime.VehiclePosition.newBuilder()
			.setVehicle(vehicle("7"))
			.setPosition(Position.newBuilder().setLatitude(1f).setLongitude(2f))));

		assertThat(FeedParser.parseVehiclePositions(bytes, "x"))
			.containsExactly(new VehiclePosition("x", "7", null, null, 1.0, 2.0, null, null, Instant.ofEpochSecond(100)));
	}

	@Test
	void emptyFeedReturnsEmptyList() throws Exception {
		assertThat(FeedParser.parseVehiclePositions(feed(), "x")).isEmpty();
	}

	static VehicleDescriptor vehicle(String id) {
		return VehicleDescriptor.newBuilder().setId(id).build();
	}

	static Position position() {
		return Position.newBuilder().setLatitude(35f).setLongitude(-78f).build();
	}

	static FeedEntity entity(String id, GtfsRealtime.VehiclePosition.Builder v) {
		return FeedEntity.newBuilder().setId(id).setVehicle(v).build();
	}

	static byte[] feed(FeedEntity... entities) {
		return FeedMessage.newBuilder()
			.setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").setTimestamp(100))
			.addAllEntity(List.of(entities))
			.build()
			.toByteArray();
	}

}
