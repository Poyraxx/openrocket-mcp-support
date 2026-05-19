package info.openrocket.core.automation;

import java.util.Map;

/**
 * Immutable description of an automation target.
 */
public record AutomationTargetDescriptor(
		String id,
		String kind,
		String displayName,
		boolean live,
		Map<String, Object> metadata) {

	public AutomationTargetDescriptor {
		metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
	}
}
