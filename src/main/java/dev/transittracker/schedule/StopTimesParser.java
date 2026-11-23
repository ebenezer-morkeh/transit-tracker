package dev.transittracker.schedule;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Streaming parser for GTFS {@code stop_times.txt} (RFC 4180 CSV, optional BOM, CRLF or LF, any column order). */
final class StopTimesParser {

	private StopTimesParser() {
	}

	/** Calls {@code sink} once per row. Throws {@link IllegalArgumentException} on a missing column or bad value. */
	static void parse(InputStream in, Consumer<ScheduledStopTime> sink) throws IOException {
		Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16);
		List<String> header = readRecord(reader);
		if (header == null) {
			throw new IllegalArgumentException("stop_times.txt is empty");
		}
		header.set(0, header.get(0).replace("﻿", ""));
		int trip = column(header, "trip_id");
		int arrival = column(header, "arrival_time");
		int departure = column(header, "departure_time");
		int stop = column(header, "stop_id");
		int sequence = column(header, "stop_sequence");
		for (List<String> r = readRecord(reader); r != null; r = readRecord(reader)) {
			if (r.size() == 1 && r.get(0).isEmpty()) {
				continue; // blank line
			}
			try {
				sink.accept(new ScheduledStopTime(r.get(trip), Integer.parseInt(r.get(sequence).trim()), r.get(stop),
						seconds(r.get(arrival)), seconds(r.get(departure))));
			}
			catch (RuntimeException e) {
				throw new IllegalArgumentException("bad stop_times row " + r + ": " + e.getMessage(), e);
			}
		}
	}

	private static int column(List<String> header, String name) {
		int i = header.indexOf(name);
		if (i < 0) {
			throw new IllegalArgumentException("stop_times.txt has no " + name + " column");
		}
		return i;
	}

	/** "H:MM:SS" or "HH:MM:SS", hours may exceed 23. Blank means untimed. */
	static Integer seconds(String time) {
		String t = time.trim();
		if (t.isEmpty()) {
			return null;
		}
		String[] hms = t.split(":");
		if (hms.length != 3) {
			throw new IllegalArgumentException("bad time '" + time + "'");
		}
		return Integer.parseInt(hms[0]) * 3600 + Integer.parseInt(hms[1]) * 60 + Integer.parseInt(hms[2]);
	}

	/** One CSV record, or null at end of input. Quoted fields may contain commas, newlines and doubled quotes. */
	private static List<String> readRecord(Reader in) throws IOException {
		int c = in.read();
		if (c == -1) {
			return null;
		}
		List<String> fields = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		while (true) {
			if (quoted) {
				if (c == -1) {
					throw new IllegalArgumentException("unterminated quoted field");
				}
				if (c == '"') {
					c = in.read();
					if (c != '"') {
						quoted = false;
						continue; // re-examine the char after the closing quote
					}
				}
				field.append((char) c);
			}
			else if (c == '"') {
				quoted = true;
			}
			else if (c == ',') {
				fields.add(field.toString());
				field.setLength(0);
			}
			else if (c == '\n' || c == -1) {
				break;
			}
			else if (c != '\r') {
				field.append((char) c);
			}
			c = in.read();
		}
		fields.add(field.toString());
		return fields;
	}

}
