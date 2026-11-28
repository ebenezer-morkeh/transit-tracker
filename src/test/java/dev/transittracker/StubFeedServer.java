package dev.transittracker;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A JDK HTTP server standing in for agency feeds: serves {@link #files} by path, 404 otherwise. */
final class StubFeedServer {

	final Map<String, byte[]> files = new ConcurrentHashMap<>();

	private final HttpServer server;

	StubFeedServer() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
		server.createContext("/", exchange -> {
			byte[] body = files.get(exchange.getRequestURI().getPath());
			if (body == null) {
				exchange.sendResponseHeaders(404, -1);
			}
			else {
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
			}
			exchange.close();
		});
		server.start();
	}

	String url(String path) {
		return "http://127.0.0.1:" + server.getAddress().getPort() + path;
	}

	void stop() {
		server.stop(0);
	}

}
