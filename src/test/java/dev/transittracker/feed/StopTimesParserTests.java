package dev.transittracker.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StopTimesParserTests {

	static List<ScheduledStopTime> parse(String csv) throws Exception {
		List<ScheduledStopTime> rows = new ArrayList<>();
		StopTimesParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), rows::add);
		return rows;
	}

	@Test
	void handlesBomCrlfQuotingColumnOrderAndTimesPast24h() throws Exception {
		String csv = "﻿stop_sequence,stop_id,trip_id,departure_time,arrival_time,stop_headsign\r\n"
				+ "1,S1,T1,08:00:30,08:00:00,Downtown\r\n"
				+ "2,\"S,2\",\"T1\",8:05:00,8:05:00,\"Say \"\"hi\"\"\"\r\n"
				+ "3,S3,T1,25:10:00,25:09:59,\"two\nlines\"\r\n"
				+ "4,S4,T1,,,\r\n";

		assertThat(parse(csv)).containsExactly(
				new ScheduledStopTime("T1", 1, "S1", 8 * 3600, 8 * 3600 + 30),
				new ScheduledStopTime("T1", 2, "S,2", 8 * 3600 + 300, 8 * 3600 + 300),
				new ScheduledStopTime("T1", 3, "S3", 25 * 3600 + 9 * 60 + 59, 25 * 3600 + 600),
				new ScheduledStopTime("T1", 4, "S4", null, null));
	}

	@Test
	void lfOnlyAndNoTrailingNewline() throws Exception {
		assertThat(parse("trip_id,arrival_time,departure_time,stop_id,stop_sequence\nT9,00:00:01,00:00:02,X,7"))
			.containsExactly(new ScheduledStopTime("T9", 7, "X", 1, 2));
	}

	@Test
	void missingRequiredColumnIsAnError() {
		assertThatThrownBy(() -> parse("trip_id,arrival_time,departure_time,stop_id\nT1,1:00:00,1:00:00,S\n"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("stop_sequence");
	}

}
