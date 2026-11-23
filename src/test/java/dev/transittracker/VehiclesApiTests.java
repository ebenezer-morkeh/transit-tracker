package dev.transittracker;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.Position;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.sun.net.httpserver.HttpServer;
import dev.transittracker.feed.VehiclePoller;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VehiclesApiTests {

	/** Stub feed server (JDK built-in). Tests swap {@link #feedBytes} to change what the next poll sees. */
	static volatile byte[] feedBytes;

	static final HttpServer feedServer = startFeedServer();

	@Autowired
	MockMvc mvc;

	@Autowired
	VehiclePoller poller;

	static HttpServer startFeedServer() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/goraleigh/vehicles", exchange -> {
				byte[] body = feedBytes;
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
				exchange.close();
			});
			server.start();
			return server;
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void feedUrl(DynamicPropertyRegistry registry) {
		registry.add("transit.feed.vehicle-positions-url",
				() -> "http://127.0.0.1:" + feedServer.getAddress().getPort() + "/goraleigh/vehicles");
	}

	@AfterAll
	static void stopFeedServer() {
		feedServer.stop(0);
	}

	static byte[] fixture() throws IOException {
		try (var in = VehiclesApiTests.class.getResourceAsStream("/fixtures/goraleigh-vehicles.pb")) {
			return in.readAllBytes();
		}
	}

	@Test
	void servesFixtureVehiclesAfterOnePollAndUpdatesThemInPlace() throws Exception {
		feedBytes = fixture();
		poller.pollOnce();

		mvc.perform(get("/api/vehicles"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(49)))
			.andExpect(jsonPath("$[?(@.vehicleId == '2110')].agencyId", contains("goraleigh")))
			.andExpect(jsonPath("$[?(@.vehicleId == '2110')].tripId", contains("4227010")))
			.andExpect(jsonPath("$[?(@.vehicleId == '2110')].routeId", contains("18L")))
			.andExpect(jsonPath("$[?(@.vehicleId == '2110')].latitude", contains(closeTo(35.766834, 1e-5))))
			.andExpect(jsonPath("$[?(@.vehicleId == '2110')].longitude", contains(closeTo(-78.56409, 1e-5))))
			.andExpect(jsonPath("$[?(@.vehicleId == '2227')].bearing", contains(closeTo(169.0, 1e-5))))
			.andExpect(jsonPath("$[?(@.vehicleId == '2227')].speed", contains(closeTo(8.9408, 1e-4))))
			.andExpect(jsonPath("$[?(@.vehicleId == '2110')].feedTimestamp",
					contains(Instant.ofEpochSecond(1795397431).toString())));

		// Same vehicle moves in the next poll: its row is updated, nothing is duplicated.
		feedBytes = FeedMessage.newBuilder()
			.setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").setTimestamp(1795397500))
			.addEntity(FeedEntity.newBuilder()
				.setId("1")
				.setVehicle(VehiclePosition.newBuilder()
					.setVehicle(VehicleDescriptor.newBuilder().setId("2110"))
					.setPosition(Position.newBuilder().setLatitude(35.5f).setLongitude(-78.5f))))
			.build()
			.toByteArray();
		poller.pollOnce();
		poller.pollOnce();

		mvc.perform(get("/api/vehicles"))
			.andExpect(jsonPath("$", hasSize(49)))
			.andExpect(jsonPath("$[?(@.vehicleId == '2110')].latitude", contains(closeTo(35.5, 1e-5))))
			.andExpect(jsonPath("$[?(@.vehicleId == '2110')].routeId", contains((Object) null)));
	}

}
