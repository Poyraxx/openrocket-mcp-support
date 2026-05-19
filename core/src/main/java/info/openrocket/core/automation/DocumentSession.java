package info.openrocket.core.automation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;

/**
 * Mutable session object representing one attached document.
 */
public final class DocumentSession {

	@FunctionalInterface
	public interface Saver {
		void save(DocumentSession session, Path path) throws IOException;
	}

	@FunctionalInterface
	public interface Closer {
		void close(DocumentSession session) throws IOException;
	}

	private final String sessionId;
	private final String backendId;
	private final AutomationTargetDescriptor target;
	private final OpenRocketDocument document;
	private final boolean live;
	private final Saver saver;
	private final Closer closer;

	private final IdentityHashMap<Simulation, String> simulationIds = new IdentityHashMap<>();
	private final java.util.concurrent.atomic.AtomicInteger simulationSequence = new java.util.concurrent.atomic.AtomicInteger(1);

	private volatile Path filePath;
	private volatile boolean closed;

	public DocumentSession(String backendId, AutomationTargetDescriptor target, OpenRocketDocument document,
			boolean live, Path filePath, Saver saver, Closer closer) {
		this.sessionId = UUID.randomUUID().toString();
		this.backendId = Objects.requireNonNull(backendId, "backendId");
		this.target = Objects.requireNonNull(target, "target");
		this.document = Objects.requireNonNull(document, "document");
		this.live = live;
		this.filePath = filePath;
		this.saver = saver;
		this.closer = closer;
	}

	public String getSessionId() {
		return sessionId;
	}

	public String getBackendId() {
		return backendId;
	}

	public AutomationTargetDescriptor getTarget() {
		return target;
	}

	public OpenRocketDocument getDocument() {
		return document;
	}

	public boolean isLive() {
		return live;
	}

	public Path getFilePath() {
		return filePath;
	}

	public synchronized void setFilePath(Path filePath) {
		this.filePath = filePath;
	}

	public boolean isClosed() {
		return closed;
	}

	public synchronized String resolveSimulationId(Simulation simulation) {
		Objects.requireNonNull(simulation, "simulation");
		String existing = simulationIds.get(simulation);
		if (existing != null) {
			return existing;
		}
		String created = "sim-" + simulationSequence.getAndIncrement();
		simulationIds.put(simulation, created);
		return created;
	}

	public synchronized Simulation getSimulationById(String simulationId) {
		for (Simulation simulation : document.getSimulations()) {
			if (resolveSimulationId(simulation).equals(simulationId)) {
				return simulation;
			}
		}
		return null;
	}

	public synchronized List<String> getKnownSimulationIds() {
		List<String> ids = new ArrayList<>();
		for (Simulation simulation : document.getSimulations()) {
			ids.add(resolveSimulationId(simulation));
		}
		return ids;
	}

	public synchronized void pruneSimulationIds() {
		List<Simulation> existing = document.getSimulations();
		simulationIds.entrySet().removeIf(entry -> !existing.contains(entry.getKey()));
	}

	public void save(Path path) throws IOException {
		Path targetPath = path == null ? filePath : path;
		if (targetPath == null) {
			throw new IllegalArgumentException("No save path specified");
		}
		if (saver == null) {
			throw new UnsupportedOperationException("Session does not support save");
		}
		saver.save(this, targetPath);
		this.filePath = targetPath;
	}

	public synchronized void close() throws IOException {
		if (closed) {
			return;
		}
		closed = true;
		if (closer != null) {
			closer.close(this);
		}
	}
}
