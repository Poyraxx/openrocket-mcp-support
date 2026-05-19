package info.openrocket.core.automation;

import java.util.List;
import java.util.Map;

/**
 * Snapshot view of a component in the document tree.
 */
public record ComponentRef(
		String id,
		String alias,
		String componentClass,
		String name,
		String parentId,
		int index,
		List<String> children,
		Map<String, Object> properties) {

	public ComponentRef {
		children = children == null ? List.of() : List.copyOf(children);
		properties = properties == null ? Map.of() : Map.copyOf(properties);
	}
}
