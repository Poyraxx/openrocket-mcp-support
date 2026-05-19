package info.openrocket.core.automation;

import java.util.Map;

/**
 * Schema view for clients that need capability discovery.
 */
public record SchemaSnapshot(
		String sessionId,
		Map<String, Object> componentCatalog,
		Map<String, Object> simulationSchema,
		Map<String, Object> flightConfigurationSchema) {

	public SchemaSnapshot {
		componentCatalog = componentCatalog == null ? Map.of() : Map.copyOf(componentCatalog);
		simulationSchema = simulationSchema == null ? Map.of() : Map.copyOf(simulationSchema);
		flightConfigurationSchema = flightConfigurationSchema == null ? Map.of() : Map.copyOf(flightConfigurationSchema);
	}
}
