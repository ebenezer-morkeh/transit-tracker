package dev.transittracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransitTrackerApplicationTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	Flyway flyway;

	@Test
	void healthIsUpAgainstRealPostgres() throws Exception {
		mvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void flywayAppliesMigrationsOnStartup() {
		assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
	}

	@Test
	void gtfsRealtimeBindingsParseAFeed() throws Exception {
		byte[] bytes = FeedMessage.newBuilder()
			.setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0"))
			.build()
			.toByteArray();
		assertThat(FeedMessage.parseFrom(bytes).getHeader().getGtfsRealtimeVersion()).isEqualTo("2.0");
	}

}
