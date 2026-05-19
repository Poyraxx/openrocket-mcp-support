package info.openrocket.core.automation;

import java.util.Map;

/**
 * Generic mutation envelope consumed by automation APIs.
 */
public record MutationRequest(String action, Map<String, Object> arguments) {

	public MutationRequest {
		arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
	}
}
