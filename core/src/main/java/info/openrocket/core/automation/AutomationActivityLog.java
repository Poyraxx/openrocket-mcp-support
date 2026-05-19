package info.openrocket.core.automation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Shared append-only activity log for OpenRocket AI automation.
 */
public final class AutomationActivityLog {

	private static final Path LOG_PATH = Path.of(
			System.getProperty("openrocket.remote.activityLogPath",
					Path.of(System.getProperty("user.home"), ".openrocket", "ai-automation.log").toString()));

	private AutomationActivityLog() {
	}

	public static Path getLogPath() {
		return LOG_PATH;
	}

	public static synchronized void log(String source, String message) {
		String safeSource = source == null || source.isBlank() ? "automation" : source;
		String safeMessage = message == null ? "" : message;
		String line = Instant.now() + " [" + safeSource + "] " + safeMessage + System.lineSeparator();
		try {
			if (LOG_PATH.getParent() != null) {
				Files.createDirectories(LOG_PATH.getParent());
			}
			Files.writeString(
					LOG_PATH,
					line,
					StandardCharsets.UTF_8,
					StandardOpenOption.CREATE,
					StandardOpenOption.APPEND);
		} catch (IOException ignored) {
			// Logging is best-effort only.
		}
	}
}
