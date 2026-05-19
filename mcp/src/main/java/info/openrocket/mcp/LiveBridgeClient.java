package info.openrocket.mcp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Thin client for the live Swing JSON bridge.
 */
final class LiveBridgeClient {

	static final Path DESCRIPTOR_PATH = Path.of(
			System.getProperty("user.home"),
			".openrocket",
			"remote-bridge.json");

	private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

	boolean isAvailable() {
		return Files.exists(DESCRIPTOR_PATH);
	}

	Object call(String command, Map<String, Object> arguments) throws IOException {
		Descriptor descriptor = readDescriptor();
		Request request = new Request();
		request.token = descriptor.token;
		request.command = command;
		request.arguments = arguments;

		try (Socket socket = new Socket(descriptor.host, descriptor.port);
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
						socket.getOutputStream(), StandardCharsets.UTF_8));
				BufferedReader reader = new BufferedReader(new InputStreamReader(
						socket.getInputStream(), StandardCharsets.UTF_8))) {
			writer.write(gson.toJson(request));
			writer.write('\n');
			writer.flush();
			Response response = gson.fromJson(reader.readLine(), Response.class);
			if (response == null) {
				throw new IOException("Bridge returned an empty response");
			}
			if (!response.ok) {
				String message = response.error == null ? "Unknown bridge error"
						: String.valueOf(response.error.get("message"));
				throw new IOException(message);
			}
			return response.result;
		}
	}

	@SuppressWarnings("unchecked")
	Map<String, Object> asMap(Object value) {
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> copy = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				copy.put(String.valueOf(entry.getKey()), entry.getValue());
			}
			return copy;
		}
		throw new IllegalArgumentException("Expected bridge result object but received " +
				(value == null ? "null" : value.getClass().getSimpleName()));
	}

	private Descriptor readDescriptor() throws IOException {
		return gson.fromJson(Files.readString(DESCRIPTOR_PATH, StandardCharsets.UTF_8), Descriptor.class);
	}

	private static final class Descriptor {
		private String host;
		private int port;
		private String token;
	}

	private static final class Request {
		private String token;
		private String command;
		private Map<String, Object> arguments;
	}

	private static final class Response {
		private boolean ok;
		private Object result;
		private Map<String, Object> error;
	}
}
