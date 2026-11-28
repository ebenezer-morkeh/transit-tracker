package dev.transittracker.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GtfsFilesTests {

	static InputStream csv(String text) {
		return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void routesKeepNamesAndColoursAndBlankOptionalColumnsAreNull() throws Exception {
		List<Route> routes = new ArrayList<>();
		GtfsFiles.routes("goraleigh", csv("""
				﻿route_id,agency_id,route_short_name,route_long_name,route_type,route_color,route_text_color
				7,GR,7,South Saunders,3,1A5FAD,FFFFFF
				"100X",GR,,"Airport, RDU",3,,
				"""), routes::add);

		assertThat(routes).containsExactly(new Route("goraleigh", "7", "7", "South Saunders", "1A5FAD", "FFFFFF"),
				new Route("goraleigh", "100X", null, "Airport, RDU", null, null));
	}

	@Test
	void stopsKeepNameAndLocationAndSkipGenericNodesAndBoardingAreas() throws Exception {
		List<Stop> stops = new ArrayList<>();
		GtfsFiles.stops(csv("""
				stop_id,stop_name,stop_lat,stop_lon,location_type
				1234,"Saunders St & Tryon Rd",35.7520,-78.6500,
				ST1,GoRaleigh Station,35.7760,-78.6380,1
				N1,,,,3
				B1,Bay B,,,4
				"""), stops::add);

		assertThat(stops).containsExactly(new Stop("1234", "Saunders St & Tryon Rd", 35.752, -78.65),
				new Stop("ST1", "GoRaleigh Station", 35.776, -78.638));
	}

	@Test
	void tripsKeepRouteServiceDirectionAndHeadsign() throws Exception {
		List<Trip> trips = new ArrayList<>();
		GtfsFiles.trips(csv("""
				route_id,service_id,trip_id,trip_headsign,direction_id,block_id
				7,WKDY,T1,To Crabtree,0,B1
				7,WKDY,T2,,,
				"""), trips::add);

		assertThat(trips).containsExactly(new Trip("T1", "7", "WKDY", 0, "To Crabtree"),
				new Trip("T2", "7", "WKDY", null, null));
	}

	@Test
	void calendarsReadWeekdayFlagsAndYyyymmddDates() throws Exception {
		List<ServiceCalendar> calendars = new ArrayList<>();
		GtfsFiles.calendars(csv("""
				service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date
				WKDY,1,1,1,1,1,0,0,20260817,20261231
				SUN,0,0,0,0,0,0,1,20260817,20261231
				"""), calendars::add);

		assertThat(calendars).containsExactly(
				new ServiceCalendar("WKDY", Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
						DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), LocalDate.of(2026, 8, 17), LocalDate.of(2026, 12, 31)),
				new ServiceCalendar("SUN", Set.of(DayOfWeek.SUNDAY), LocalDate.of(2026, 8, 17),
						LocalDate.of(2026, 12, 31)));
	}

	@Test
	void calendarDatesAreAddedOrRemovedAndOtherExceptionTypesAreErrors() throws Exception {
		List<ServiceCalendarDate> dates = new ArrayList<>();
		GtfsFiles.calendarDates(csv("""
				service_id,date,exception_type
				WKDY,20261126,2
				SUN,20261126,1
				"""), dates::add);

		assertThat(dates).containsExactly(new ServiceCalendarDate("WKDY", LocalDate.of(2026, 11, 26), false),
				new ServiceCalendarDate("SUN", LocalDate.of(2026, 11, 26), true));

		assertThatThrownBy(() -> GtfsFiles.calendarDates(csv("service_id,date,exception_type\nX,20261126,3\n"),
					d -> {
					}))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("exception_type");
	}

}
