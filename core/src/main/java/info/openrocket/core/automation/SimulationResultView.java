package info.openrocket.core.automation;

import java.util.List;
import java.util.Map;

/**
 * Serializable simulation results with summary and branch data.
 */
public record SimulationResultView(
		String simulationId,
		String simulationName,
		String status,
		Map<String, Object> summary,
		List<String> warnings,
		List<Map<String, Object>> branches) {

	public SimulationResultView {
		summary = summary == null ? Map.of() : Map.copyOf(summary);
		warnings = warnings == null ? List.of() : List.copyOf(warnings);
		branches = branches == null ? List.of() : List.copyOf(branches);
	}
}
