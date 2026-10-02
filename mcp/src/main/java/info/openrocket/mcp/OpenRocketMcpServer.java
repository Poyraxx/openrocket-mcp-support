package info.openrocket.mcp;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import info.openrocket.core.automation.AutomationActivityLog;
import info.openrocket.core.automation.AutomationDocumentOps;
import info.openrocket.core.automation.AutomationJsonSupport;
import info.openrocket.core.automation.ComponentMetadataRegistry;
import info.openrocket.core.automation.DocumentSession;
import info.openrocket.core.automation.HeadlessAutomationBackend;
import info.openrocket.core.automation.MutationRequest;
import info.openrocket.core.automation.SimulationJobRef;
import info.openrocket.core.automation.SimulationJobRegistry;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.ResourceTemplate;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * STDIO MCP server exposing OpenRocket automation.
 */
public final class OpenRocketMcpServer {

	private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
	private final HeadlessAutomationBackend headless = new HeadlessAutomationBackend();
	private final SimulationJobRegistry localJobs = new SimulationJobRegistry();
	private final LiveBridgeClient liveBridge = new LiveBridgeClient();
	private final ComponentMetadataRegistry componentMetadataRegistry = new ComponentMetadataRegistry();

	private final Map<String, SessionBinding> sessions = new java.util.concurrent.ConcurrentHashMap<>();
	private final Map<String, JobBinding> jobs = new java.util.concurrent.ConcurrentHashMap<>();
	private McpSyncServer server;

