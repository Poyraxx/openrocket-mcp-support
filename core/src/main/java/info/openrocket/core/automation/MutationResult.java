package info.openrocket.core.automation;

import java.util.List;
import java.util.Map;

/**
 * Result of one batched mutation request.
 */
public record MutationResult(
		String sessionId,
		boolean success,
		List<String> warnings,
		List<Map<String, Object>> operations,
		DocumentSnapshot snapshot) {

	public MutationResult {
		warnings = warnings == null ? List.of() : List.copyOf(warnings);
		operations = operations == null ? List.of() : List.copyOf(operations);
	}
}
