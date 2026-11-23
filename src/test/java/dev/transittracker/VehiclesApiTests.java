package dev.transittracker;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.Position;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.sun.net.httpserver.HttpServer;
import dev.transittracker.feed.FeedPoller;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VehiclesApiTests {

	/** Stub feed responses by path ("/{agency}/vehicles"). Tests swap entries to change what the next poll sees. */
	static final Map<String, StubResponse> feeds = new ConcurrentHashMap<>();

	record StubResponse(int status, byte[] body) {
	}

	static final HttpServer feedServer = startFeedServer();

	@Autowired
	MockMvc mvc;

	@Autowired
	FeedPoller poller;

	static HttpServer startFeedServer() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/", exchange -> {
				StubResponse r = feeds.getOrDefault(exchange.getRequestURI().getPath(), new StubResponse(404, new byte[0]));
				exchange.sendResponseHeaders(r.status(), r.body().length == 0 ? -1 : r.body().length);
				exchange.getResponseBody().write(r.body());
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
	static void feedUrls(DynamicPropertyRegistry registry) {
		String base = "http://127.0.0.1:" + feedServer.getAddress().getPort();
		// Replaces the whole transit.feeds list from application.properties with two stub agencies.
		registry.add("transit.feeds[0].agency-id", () -> "goraleigh");
		registry.add("transit.feeds[0].vehicle-positions-url", () -> base + "/goraleigh/vehicles");
		registry.add("transit.feeds[1].agency-id", () -> "godurham");
		registry.add("transit.feeds[1].vehicle-positions-url", () -> base + "/godurham/vehicles");
	}

	@BeforeEach
	void bothAgenciesEmpty() {
		serve("goraleigh", feed());
		serve("godurham", feed());
	}

	static void serve(String agency, byte[] body) {
		feeds.put("/" + agency + "/vehicles", new StubResponse(200, body));
	}

	/** A feed with one vehicle per "vehicleId:routeId" pair. */
	static byte[] feed(String... vehicles) {
		FeedMessage.Builder msg = FeedMessage.newBuilder()
			.setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").setTimestamp(1795397500));
		for (String v : vehicles) {
			String[] idRoute = v.split(":");
			msg.addEntity(FeedEntity.newBuilder()
				.setId(idRoute[0])
				.setVehicle(VehiclePosition.newBuilder()
					.setVehicle(VehicleDescriptor.newBuilder().setId(idRoute[0]))
					.setTrip(TripDescriptor.newBuilder().setRouteId(idRoute[1]))
					.setPosition(Position.newBuilder().setLatitude(35.9f).setLongitude(-78.9f))));
		}
		return msg.build().toByteArray();
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
		serve("goraleigh", fixture());
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
		serve("goraleigh", FeedMessage.newBuilder()
			.setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").setTimestamp(1795397500))
			.addEntity(FeedEntity.newBuilder()
				.setId("1")
				.setVehicle(VehiclePosition.newBuilder()
					.setVehicle(VehicleDescriptor.newBuilder().setId("2110"))
					.setPosition(Position.newBuilder().setLatitude(35.5f).setLongitude(-78.5f))))
			.build()
			.toByteArray());
		poller.pollOnce();
		poller.pollOnce();

		mvc.perform(get("/api/vehicles"))
			.andExpect(jsonPath("$[?(@.vehicleId == '2110')].latitude", contains(closeTo(35.5, 1e-5))))
			.andExpect(jsonPath("$[?(@.vehicleId == '2110')].routeId", contains((Object) null)));
	}

	@Test
	void vehicleMissingFromTheNextPollDisappears() throws Exception {
		serve("goraleigh", feed("100:1", "101:1"));
		poller.pollOnce();
		serve("goraleigh", feed("101:1"));
		poller.pollOnce();

		mvc.perform(get("/api/vehicles")).andExpect(jsonPath("$[*].vehicleId", contains("101")));
	}

	@Test
	void routeWithoutAgencyIs400AndUnknownAgencyIsEmpty() throws Exception {
		mvc.perform(get("/api/vehicles?route=1")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/vehicles?agency=nope")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void blankAgencyOrRouteIs400() throws Exception {
		for (String path : new String[] { "/api/vehicles", "/api/vehicles/stream" }) {
			mvc.perform(get(path).param("agency", "").param("route", "1")).andExpect(status().isBadRequest());
			mvc.perform(get(path).param("agency", "")).andExpect(status().isBadRequest());
			mvc.perform(get(path).param("agency", " ")).andExpect(status().isBadRequest());
			mvc.perform(get(path).param("agency", "goraleigh").param("route", "")).andExpect(status().isBadRequest());
		}
	}

	@Test
	void failingOrGarbageFeedDoesNotAffectOtherAgencies() throws Exception {
		serve("godurham", feed("200:1"));
		serve("goraleigh", feed("100:1"));
		poller.pollOnce();

		// GoRaleigh breaks (500, then garbage): GoDurham still updates, GoRaleigh keeps its last good data.
		feeds.put("/goraleigh/vehicles", new StubResponse(500, new byte[0]));
		serve("godurham", feed("201:1"));
		poller.pollOnce();
		mvc.perform(get("/api/vehicles"))
			.andExpect(jsonPath("$[*].vehicleId", contains("201", "100")));

		serve("goraleigh", new byte[] { (byte) 0xff, (byte) 0xff, 0x01, 0x02 });
		serve("godurham", feed("202:1"));
		poller.pollOnce();
		mvc.perform(get("/api/vehicles"))
			.andExpect(jsonPath("$[*].vehicleId", contains("202", "100")));
	}

	@Test
	void agenciesSharingARouteIdDoNotCollideAndFiltersNarrowResults() throws Exception {
		serve("goraleigh", feed("100:1", "101:2"));
		serve("godurham", feed("100:1"));
		poller.pollOnce();

		mvc.perform(get("/api/vehicles")).andExpect(jsonPath("$", hasSize(3)));
		mvc.perform(get("/api/vehicles?agency=godurham"))
			.andExpect(jsonPath("$[*].agencyId", contains("godurham")));
		mvc.perform(get("/api/vehicles?agency=goraleigh&route=1"))
			.andExpect(jsonPath("$[*].agencyId", contains("goraleigh")))
			.andExpect(jsonPath("$[*].vehicleId", contains("100")));
	}

	@Test
	void streamSubscriberGetsItsFilteredVehiclesAfterEachPoll() throws Exception {
		mvc.perform(get("/api/vehicles/stream?route=1")).andExpect(status().isBadRequest());
		MvcResult raleigh1 = mvc.perform(get("/api/vehicles/stream?agency=goraleigh&route=1"))
			.andExpect(request().asyncStarted())
			.andReturn();
		MvcResult all = mvc.perform(get("/api/vehicles/stream")).andExpect(request().asyncStarted()).andReturn();

		serve("goraleigh", feed("100:1", "101:2"));
		serve("godurham", feed("200:1"));
		poller.pollOnce();
		await().until(() -> all.getResponse().getContentAsString().contains("\"200\""));

		String raleighEvents = raleigh1.getResponse().getContentAsString();
		assertThat(raleigh1.getResponse().getContentType(), containsString("text/event-stream"));
		assertThat(raleighEvents, containsString("data:[{\"agencyId\":\"goraleigh\",\"vehicleId\":\"100\""));
		assertThat(raleighEvents, not(containsString("\"101\"")));
		assertThat(raleighEvents, not(containsString("\"200\"")));
		assertThat(all.getResponse().getContentAsString(), containsString("\"200\""));

		// One client disconnects: later polls still reach the other one.
		raleigh1.getRequest().getAsyncContext().complete();
		serve("godurham", feed("201:1"));
		poller.pollOnce();
		await().until(() -> all.getResponse().getContentAsString().contains("\"201\""));
	}

	@Test
	void streamSkipsACycleWhereEveryVehicleFeedFailed() throws Exception {
		MvcResult all = mvc.perform(get("/api/vehicles/stream")).andExpect(request().asyncStarted()).andReturn();

		feeds.put("/goraleigh/vehicles", new StubResponse(500, new byte[0]));
		feeds.put("/godurham/vehicles", new StubResponse(500, new byte[0]));
		poller.pollOnce();
		serve("goraleigh", feed("300:1"));
		poller.pollOnce();

		await().until(() -> all.getResponse().getContentAsString().contains("\"300\""));
		assertThat(all.getResponse().getContentAsString().split("data:").length - 1, is(1));
	}

}