	public static void main(String[] args) throws Exception {
		PrintStream protocolOutput = System.out;
		System.setOut(System.err);
		OpenRocketMcpServer server = new OpenRocketMcpServer();
		server.start(protocolOutput);
		Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown, "openrocket-mcp-shutdown"));
		new CountDownLatch(1).await();
	}

	private void start(PrintStream protocolOutput) {
		var transport = new StdioServerTransportProvider(new JacksonMcpJsonMapperSupplier().get(), System.in, protocolOutput);
		server = McpServer.sync(transport)
				.serverInfo("openrocket-mcp", "0.2.0")
				.capabilities(ServerCapabilities.builder()
						.tools(true)
						.resources(false, true)
						.build())
				.tools(
						tool("list_targets", "List headless and live OpenRocket targets.",
								emptySchema(), (exchange, request) -> toolResult(this::listTargets)),
						tool("attach_target", "Attach to a previously listed target.",
								schema("""
									{"type":"object","properties":{"targetId":{"type":"string"}},"required":["targetId"]}
									"""), (exchange, request) -> toolResult(() -> attachTarget(stringArg(request.arguments(), "targetId")))),
						tool("open_or_create_document", "Open a headless document from disk or create a new one.",
								schema("""
									{"type":"object","properties":{"path":{"type":"string"},"createNew":{"type":"boolean"}}}
									"""), (exchange, request) -> toolResult(() -> openOrCreateDocument(request.arguments()))),
						tool("save_document", "Save the active document session.",
								schema("""
									{"type":"object","properties":{"sessionId":{"type":"string"},"path":{"type":"string"}},"required":["sessionId"]}
									"""), (exchange, request) -> toolResult(() -> saveDocument(request.arguments()))),
						tool("get_schema", "Get automation schema for a session.",
								schema("""
									{"type":"object","properties":{"sessionId":{"type":"string"}},"required":["sessionId"]}
									"""), (exchange, request) -> toolResult(() -> getSchema(request.arguments()))),
						tool("get_snapshot", "Get a full document snapshot for a session.",
								schema("""
									{"type":"object","properties":{"sessionId":{"type":"string"}},"required":["sessionId"]}
									"""), (exchange, request) -> toolResult(() -> getSnapshot(request.arguments()))),
						tool("mutate", "Apply a batched document mutation as a single undo step.",
								schema("""
									{"type":"object","properties":{"sessionId":{"type":"string"},"undoDescription":{"type":"string"},"requests":{"type":"array"}},"required":["sessionId","requests"]}
									"""), (exchange, request) -> toolResult(() -> mutate(request.arguments()))),
						tool("manage_simulations", "List or mutate simulations inside a session.",
								schema("""
									{"type":"object","properties":{"sessionId":{"type":"string"},"action":{"type":"string"},"arguments":{"type":"object"}},"required":["sessionId","action"]}
									"""), (exchange, request) -> toolResult(() -> manageSimulations(request.arguments()))),
						tool("run_simulation", "Start an asynchronous simulation job.",
								schema("""
									{"type":"object","properties":{"sessionId":{"type":"string"},"simulationId":{"type":"string"}},"required":["sessionId","simulationId"]}
									"""), (exchange, request) -> toolResult(() -> runSimulation(request.arguments()))),
						tool("get_job", "Get simulation job status.",
								schema("""
									{"type":"object","properties":{"jobId":{"type":"string"}},"required":["jobId"]}
									"""), (exchange, request) -> toolResult(() -> getJob(stringArg(request.arguments(), "jobId")))),
						tool("cancel_job", "Cancel a running simulation job.",
								schema("""
									{"type":"object","properties":{"jobId":{"type":"string"}},"required":["jobId"]}
									"""), (exchange, request) -> toolResult(() -> cancelJob(stringArg(request.arguments(), "jobId")))),
						tool("get_results", "Get simulation summary and branch series.",
								schema("""
									{"type":"object","properties":{"sessionId":{"type":"string"},"simulationId":{"type":"string"}},"required":["sessionId","simulationId"]}
									"""), (exchange, request) -> toolResult(() -> getResults(request.arguments()))))
				.resources(
						new McpServerFeatures.SyncResourceSpecification(
								Resource.builder()
										.uri("openrocket://component-catalog")
										.name("OpenRocket Component Catalog")
										.description("DocumentConfig-derived component aliases and property metadata.")
										.mimeType("application/json")
										.build(),
								(exchange, request) -> jsonResource(componentMetadataRegistry.getComponentCatalog(), request.uri())))
				.resourceTemplates(
						new McpServerFeatures.SyncResourceTemplateSpecification(
								ResourceTemplate.builder()
										.uriTemplate("openrocket://session/{sessionId}/snapshot")
										.name("Session Snapshot")
										.description("Read the current document snapshot for a session.")
										.mimeType("application/json")
										.build(),
								(exchange, request) -> resourceResult(request.uri(), () -> readTemplateResource(request.uri()))),
						new McpServerFeatures.SyncResourceTemplateSpecification(
								ResourceTemplate.builder()
										.uriTemplate("openrocket://session/{sessionId}/simulations")
										.name("Session Simulations")
										.description("Read current simulation summaries for a session.")
										.mimeType("application/json")
										.build(),
								(exchange, request) -> resourceResult(request.uri(), () -> readTemplateResource(request.uri()))))
				.build();
		AutomationActivityLog.log("openrocket-mcp", "server started");
	}

	private void shutdown() {
		AutomationActivityLog.log("openrocket-mcp", "server shutdown requested");
		try {
			if (server != null) {
				server.close();
			}
		} catch (Exception ignored) {
			// Best-effort shutdown.
		}
		try {
			localJobs.close();
		} catch (Exception ignored) {
			// Best-effort shutdown.
		}
		try {
			headless.close();
		} catch (Exception ignored) {
			// Best-effort shutdown.
		}
	}

	private McpServerFeatures.SyncToolSpecification tool(String name, String description, McpSchema.JsonSchema schema,
			java.util.function.BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> handler) {
		return McpServerFeatures.SyncToolSpecification.builder()
				.tool(Tool.builder()
						.name(name)
						.description(description)
						.inputSchema(schema)
						.build())
				.callHandler((exchange, request) -> {
					logToolRequest(name, request.arguments());
					try {
						CallToolResult result = handler.apply(exchange, request);
						AutomationActivityLog.log("openrocket-mcp", "ok " + name);
						return result;
					} catch (RuntimeException exception) {
						AutomationActivityLog.log("openrocket-mcp",
								"error " + name + " -> " + safeMessage(exception));
						throw exception;
					}
				})
				.build();
	}

	private CallToolResult jsonResult(Object value) {
		return CallToolResult.builder()
				.content(List.of(new TextContent(AutomationJsonSupport.toCompactJson(value))))
				.build();
	}

	private CallToolResult toolResult(IoSupplier<Object> supplier) {
		try {
			return jsonResult(supplier.get());
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private ReadResourceResult jsonResource(Object value, String uri) {
		return new ReadResourceResult(List.of(
				new TextResourceContents(uri, "application/json", AutomationJsonSupport.toCompactJson(value))));
	}

	private ReadResourceResult resourceResult(String uri, IoSupplier<Object> supplier) {
		try {
			return jsonResource(supplier.get(), uri);
		} catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private Object readTemplateResource(String uri) throws IOException {
		if (uri.endsWith("/snapshot")) {
			String sessionId = sessionIdFromUri(uri, "/snapshot");
			return getSnapshot(Map.of("sessionId", sessionId));
		}
		if (uri.endsWith("/simulations")) {
			String sessionId = sessionIdFromUri(uri, "/simulations");
			return manageSimulations(Map.of("sessionId", sessionId, "action", "list"));
		}
		throw new IllegalArgumentException("Unsupported resource URI: " + uri);
	}

	private String sessionIdFromUri(String uri, String suffix) {
		String prefix = "openrocket://session/";
		if (!uri.startsWith(prefix) || !uri.endsWith(suffix)) {
			throw new IllegalArgumentException("Invalid session resource URI: " + uri);
		}
		return uri.substring(prefix.length(), uri.length() - suffix.length());
	}

	private Object listTargets() throws IOException {
		List<Object> targets = new ArrayList<>();
		targets.addAll(headless.listTargets());
		if (liveBridge.isAvailable()) {
			Object liveTargets = liveBridge.call("list_targets", Map.of());
			if (liveTargets instanceof List<?> items) {
				targets.addAll(items);
			}
		}
		return targets;
	}

	private Object attachTarget(String targetId) throws IOException {
		if (targetId.startsWith("frame:")) {
			Map<String, Object> result = liveBridge.asMap(liveBridge.call("attach_target", Map.of("targetId", targetId)));
			sessions.put(string(result, "sessionId"), SessionBinding.remote(string(result, "sessionId")));
			return result;
		}
		DocumentSession session = headless.attachTarget(targetId);
		sessions.put(session.getSessionId(), SessionBinding.local(session));
		return sessionPayload(session);
	}

	private Object openOrCreateDocument(Map<String, Object> arguments) throws IOException {
		Path path = arguments.containsKey("path") && arguments.get("path") != null && !String.valueOf(arguments.get("path")).isBlank()
				? Path.of(String.valueOf(arguments.get("path"))).toAbsolutePath().normalize()
				: null;
		boolean createNew = booleanArg(arguments, "createNew", false);
		try {
			DocumentSession session = headless.openOrCreateDocument(path, createNew);
			sessions.put(session.getSessionId(), SessionBinding.local(session));
			return sessionPayload(session);
		} catch (Exception exception) {
			throw new IOException(exception.getMessage(), exception);
		}
	}

	private Object saveDocument(Map<String, Object> arguments) throws IOException {
		SessionBinding binding = requireSession(arguments);
		if (binding.local()) {
			try {
				Path path = arguments.containsKey("path") && arguments.get("path") != null
						? Path.of(String.valueOf(arguments.get("path"))).toAbsolutePath().normalize()
						: null;
				binding.session().save(path);
				return sessionPayload(binding.session());
			} catch (Exception exception) {
				throw new IOException(exception.getMessage(), exception);
			}
		}
		return liveBridge.call("save_document", remoteArgs(binding.remoteSessionId(), arguments));
	}

	private Object getSchema(Map<String, Object> arguments) throws IOException {
		SessionBinding binding = requireSession(arguments);
		if (binding.local()) {
			return AutomationDocumentOps.getSchema(binding.session());
		}
		return liveBridge.call("get_schema", remoteArgs(binding.remoteSessionId(), arguments));
	}

	private Object getSnapshot(Map<String, Object> arguments) throws IOException {
		SessionBinding binding = requireSession(arguments);
		if (binding.local()) {
			return AutomationDocumentOps.getSnapshot(binding.session());
		}
		return liveBridge.call("get_snapshot", remoteArgs(binding.remoteSessionId(), arguments));
	}

	private Object mutate(Map<String, Object> arguments) throws IOException {
		SessionBinding binding = requireSession(arguments);
		if (binding.local()) {
			return AutomationDocumentOps.mutate(
					binding.session(),
					parseMutationRequests(arguments.get("requests")),
					arguments.containsKey("undoDescription") ? String.valueOf(arguments.get("undoDescription")) : null);
		}
		return liveBridge.call("mutate", remoteArgs(binding.remoteSessionId(), arguments));
	}

	private Object manageSimulations(Map<String, Object> arguments) throws IOException {
		SessionBinding binding = requireSession(arguments);
		if (binding.local()) {
			return AutomationDocumentOps.manageSimulations(
					binding.session(),
					stringArg(arguments, "action"),
					map(arguments.get("arguments")));
		}
		return liveBridge.call("manage_simulations", remoteArgs(binding.remoteSessionId(), arguments));
	}

	private Object runSimulation(Map<String, Object> arguments) throws IOException {
		SessionBinding binding = requireSession(arguments);
		String simulationId = stringArg(arguments, "simulationId");
		if (binding.local()) {
			SimulationJobRef job = localJobs.submit(binding.session(), simulationId);
			jobs.put(job.jobId(), JobBinding.local(job.jobId()));
			return job;
		}
		Object result = liveBridge.call("run_simulation", remoteArgs(binding.remoteSessionId(), arguments));
		String jobId = string(liveBridge.asMap(result), "jobId");
		jobs.put(jobId, JobBinding.remote(jobId));
		return result;
	}

	private Object getJob(String jobId) throws IOException {
		JobBinding binding = requireJob(jobId);
		if (binding.local()) {
			return localJobs.getJob(jobId);
		}
		return liveBridge.call("get_job", Map.of("jobId", binding.remoteJobId()));
	}

	private Object cancelJob(String jobId) throws IOException {
		JobBinding binding = requireJob(jobId);
		if (binding.local()) {
			localJobs.cancel(jobId);
			return localJobs.getJob(jobId);
		}
		return liveBridge.call("cancel_job", Map.of("jobId", binding.remoteJobId()));
	}

	private Object getResults(Map<String, Object> arguments) throws IOException {
		SessionBinding binding = requireSession(arguments);
		if (binding.local()) {
			return AutomationDocumentOps.getResults(binding.session(), stringArg(arguments, "simulationId"));
		}
		return liveBridge.call("get_results", remoteArgs(binding.remoteSessionId(), arguments));
	}

	private List<MutationRequest> parseMutationRequests(Object rawRequests) {
		List<MutationRequest> requests = new ArrayList<>();
		if (!(rawRequests instanceof List<?> items)) {
			return requests;
		}
		for (Object item : items) {
			Map<String, Object> map = map(item);
			requests.add(new MutationRequest(stringArg(map, "action"), map(map.get("arguments"))));
		}
		return requests;
	}

	private SessionBinding requireSession(Map<String, Object> arguments) {
		String sessionId = stringArg(arguments, "sessionId");
		SessionBinding binding = sessions.get(sessionId);
		if (binding == null) {
			throw new IllegalArgumentException("Unknown session: " + sessionId);
		}
		return binding;
	}

	private JobBinding requireJob(String jobId) {
		JobBinding binding = jobs.get(jobId);
		if (binding == null) {
			throw new IllegalArgumentException("Unknown job: " + jobId);
		}
		return binding;
	}

	private Map<String, Object> remoteArgs(String remoteSessionId, Map<String, Object> arguments) {
		Map<String, Object> payload = new LinkedHashMap<>(arguments);
		payload.put("sessionId", remoteSessionId);
		return payload;
	}

	private Map<String, Object> sessionPayload(DocumentSession session) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("sessionId", session.getSessionId());
		payload.put("targetId", session.getTarget().id());
		payload.put("live", session.isLive());
		payload.put("filePath", session.getFilePath() == null ? null : session.getFilePath().toString());
		return payload;
	}

	private Map<String, Object> map(Object value) {
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

	private String stringArg(Map<String, Object> arguments, String key) {
		Object value = arguments.get(key);
		if (value == null || String.valueOf(value).isBlank()) {
			throw new IllegalArgumentException("Missing required argument: " + key);
		}
		return String.valueOf(value);
	}

	private boolean booleanArg(Map<String, Object> arguments, String key, boolean fallback) {
		if (!arguments.containsKey(key) || arguments.get(key) == null) {
			return fallback;
		}
		Object value = arguments.get(key);
		return value instanceof Boolean booleanValue ? booleanValue : Boolean.parseBoolean(String.valueOf(value));
	}

	private String string(Map<String, Object> map, String key) {
		Object value = map.get(key);
		if (value == null) {
			throw new IllegalArgumentException("Missing key '" + key + "' in bridge response");
		}
		return String.valueOf(value);
	}

	private McpSchema.JsonSchema emptySchema() {
		return new McpSchema.JsonSchema("object", Map.of(), List.of(), true, null, null);
	}

	private McpSchema.JsonSchema schema(String json) {
		return toJsonSchema(map(gson.fromJson(json, Map.class)));
	}

	@SuppressWarnings("unchecked")
	private McpSchema.JsonSchema toJsonSchema(Map<String, Object> schema) {
		String type = schema.containsKey("type") && schema.get("type") != null
				? String.valueOf(schema.get("type"))
				: "object";
		Map<String, Object> properties = map(schema.get("properties"));
		List<String> required = new ArrayList<>();
		if (schema.get("required") instanceof List<?> items) {
			for (Object item : items) {
				required.add(String.valueOf(item));
			}
		}
		Boolean additionalProperties = null;
		if (schema.containsKey("additionalProperties") && schema.get("additionalProperties") != null) {
			Object value = schema.get("additionalProperties");
			additionalProperties = value instanceof Boolean booleanValue
					? booleanValue
					: Boolean.parseBoolean(String.valueOf(value));
		}
		Map<String, Object> defs = schema.containsKey("$defs")
				? map(schema.get("$defs"))
				: map(schema.get("defs"));
		if (defs.isEmpty()) {
			defs = null;
		}
		Map<String, Object> definitions = map(schema.get("definitions"));
		if (definitions.isEmpty()) {
			definitions = null;
		}
		return new McpSchema.JsonSchema(type, properties, required, additionalProperties, defs, definitions);
	}

	private void logToolRequest(String name, Map<String, Object> arguments) {
		AutomationActivityLog.log(
				"openrocket-mcp",
				"request " + name + " args=" +
						AutomationJsonSupport.summarize(arguments == null ? Map.of() : arguments, 400));
	}

	private static String safeMessage(Throwable throwable) {
		if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
			return throwable == null ? "Unknown failure" : throwable.getClass().getSimpleName();
		}
		return throwable.getMessage();
	}

	private record SessionBinding(DocumentSession session, String remoteSessionId) {
		static SessionBinding local(DocumentSession session) {
			return new SessionBinding(session, null);
		}

		static SessionBinding remote(String remoteSessionId) {
			return new SessionBinding(null, remoteSessionId);
		}

		boolean local() {
			return session != null;
		}
	}

	private record JobBinding(String localJobId, String remoteJobId) {
		static JobBinding local(String localJobId) {
			return new JobBinding(localJobId, null);
		}

		static JobBinding remote(String remoteJobId) {
			return new JobBinding(null, remoteJobId);
		}

		boolean local() {
			return localJobId != null;
		}
	}

	@FunctionalInterface
	private interface IoSupplier<T> {
		T get() throws IOException;
	}
}
