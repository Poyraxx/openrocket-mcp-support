package info.openrocket.core.automation;

import java.util.List;
import java.util.Map;

/**
 * Metadata for a writable/readable component property.
 */
public record PropertyDescriptor(
		String name,
		String valueType,
		boolean readable,
		boolean writable,
		String unitHint,
		List<String> enumValues,
		List<String> requiredAttributes,
		List<String> optionalAttributes,
		Map<String, Object> metadata) {

	public PropertyDescriptor {
		enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
		requiredAttributes = requiredAttributes == null ? List.of() : List.copyOf(requiredAttributes);
		optionalAttributes = optionalAttributes == null ? List.of() : List.copyOf(optionalAttributes);
		metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
	}
}
