package info.openrocket.core.automation;

import java.time.Instant;
import java.util.Map;

/**
 * Serializable simulation job status view.
 */
public record SimulationJobRef(
		String jobId,
		String sessionId,
		String simulationId,
		String status,
		Instant createdAt,
		Instant startedAt,
		Instant finishedAt,
		String message,
		Map<String, Object> metadata) {

	public SimulationJobRef {
		metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
	}
}
