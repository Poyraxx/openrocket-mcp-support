package info.openrocket.swing.remote;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import info.openrocket.core.automation.AutomationActivityLog;
import info.openrocket.core.automation.AutomationDocumentOps;
import info.openrocket.core.automation.AutomationJsonSupport;
import info.openrocket.core.automation.AutomationTargetDescriptor;
import info.openrocket.core.automation.DocumentSession;
import info.openrocket.core.automation.MutationRequest;
import info.openrocket.core.automation.SimulationJobRegistry;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.file.GeneralRocketSaver;
import info.openrocket.core.util.DecalNotFoundException;
import info.openrocket.swing.gui.main.BasicFrame;

/**
 * Lightweight localhost-only JSON bridge for live Swing sessions.
 */
public final class OpenRocketJsonBridge implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(OpenRocketJsonBridge.class);
	private static final Path DESCRIPTOR_PATH = Path.of(
			System.getProperty("user.home"),
			".openrocket",
			"remote-bridge.json");

	private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
	private final ExecutorService clientExecutor = Executors.newCachedThreadPool(runnable -> {
		Thread thread = new Thread(runnable, "openrocket-json-bridge");
		thread.setDaemon(true);
		return thread;
	});
	private final SimulationJobRegistry jobs = new SimulationJobRegistry();
	private final Map<String, DocumentSession> sessions = new java.util.concurrent.ConcurrentHashMap<>();
	private final GeneralRocketSaver saver = new GeneralRocketSaver();

	private final ServerSocket serverSocket;
	private final String token;
	private final String host;
	private final Instant startedAt = Instant.now();

	private volatile boolean running = true;

	public OpenRocketJsonBridge(int requestedPort, String configuredToken) throws IOException {
		this.host = InetAddress.getLoopbackAddress().getHostAddress();
		this.serverSocket = new ServerSocket(requestedPort, 0, InetAddress.getLoopbackAddress());
		this.token = (configuredToken == null || configuredToken.isBlank())
				? UUID.randomUUID().toString()
				: configuredToken;
	}

	public void start() throws IOException {
		writeDescriptor();
		clientExecutor.submit(this::acceptLoop);
		log.info("OpenRocket JSON bridge listening on {}:{}", host, serverSocket.getLocalPort());
		AutomationActivityLog.log("openrocket-bridge",
				"listening on " + host + ":" + serverSocket.getLocalPort() +
						" descriptor=" + DESCRIPTOR_PATH);
	}

	@Override
	public void close() throws IOException {
		running = false;
		jobs.close();
		clientExecutor.shutdownNow();
		serverSocket.close();
		AutomationActivityLog.log("openrocket-bridge", "bridge shutdown requested");
		try {
			Files.deleteIfExists(DESCRIPTOR_PATH);
		} catch (IOException exception) {
			log.debug("Failed to remove bridge descriptor", exception);
		}
	}

	private void acceptLoop() {
		while (running) {
			try {
				Socket socket = serverSocket.accept();
				clientExecutor.submit(() -> handleClient(socket));
			} catch (IOException exception) {
				if (running) {
					log.warn("Bridge accept loop failed", exception);
				}
				return;
			}
		}
	}

	private void handleClient(Socket socket) {
		try (socket;
				BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(
						socket.getInputStream(), StandardCharsets.UTF_8));
	                BufferedWriter writer = new BufferedWriter(new java.io.OutputStreamWriter(
						socket.getOutputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String commandName = "unknown";
				Response response;
				try {
					Request request = gson.fromJson(line, Request.class);
					commandName = request == null ? "unknown" : normalize(request.command);
					logRequest(commandName, request == null ? null : request.arguments);
					response = handleRequest(request);
				} catch (RuntimeException exception) {
					AutomationActivityLog.log("openrocket-bridge",
							"error " + commandName + " -> " + safeMessage(exception));
					response = Response.error(exception.getMessage() == null
							? exception.getClass().getSimpleName()
							: exception.getMessage());
				}
				try {
					writer.write(gson.toJson(response.toPayload()));
					writer.write('\n');
					writer.flush();
					logResponse(commandName, response);
				} catch (RuntimeException serializationException) {
					log.error("Failed to serialize bridge response for {}", commandName, serializationException);
					AutomationActivityLog.log("openrocket-bridge",
							"serialization failure " + commandName + " -> " + safeMessage(serializationException));
					Response fallback = Response.error("Failed to serialize bridge response");
					writer.write(gson.toJson(fallback.toPayload()));
					writer.write('\n');
					writer.flush();
				}
			}
		} catch (IOException exception) {
			if (running) {
				log.debug("Bridge client disconnected", exception);
			}
		} catch (RuntimeException exception) {
			log.error("Bridge client failed unexpectedly", exception);
			AutomationActivityLog.log("openrocket-bridge",
					"client failure -> " + safeMessage(exception));
		}
	}

	private Response handleRequest(Request request) {
		if (request == null) {
			return Response.error("Missing request body");
		}
		if (!token.equals(request.token)) {
			return Response.error("Unauthorized");
		}

		Map<String, Object> arguments = request.arguments == null ? Map.of() : request.arguments;
		return switch (normalize(request.command)) {
			case "ping" -> Response.ok(descriptorPayload());
			case "list_targets" -> Response.ok(onEdt(this::listTargets));
			case "attach_target" -> Response.ok(onEdt(() -> sessionPayload(attachTarget(requireString(arguments, "targetId")))));
			case "save_document" -> Response.ok(onEdt(() -> saveDocument(arguments)));
			case "get_schema" -> Response.ok(onEdt(() -> AutomationDocumentOps.getSchema(requireSession(arguments))));
			case "get_snapshot" -> Response.ok(onEdt(() -> AutomationDocumentOps.getSnapshot(requireSession(arguments))));
			case "mutate" -> Response.ok(onEdt(() -> AutomationDocumentOps.mutate(
					requireSession(arguments),
					parseMutationRequests(arguments.get("requests")),
					arguments.containsKey("undoDescription") ? String.valueOf(arguments.get("undoDescription")) : null)));
			case "manage_simulations" -> Response.ok(onEdt(() -> AutomationDocumentOps.manageSimulations(
					requireSession(arguments),
					requireString(arguments, "action"),
					map(arguments.get("arguments")))));
			case "run_simulation" -> Response.ok(onEdt(() -> jobs.submit(
					requireSession(arguments),
					requireString(arguments, "simulationId"))));
			case "get_job" -> Response.ok(jobs.getJob(requireString(arguments, "jobId")));
			case "cancel_job" -> Response.ok(cancelJob(arguments));
			case "get_results" -> Response.ok(onEdt(() -> AutomationDocumentOps.getResults(
					requireSession(arguments),
					requireString(arguments, "simulationId"))));
			default -> Response.error("Unsupported bridge command: " + request.command);
		};
	}

	private Map<String, Object> saveDocument(Map<String, Object> arguments) {
		DocumentSession session = requireSession(arguments);
		Path path = arguments.containsKey("path")
				? Path.of(String.valueOf(arguments.get("path"))).toAbsolutePath().normalize()
				: session.getFilePath();
		if (path == null) {
			throw new IllegalArgumentException("Missing save path");
		}
		try {
			if (path.getParent() != null) {
				Files.createDirectories(path.getParent());
			}
			saver.save(path.toFile(), session.getDocument());
			session.getDocument().setFile(path.toFile());
			session.getDocument().setSaved(true);
			session.setFilePath(path);
		} catch (IOException | DecalNotFoundException exception) {
			throw new IllegalStateException("Failed to save live document: " + exception.getMessage(), exception);
		}
		return sessionPayload(session);
	}

	private Object cancelJob(Map<String, Object> arguments) {
		String jobId = requireString(arguments, "jobId");
		jobs.cancel(jobId);
		return jobs.getJob(jobId);
	}

	private List<AutomationTargetDescriptor> listTargets() {
		List<AutomationTargetDescriptor> targets = new ArrayList<>();
		for (BasicFrame frame : BasicFrame.getAllFrames()) {
			targets.add(targetFor(frame));
		}
		return targets;
	}

	private DocumentSession attachTarget(String targetId) {
		DocumentSession existing = sessions.get(targetId);
		if (existing != null) {
			return existing;
		}
		BasicFrame frame = findFrame(targetId);
		if (frame == null) {
			throw new IllegalArgumentException("Target not found: " + targetId);
		}
		AutomationTargetDescriptor target = targetFor(frame);
		OpenRocketDocument document = frame.getDocument();
		Path filePath = document.getFile() == null ? null : document.getFile().toPath().toAbsolutePath().normalize();
		DocumentSession session = new DocumentSession(
				"swing-live",
				target,
				document,
				true,
				filePath,
				(currentSession, savePath) -> saveDocument(Map.of(
						"sessionId", currentSession.getSessionId(),
						"path", savePath.toString())),
				currentSession -> {
					sessions.remove(currentSession.getTarget().id());
					sessions.remove(currentSession.getSessionId());
				});
		sessions.put(targetId, session);
		sessions.put(session.getSessionId(), session);
		return session;
	}

	private BasicFrame findFrame(String targetId) {
		for (BasicFrame frame : BasicFrame.getAllFrames()) {
			if (targetFor(frame).id().equals(targetId)) {
				return frame;
			}
		}
		return null;
	}

	private AutomationTargetDescriptor targetFor(BasicFrame frame) {
		OpenRocketDocument document = frame.getDocument();
		String frameId = Integer.toHexString(System.identityHashCode(frame));
		String documentId = document.getRocket().getID().toString();
		Path filePath = document.getFile() == null ? null : document.getFile().toPath().toAbsolutePath().normalize();
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("frameId", frameId);
		metadata.put("documentId", documentId);
		if (filePath != null) {
			metadata.put("path", filePath.toString());
		}
		return new AutomationTargetDescriptor(
				"frame:" + frameId + "/document:" + documentId,
				"swing-frame",
				filePath != null ? filePath.getFileName().toString() : document.getRocket().getName(),
				true,
				metadata);
	}

	private Map<String, Object> sessionPayload(DocumentSession session) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("sessionId", session.getSessionId());
		payload.put("targetId", session.getTarget().id());
		payload.put("live", session.isLive());
		payload.put("filePath", session.getFilePath() == null ? null : session.getFilePath().toString());
		return payload;
	}

	private DocumentSession requireSession(Map<String, Object> arguments) {
		String sessionId = requireString(arguments, "sessionId");
		DocumentSession session = sessions.get(sessionId);
		if (session == null) {
			for (DocumentSession candidate : sessions.values()) {
				if (candidate.getSessionId().equals(sessionId)) {
					session = candidate;
					break;
				}
			}
		}
		if (session == null) {
			throw new IllegalArgumentException("Session not found: " + sessionId);
		}
		return session;
	}

	private List<MutationRequest> parseMutationRequests(Object rawRequests) {
		List<MutationRequest> requests = new ArrayList<>();
		if (!(rawRequests instanceof List<?> items)) {
			return requests;
		}
		for (Object item : items) {
			Map<String, Object> requestMap = map(item);
			requests.add(new MutationRequest(
					requireString(requestMap, "action"),
					map(requestMap.get("arguments"))));
		}
		return requests;
	}

	private void writeDescriptor() throws IOException {
		if (DESCRIPTOR_PATH.getParent() != null) {
			Files.createDirectories(DESCRIPTOR_PATH.getParent());
		}
		Files.writeString(DESCRIPTOR_PATH, gson.toJson(descriptorPayload()), StandardCharsets.UTF_8);
	}

	private Map<String, Object> descriptorPayload() {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("host", host);
		payload.put("port", serverSocket.getLocalPort());
		payload.put("token", token);
		payload.put("startedAt", startedAt.toString());
		payload.put("descriptorPath", DESCRIPTOR_PATH.toString());
		return payload;
	}

	private <T> T onEdt(Callable<T> callable) {
		if (SwingUtilities.isEventDispatchThread()) {
			try {
				return callable.call();
			} catch (Exception exception) {
				throw wrap(exception);
			}
		}
		FutureTask<T> task = new FutureTask<>(callable);
		try {
			SwingUtilities.invokeAndWait(task);
			return task.get();
		} catch (Exception exception) {
			throw wrap(exception);
		}
	}

	private RuntimeException wrap(Exception exception) {
		Throwable cause = exception instanceof java.util.concurrent.ExecutionException executionException
				? executionException.getCause()
				: exception;
		return cause instanceof RuntimeException runtimeException
				? runtimeException
				: new IllegalStateException(cause.getMessage(), cause);
	}

	private static String requireString(Map<String, Object> args, String key) {
		Object value = args.get(key);
		if (value == null || String.valueOf(value).isBlank()) {
			throw new IllegalArgumentException("Missing required argument: " + key);
		}
		return String.valueOf(value);
	}

	private static Map<String, Object> map(Object value) {
		if (value == null) {
			return Map.of();
		}
		if (value instanceof Map<?, ?> original) {
			Map<String, Object> copy = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : original.entrySet()) {
				copy.put(String.valueOf(entry.getKey()), entry.getValue());
			}
			return copy;
		}
		throw new IllegalArgumentException("Expected object map but received " + value.getClass().getSimpleName());
	}

	private static String normalize(String value) {
		return value == null ? "" : value.toLowerCase(java.util.Locale.ENGLISH);
	}

	private void logRequest(String commandName, Map<String, Object> arguments) {
		AutomationActivityLog.log(
				"openrocket-bridge",
				"request " + commandName + " args=" +
						AutomationJsonSupport.summarize(arguments == null ? Map.of() : arguments, 400));
	}

	private void logResponse(String commandName, Response response) {
		if (response.ok) {
			AutomationActivityLog.log("openrocket-bridge", "ok " + commandName);
		} else {
			AutomationActivityLog.log("openrocket-bridge",
					"error " + commandName + " -> " + response.errorMessage());
		}
	}

	private static String safeMessage(Throwable throwable) {
		if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
			return throwable == null ? "Unknown failure" : throwable.getClass().getSimpleName();
		}
		return throwable.getMessage();
	}

	private static final class Request {
		private String token;
		private String command;
		private Map<String, Object> arguments;
	}

	private static final class Response {
		private final boolean ok;
		private final Object result;
		private final Map<String, Object> error;

		private Response(boolean ok, Object result, Map<String, Object> error) {
			this.ok = ok;
			this.result = result;
			this.error = error;
		}

		private static Response ok(Object result) {
			return new Response(true, result, null);
		}

		private static Response error(String message) {
			String safeMessage = (message == null || message.isBlank()) ? "Bridge request failed" : message;
			return new Response(false, null, Map.of("message", safeMessage));
		}

		private Map<String, Object> toPayload() {
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("ok", ok);
			payload.put("result", AutomationJsonSupport.sanitizeForJson(result));
			payload.put("error", AutomationJsonSupport.sanitizeForJson(error));
			return payload;
		}

		private String errorMessage() {
			return error == null ? "Unknown bridge error" : String.valueOf(error.get("message"));
		}
	}
}
