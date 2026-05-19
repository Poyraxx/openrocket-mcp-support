package info.openrocket.core.automation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.OpenRocketDocumentFactory;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.GeneralRocketSaver;
import info.openrocket.core.file.RocketLoadException;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.startup.Application;
import info.openrocket.core.startup.CoreModule;
import info.openrocket.core.util.DecalNotFoundException;

/**
 * Headless automation backend backed directly by the OpenRocket core model.
 */
public final class HeadlessAutomationBackend implements AutomationBackend {

	private static final Object INITIALIZATION_LOCK = new Object();
	private static volatile boolean initialized;

	private final Map<String, DocumentSession> sessions = new ConcurrentHashMap<>();
	private final GeneralRocketSaver saver = new GeneralRocketSaver();

	public HeadlessAutomationBackend() {
		initializeCore();
	}

	@Override
	public String getBackendId() {
		return "headless";
	}

	@Override
	public List<AutomationTargetDescriptor> listTargets() {
		List<AutomationTargetDescriptor> targets = new ArrayList<>();
		for (DocumentSession session : sessions.values()) {
			targets.add(session.getTarget());
		}
		return targets;
	}

	@Override
	public DocumentSession attachTarget(String targetId) {
		DocumentSession session = sessions.get(targetId);
		if (session == null) {
			throw new IllegalArgumentException("Target not found: " + targetId);
		}
		return session;
	}

	@Override
	public DocumentSession openOrCreateDocument(Path path, boolean createNew) throws IOException, RocketLoadException {
		Path normalizedPath = path == null ? null : path.toAbsolutePath().normalize();
		boolean create = createNew || normalizedPath == null || !Files.exists(normalizedPath);

		OpenRocketDocument document;
		if (create) {
			document = OpenRocketDocumentFactory.createNewRocket();
		} else {
			document = new GeneralRocketLoader(normalizedPath.toFile()).load();
			document.setFile(normalizedPath.toFile());
			document.setSaved(true);
		}

		String targetId = "headless:" + UUID.randomUUID();
		AutomationTargetDescriptor target = new AutomationTargetDescriptor(
				targetId,
				"headless-document",
				displayName(normalizedPath, create),
				false,
				metadata(normalizedPath, create));

		DocumentSession session = new DocumentSession(
				getBackendId(),
				target,
				document,
				false,
				normalizedPath,
				(currentSession, savePath) -> saveDocument(currentSession, savePath),
				currentSession -> sessions.remove(currentSession.getTarget().id()));
		sessions.put(targetId, session);
		return session;
	}

	@Override
	public void close() throws IOException {
		List<IOException> failures = new ArrayList<>();
		for (DocumentSession session : sessions.values()) {
			try {
				session.close();
			} catch (IOException exception) {
				failures.add(exception);
			}
		}
		sessions.clear();
		if (!failures.isEmpty()) {
			throw failures.get(0);
		}
	}

	private void saveDocument(DocumentSession session, Path path) throws IOException {
		Path normalized = path.toAbsolutePath().normalize();
		if (normalized.getParent() != null) {
			Files.createDirectories(normalized.getParent());
		}
		try {
			saver.save(normalized.toFile(), session.getDocument());
		} catch (DecalNotFoundException exception) {
			throw new IOException("Failed to save document because a decal asset was missing", exception);
		}
		session.getDocument().setFile(normalized.toFile());
		session.getDocument().setSaved(true);
		session.setFilePath(normalized);
	}

	private static void initializeCore() {
		if (initialized) {
			return;
		}
		synchronized (INITIALIZATION_LOCK) {
			if (initialized) {
				return;
			}
			CoreModule coreModule = new CoreModule();
			Module pluginModule = new PluginModule();
			Injector injector = Guice.createInjector(coreModule, pluginModule);
			Application.setInjector(injector);
			coreModule.startLoader();
			initialized = true;
		}
	}

	private static String displayName(Path path, boolean create) {
		if (path == null) {
			return create ? "Untitled rocket" : "Detached rocket";
		}
		return path.getFileName().toString();
	}

	private static Map<String, Object> metadata(Path path, boolean create) {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("mode", "headless");
		metadata.put("created", create);
		if (path != null) {
			metadata.put("path", path.toString());
		}
		return metadata;
	}
}
