package info.openrocket.core.automation;

import java.util.List;
import java.util.Map;

/**
 * Serializable document snapshot for automation clients.
 */
public record DocumentSnapshot(
		String sessionId,
		String targetId,
		boolean live,
		String filePath,
		boolean saved,
		String rootComponentId,
		Map<String, Object> documentProperties,
		Map<String, Object> currentAerodynamics,
		List<ComponentRef> components,
		List<Map<String, Object>> flightConfigurations,
		List<Map<String, Object>> simulations) {

	public DocumentSnapshot {
		documentProperties = documentProperties == null ? Map.of() : Map.copyOf(documentProperties);
		currentAerodynamics = currentAerodynamics == null ? Map.of() : Map.copyOf(currentAerodynamics);
		components = components == null ? List.of() : List.copyOf(components);
		flightConfigurations = flightConfigurations == null ? List.of() : List.copyOf(flightConfigurations);
		simulations = simulations == null ? List.of() : List.copyOf(simulations);
	}
}
