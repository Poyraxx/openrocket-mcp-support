package info.openrocket.core.automation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import info.openrocket.core.file.RocketLoadException;

/**
 * Backend abstraction for OpenRocket automation targets.
 */
public interface AutomationBackend extends AutoCloseable {

	String getBackendId();

	List<AutomationTargetDescriptor> listTargets() throws IOException;

	DocumentSession attachTarget(String targetId) throws IOException;

	DocumentSession openOrCreateDocument(Path path, boolean createNew) throws IOException, RocketLoadException;

	@Override
	default void close() throws IOException {
		// Default no-op.
	}
}
