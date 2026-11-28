package dev.transittracker.schedule;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Streaming reader for one static GTFS file (RFC 4180 CSV, optional BOM, CRLF or LF, any column order). */
final class GtfsCsv {

	private GtfsCsv() {
	}

	/** One record, read by column name. */
	static final class Row {

		private final Map<String, Integer> columns;

		private final List<String> fields;

		private Row(Map<String, Integer> columns, List<String> fields) {
			this.columns = columns;
			this.fields = fields;
		}

		/** The value, trimmed; throws when the column is absent or the value blank. */
		String required(String column) {
			String value = optional(column);
			if (value == null) {
				throw new IllegalArgumentException(column + " is blank");
			}
			return value;
		}

		/** The value, trimmed; null when the column is absent or the value blank. */
		String optional(String column) {
			Integer i = columns.get(column);
			if (i == null || i >= fields.size()) {
				return null;
			}
			String value = fields.get(i).trim();
			return value.isEmpty() ? null : value;
		}

		/** The value as is (not trimmed, blank kept); throws when the column is absent. */
		String raw(String column) {
			Integer i = columns.get(column);
			if (i == null || i >= fields.size()) {
				throw new IllegalArgumentException(column + " is missing");
			}
			return fields.get(i);
		}

	}

	/**
	 * Calls {@code sink} once per non-blank record of {@code file}. Throws {@link IllegalArgumentException} when a
	 * {@code requiredColumns} header is missing, or (wrapping the cause, with the row) when {@code sink} rejects a row.
	 */
	static void read(InputStream in, String file, List<String> requiredColumns, Consumer<Row> sink) throws IOException {
		Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16);
		List<String> header = readRecord(reader);
		if (header == null) {
			throw new IllegalArgumentException(file + " is empty");
		}
		header.set(0, header.get(0).replace("﻿", ""));
		Map<String, Integer> columns = new HashMap<>();
		for (int i = 0; i < header.size(); i++) {
			columns.putIfAbsent(header.get(i).trim(), i);
		}
		for (String column : requiredColumns) {
			if (!columns.containsKey(column)) {
				throw new IllegalArgumentException(file + " has no " + column + " column");
			}
		}
		for (List<String> r = readRecord(reader); r != null; r = readRecord(reader)) {
			if (r.size() == 1 && r.get(0).isEmpty()) {
				continue; // blank line
			}
			try {
				sink.accept(new Row(columns, r));
			}
			catch (RuntimeException e) {
				throw new IllegalArgumentException("bad " + file + " row " + r + ": " + e.getMessage(), e);
			}
		}
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
