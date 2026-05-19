package info.openrocket.core.automation;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Utilities for converting automation payloads into JSON-safe structures.
 */
public final class AutomationJsonSupport {

	private static final Gson COMPACT_GSON = new GsonBuilder()
			.disableHtmlEscaping()
			.serializeNulls()
			.create();

	private AutomationJsonSupport() {
	}

	public static Object sanitizeForJson(Object value) {
		return sanitize(value, new IdentityHashMap<>());
	}

	public static String toCompactJson(Object value) {
		return COMPACT_GSON.toJson(sanitizeForJson(value));
	}

	public static String summarize(Object value, int maxLength) {
		String json = toCompactJson(value);
		if (json.length() <= maxLength) {
			return json;
		}
		return json.substring(0, Math.max(0, maxLength - 3)) + "...";
	}

	private static Object sanitize(Object value, IdentityHashMap<Object, Boolean> seen) {
		if (value == null) {
			return null;
		}
		if (value instanceof String || value instanceof Boolean) {
			return value;
		}
		if (value instanceof Character character) {
			return character.toString();
		}
		if (value instanceof Number number) {
			return sanitizeNumber(number);
		}
		if (value instanceof Enum<?> enumeration) {
			return enumeration.name();
		}
		if (value instanceof Instant
				|| value instanceof UUID
				|| value instanceof Path
				|| value instanceof File
				|| value instanceof Class<?>) {
			return String.valueOf(value);
		}
		if (value instanceof Map<?, ?> map) {
			if (seen.containsKey(value)) {
				return "[circular]";
			}
			seen.put(value, Boolean.TRUE);
			Map<String, Object> copy = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				copy.put(String.valueOf(entry.getKey()), sanitize(entry.getValue(), seen));
			}
			seen.remove(value);
			return copy;
		}
		if (value instanceof Iterable<?> iterable) {
			if (seen.containsKey(value)) {
				return "[circular]";
			}
			seen.put(value, Boolean.TRUE);
			List<Object> copy = new ArrayList<>();
			for (Object item : iterable) {
				copy.add(sanitize(item, seen));
			}
			seen.remove(value);
			return copy;
		}
		if (value.getClass().isArray()) {
			if (seen.containsKey(value)) {
				return "[circular]";
			}
			seen.put(value, Boolean.TRUE);
			int length = Array.getLength(value);
			List<Object> copy = new ArrayList<>(length);
			for (int i = 0; i < length; i++) {
				copy.add(sanitize(Array.get(value, i), seen));
			}
			seen.remove(value);
			return copy;
		}
		if (value.getClass().isRecord()) {
			if (seen.containsKey(value)) {
				return "[circular]";
			}
			seen.put(value, Boolean.TRUE);
			Map<String, Object> copy = new LinkedHashMap<>();
			for (RecordComponent component : value.getClass().getRecordComponents()) {
				try {
					Method accessor = component.getAccessor();
					copy.put(component.getName(), sanitize(accessor.invoke(value), seen));
				} catch (ReflectiveOperationException exception) {
					copy.put(component.getName(), "<error:" + exception.getClass().getSimpleName() + ">");
				}
			}
			seen.remove(value);
			return copy;
		}

		Package valuePackage = value.getClass().getPackage();
		String packageName = valuePackage == null ? "" : valuePackage.getName();
		if (packageName.startsWith("java.") || packageName.startsWith("javax.")) {
			return String.valueOf(value);
		}
		return String.valueOf(value);
	}

	private static Object sanitizeNumber(Number number) {
		if (number instanceof Double value) {
			return Double.isFinite(value) ? value : null;
		}
		if (number instanceof Float value) {
			return Float.isFinite(value) ? value : null;
		}
		return number;
	}
}
