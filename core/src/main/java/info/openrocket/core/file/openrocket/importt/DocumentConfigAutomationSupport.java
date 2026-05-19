package info.openrocket.core.file.openrocket.importt;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import info.openrocket.core.automation.PropertyDescriptor;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.material.Material;
import info.openrocket.core.preset.ComponentPreset;
import info.openrocket.core.rocketcomponent.ClusterConfiguration;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.FlightConfigurableParameterSet;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.position.AngleMethod;
import info.openrocket.core.rocketcomponent.position.AnglePositionable;
import info.openrocket.core.rocketcomponent.position.AxialMethod;
import info.openrocket.core.rocketcomponent.position.AxialPositionable;
import info.openrocket.core.rocketcomponent.position.RadiusMethod;
import info.openrocket.core.rocketcomponent.position.RadiusPositionable;
import info.openrocket.core.util.ORColor;
import info.openrocket.core.util.Reflection;

/**
 * Reuses {@link DocumentConfig}'s constructor/setter registry for automation.
 */
public final class DocumentConfigAutomationSupport {

	private static final String VALUE_KEY = "value";
	private static final Field REFLECTION_METHOD_FIELD = findDeclaredField(Reflection.Method.class, "method");

	private static final Map<String, Constructor<? extends RocketComponent>> CONSTRUCTORS =
			Collections.unmodifiableMap(new LinkedHashMap<>(DocumentConfig.constructors));
	private static final Map<String, List<String>> ALIASES_BY_CLASS = buildAliasesByClass();
	private static final Map<Class<?>, Map<String, PropertySupport>> PROPERTY_CACHE = new ConcurrentHashMap<>();

	private DocumentConfigAutomationSupport() {
	}

	public static Map<String, Class<? extends RocketComponent>> getComponentAliases() {
		Map<String, Class<? extends RocketComponent>> aliases = new LinkedHashMap<>();
		CONSTRUCTORS.forEach((alias, constructor) -> aliases.put(alias, constructor.getDeclaringClass()));
		return Collections.unmodifiableMap(aliases);
	}

	public static RocketComponent instantiateComponent(String alias) {
		Constructor<? extends RocketComponent> constructor = CONSTRUCTORS.get(normalize(alias));
		if (constructor == null) {
			throw new IllegalArgumentException("Unsupported component type: " + alias);
		}
		try {
			return constructor.newInstance();
		} catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
			throw new IllegalStateException("Unable to instantiate component type " + alias, e);
		}
	}

	public static String getPrimaryAlias(Class<? extends RocketComponent> componentClass) {
		List<String> aliases = ALIASES_BY_CLASS.get(componentClass.getSimpleName());
		if (aliases == null || aliases.isEmpty()) {
			return componentClass.getSimpleName().toLowerCase(Locale.ENGLISH);
		}
		return aliases.get(0);
	}

	public static List<String> getAliases(Class<? extends RocketComponent> componentClass) {
		List<String> aliases = ALIASES_BY_CLASS.get(componentClass.getSimpleName());
		return aliases == null ? List.of(getPrimaryAlias(componentClass)) : List.copyOf(aliases);
	}

	public static List<String> getCompatibleChildAliases(RocketComponent parent) {
		List<String> compatible = new ArrayList<>();
		for (Map.Entry<String, Constructor<? extends RocketComponent>> entry : CONSTRUCTORS.entrySet()) {
			if (parent.isCompatible(entry.getValue().getDeclaringClass())) {
				compatible.add(entry.getKey());
			}
		}
		compatible.sort(Comparator.naturalOrder());
		return compatible;
	}

	public static List<PropertyDescriptor> getPropertyDescriptors(Class<? extends RocketComponent> componentClass) {
		return propertySupports(componentClass).values().stream()
				.map(PropertySupport::descriptor)
				.toList();
	}

	public static Map<String, Object> readProperties(RocketComponent component) {
		Map<String, Object> values = new LinkedHashMap<>();
		for (Map.Entry<String, PropertySupport> entry : propertySupports(component.getClass()).entrySet()) {
			PropertySupport support = entry.getValue();
			if (support.reader() == null || !support.descriptor().readable()) {
				continue;
			}
			Object value = support.reader().apply(component);
			if (value != null) {
				values.put(entry.getKey(), value);
			}
		}
		return values;
	}

	public static void applyProperty(RocketComponent component, String propertyName, Object rawValue, WarningSet warnings) {
		String normalized = normalize(propertyName);
		PropertySupport support = propertySupports(component.getClass()).get(normalized);
		if (support == null || support.setter() == null || !support.descriptor().writable()) {
			throw new IllegalArgumentException("Property '" + propertyName + "' is not writable for " +
					component.getClass().getSimpleName());
		}

		ValuePayload payload = toPayload(rawValue);
		if (support.setter() instanceof OverrideSetter overrideSetter) {
			Method enabledMethod = reflectionMethodField(overrideSetter, "enabledMethod");
			String enabled = payload.attributes().get("enabled");
			if (enabledMethod != null && enabled != null && !Boolean.parseBoolean(enabled)) {
				try {
					enabledMethod.invoke(component, false);
					return;
				} catch (IllegalAccessException | InvocationTargetException e) {
					throw new IllegalStateException("Unable to disable override property '" + propertyName + "'", e);
				}
			}
		}
		support.setter().set(component, payload.value(), payload.attributes(), warnings);
	}

	private static Map<String, PropertySupport> propertySupports(Class<?> componentClass) {
		return PROPERTY_CACHE.computeIfAbsent(componentClass, DocumentConfigAutomationSupport::buildPropertySupports);
	}

	private static Map<String, PropertySupport> buildPropertySupports(Class<?> componentClass) {
		LinkedHashMap<String, PropertySupport> effective = new LinkedHashMap<>();
		LinkedHashSet<String> blocked = new LinkedHashSet<>();

		Class<?> current = componentClass;
		while (current != null && RocketComponent.class.isAssignableFrom(current)) {
			String prefix = current.getSimpleName() + ":";
			for (Map.Entry<String, Setter> entry : DocumentConfig.setters.entrySet()) {
				if (!entry.getKey().startsWith(prefix)) {
					continue;
				}
				String property = normalize(entry.getKey().substring(prefix.length()));
				if (effective.containsKey(property) || blocked.contains(property)) {
					continue;
				}
				if (entry.getValue() == null) {
					blocked.add(property);
					continue;
				}
				PropertySupport support = createSupport(property, entry.getValue());
				if (support != null) {
					effective.put(property, support);
				}
			}
			current = current.getSuperclass();
		}

		return Collections.unmodifiableMap(effective);
	}

	private static PropertySupport createSupport(String property, Setter setter) {
		if (setter instanceof StringSetter stringSetter) {
			return simpleSupport(property, setter, reflectionMethodField(stringSetter, "setMethod"), "string", null);
		}
		if (setter instanceof IntSetter intSetter) {
			return simpleSupport(property, setter, reflectionMethodField(intSetter, "setMethod"), "integer", null);
		}
		if (setter instanceof BooleanSetter booleanSetter) {
			return simpleSupport(property, setter, reflectionMethodField(booleanSetter, "setMethod"), "boolean", null);
		}
		if (setter instanceof EnumSetter<?> enumSetter) {
			Method setMethod = reflectionMethodField(enumSetter, "setter");
			Method configGetter = reflectionMethodField(enumSetter, "configurationGetter");
			Class<? extends Enum<?>> enumClass = enumClassField(enumSetter, "enumClass");
			Function<RocketComponent, Object> reader = readerForSetter(setMethod, configGetter, true);
			return new PropertySupport(
					new PropertyDescriptor(
							property,
							"enum",
							reader != null,
							true,
							null,
							enumValues(enumClass),
							List.of(),
							List.of(),
							Map.of("configurationScoped", configGetter != null)),
					setter,
					wrapEnumReader(reader));
		}
		if (setter instanceof FinTabPositionSetter) {
			return new PropertySupport(
					new PropertyDescriptor(
							property,
							"object",
							true,
							true,
							"m",
							enumValues(AxialMethod.class),
							List.of("relativeto"),
							List.of(),
							Map.of("shape", "fin-tab-position")),
					setter,
					component -> {
						if (!(component instanceof FinSet finSet)) {
							return null;
						}
						Map<String, Object> payload = new LinkedHashMap<>();
						payload.put(VALUE_KEY, finSet.getTabOffset());
						payload.put("relativeto", toXmlEnumName(finSet.getTabOffsetMethod()));
						return payload;
					});
		}
		if (setter instanceof DoubleSetter doubleSetter) {
			Method setMethod = reflectionMethodField(doubleSetter, "setMethod");
			Method configGetter = reflectionMethodField(doubleSetter, "configGetter");
			Method specialMethod = reflectionMethodField(doubleSetter, "specialMethod");
			String specialString = stringField(doubleSetter, "specialString");
			String valueType = "number";
			Function<RocketComponent, Object> baseReader = readerForSetter(setMethod, configGetter, false);
			Function<RocketComponent, Object> reader = baseReader;
			if (specialMethod != null) {
				Function<RocketComponent, Object> specialReader = readerForSetter(specialMethod, null, true);
				reader = component -> {
					Boolean automatic = asBoolean(specialReader == null ? null : specialReader.apply(component));
					Object currentValue = baseReader == null ? null : baseReader.apply(component);
					if (Boolean.TRUE.equals(automatic) && specialString != null) {
						Map<String, Object> payload = new LinkedHashMap<>();
						payload.put(VALUE_KEY, specialString);
						if (currentValue != null) {
							payload.put("rawValue", currentValue);
						}
						return payload;
					}
					return currentValue;
				};
			}

			Map<String, Object> metadata = new LinkedHashMap<>();
			if (specialString != null) {
				metadata.put("specialValue", specialString);
			}
			if (configGetter != null) {
				metadata.put("configurationScoped", true);
			}
			return new PropertySupport(
					new PropertyDescriptor(
							property,
							valueType,
							reader != null,
							true,
							guessUnit(property, setter),
							List.of(),
							List.of(),
							List.of(),
							metadata),
					setter,
					reader);
		}
		if (setter instanceof OverrideSetter overrideSetter) {
			Method valueSetter = reflectionMethodField(overrideSetter, "setMethod");
			Method enabledSetter = reflectionMethodField(overrideSetter, "enabledMethod");
			Function<RocketComponent, Object> valueReader = readerForSetter(valueSetter, null, false);
			Function<RocketComponent, Object> enabledReader = readerForSetter(enabledSetter, null, true);
			return new PropertySupport(
					new PropertyDescriptor(
							property,
							"object",
							valueReader != null || enabledReader != null,
							true,
							guessUnit(property, setter),
							List.of(),
							List.of(),
							List.of(),
							Map.of("shape", "override")),
					setter,
					component -> {
						Map<String, Object> payload = new LinkedHashMap<>();
						Boolean enabled = asBoolean(enabledReader == null ? null : enabledReader.apply(component));
						if (enabled != null) {
							payload.put("enabled", enabled);
						}
						Object value = valueReader == null ? null : valueReader.apply(component);
						if (value != null) {
							payload.put(VALUE_KEY, value);
						}
						return payload.isEmpty() ? null : payload;
					});
		}
		if (setter instanceof ColorSetter colorSetter) {
			Method setMethod = reflectionMethodField(colorSetter, "setMethod");
			Function<RocketComponent, Object> baseReader = readerForSetter(setMethod, null, false);
			return new PropertySupport(
					new PropertyDescriptor(
							property,
							"object",
							baseReader != null,
							true,
							null,
							List.of(),
							List.of("red", "green", "blue"),
							List.of(),
							Map.of("shape", "color")),
					setter,
					component -> toColorMap((ORColor) (baseReader == null ? null : baseReader.apply(component))));
		}
		if (setter instanceof MaterialSetter materialSetter) {
			Method setMethod = reflectionMethodField(materialSetter, "setMethod");
			Function<RocketComponent, Object> baseReader = readerForSetter(setMethod, null, false);
			return new PropertySupport(
					new PropertyDescriptor(
							property,
							"object",
							baseReader != null,
							true,
							null,
							List.of(),
							List.of("density"),
							List.of("shearModulus", "type", "group"),
							Map.of("shape", "material")),
					setter,
					component -> toMaterialMap((Material) (baseReader == null ? null : baseReader.apply(component))));
		}
		if (setter instanceof AxialPositionSetter) {
			return new PropertySupport(
					new PropertyDescriptor(
							property,
							"object",
							true,
							true,
							"m",
							enumValues(AxialMethod.class),
							List.of("method"),
							List.of(),
							Map.of("shape", "axial-position")),
					setter,
					DocumentConfigAutomationSupport::toAxialPositionMap);
		}
		if (setter instanceof RadiusPositionSetter) {
			return new PropertySupport(
					new PropertyDescriptor(
							property,
							"object",
							true,
							true,
							"m",
							enumValues(RadiusMethod.class),
							List.of(),
							List.of("method"),
							Map.of("shape", "radius-position")),
					setter,
					DocumentConfigAutomationSupport::toRadiusPositionMap);
		}
		if (setter instanceof AnglePositionSetter) {
			return new PropertySupport(
					new PropertyDescriptor(
							property,
							"object",
							true,
							true,
							"degrees",
							enumValues(AngleMethod.class),
							List.of(),
							List.of("method"),
							Map.of("shape", "angle-position")),
					setter,
					DocumentConfigAutomationSupport::toAnglePositionMap);
		}
		if (setter instanceof ClusterConfigurationSetter) {
			return new PropertySupport(
					new PropertyDescriptor(
							property,
							"string",
							true,
							true,
							null,
							clusterValues(),
							List.of(),
							List.of(),
							Map.of("shape", "cluster-configuration")),
					setter,
					component -> {
						if (component instanceof info.openrocket.core.rocketcomponent.Clusterable clusterable) {
							ClusterConfiguration configuration = clusterable.getClusterConfiguration();
							return configuration == null ? null : configuration.getXMLName();
						}
						return null;
					});
		}
		if (setter instanceof ComponentPresetSetter) {
			return new PropertySupport(
					new PropertyDescriptor(
							property,
							"object",
							true,
							true,
							null,
							List.of(),
							List.of("manufacturer", "partno"),
							List.of("digest", "type"),
							Map.of("shape", "component-preset")),
					setter,
					component -> {
						ComponentPreset preset = component.getPresetComponent();
						if (preset == null) {
							return null;
						}
						Map<String, Object> payload = new LinkedHashMap<>();
						payload.put("manufacturer", preset.getManufacturer().getSimpleName());
						payload.put("partno", preset.getPartNo());
						payload.put("digest", preset.getDigest());
						payload.put("type", preset.getType().name());
						return payload;
					});
		}
		return null;
	}

	private static PropertySupport simpleSupport(String property, Setter setter, Method setterMethod, String type,
			String unitHint) {
		Function<RocketComponent, Object> reader = readerForSetter(setterMethod, null, "boolean".equals(type));
		return new PropertySupport(
				new PropertyDescriptor(
						property,
						type,
						reader != null,
						true,
						unitHint != null ? unitHint : guessUnit(property, setter),
						List.of(),
						List.of(),
						List.of(),
						Map.of()),
				setter,
				reader);
	}

	private static Function<RocketComponent, Object> wrapEnumReader(Function<RocketComponent, Object> delegate) {
		if (delegate == null) {
			return null;
		}
		return component -> {
			Object value = delegate.apply(component);
			if (value instanceof Enum<?> enumeration) {
				return toXmlEnumName(enumeration);
			}
			return value;
		};
	}

	private static Function<RocketComponent, Object> readerForSetter(Method setterMethod, Method configurationGetter,
			boolean preferBooleanGetter) {
		if (setterMethod == null) {
			return null;
		}
		String suffix = setterMethod.getName().startsWith("set") ? setterMethod.getName().substring(3) : null;
		if (suffix == null || suffix.isEmpty()) {
			return null;
		}
		Method getter = findGetter(setterMethod.getDeclaringClass(), suffix, preferBooleanGetter);
		if (getter == null) {
			return null;
		}

		return component -> {
			Object target = component;
			if (configurationGetter != null) {
				try {
					Object config = configurationGetter.invoke(component);
					if (config instanceof FlightConfigurableParameterSet<?> parameterSet) {
						target = parameterSet.getDefault();
					}
				} catch (IllegalAccessException | InvocationTargetException e) {
					throw new IllegalStateException("Unable to access configuration getter", e);
				}
			}

			try {
				return getter.invoke(target);
			} catch (IllegalAccessException | InvocationTargetException e) {
				throw new IllegalStateException("Unable to read property via " + getter, e);
			}
		};
	}

	private static Method findGetter(Class<?> declaringClass, String suffix, boolean preferBooleanGetter) {
		List<String> candidates = new ArrayList<>();
		if (preferBooleanGetter) {
			candidates.add("is" + suffix);
		}
		candidates.add("get" + suffix);
		if (!preferBooleanGetter) {
			candidates.add("is" + suffix);
		}
		for (String candidate : candidates) {
			try {
				return declaringClass.getMethod(candidate);
			} catch (NoSuchMethodException ignored) {
				// Try next candidate.
			}
		}
		return null;
	}

	private static ValuePayload toPayload(Object rawValue) {
		if (rawValue instanceof Map<?, ?> map) {
			HashMap<String, String> attributes = new HashMap<>();
			Object value = "";
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				String key = String.valueOf(entry.getKey());
				if (VALUE_KEY.equals(key)) {
					value = entry.getValue();
				} else {
					attributes.put(key, stringify(entry.getValue()));
				}
			}
			return new ValuePayload(stringify(value), attributes);
		}
		return new ValuePayload(stringify(rawValue), new HashMap<>());
	}

	private static String stringify(Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof Enum<?> enumeration) {
			return toXmlEnumName(enumeration);
		}
		return String.valueOf(value);
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
	}

	private static String guessUnit(String property, Setter setter) {
		if (property.contains("mass")) {
			return "kg";
		}
		if (property.contains("cd")) {
			return "coefficient";
		}
		if (property.contains("angle") || property.contains("direction") || property.equals("rotation") ||
				property.equals("cant")) {
			return "degrees";
		}
		if (property.contains("delay")) {
			return "s";
		}
		if (property.contains("count")) {
			return null;
		}
		if (setter instanceof IntSetter || setter instanceof BooleanSetter || setter instanceof EnumSetter<?>) {
			return null;
		}
		return "m";
	}

	private static Method reflectionMethodField(Object source, String fieldName) {
		Object value = fieldValue(source, fieldName);
		if (value == null) {
			return null;
		}
		return unwrapReflectionMethod((Reflection.Method) value);
	}

	private static String stringField(Object source, String fieldName) {
		Object value = fieldValue(source, fieldName);
		return value == null ? null : String.valueOf(value);
	}

	@SuppressWarnings("unchecked")
	private static Class<? extends Enum<?>> enumClassField(Object source, String fieldName) {
		return (Class<? extends Enum<?>>) fieldValue(source, fieldName);
	}

	private static Object fieldValue(Object source, String fieldName) {
		Field field = findDeclaredFieldOrNull(source.getClass(), fieldName);
		if (field == null) {
			return null;
		}
		try {
			field.setAccessible(true);
			return field.get(source);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Unable to access field " + fieldName, e);
		}
	}

	private static Field findDeclaredField(Class<?> sourceClass, String fieldName) {
		Class<?> current = sourceClass;
		while (current != null) {
			try {
				Field field = current.getDeclaredField(fieldName);
				field.setAccessible(true);
				return field;
			} catch (NoSuchFieldException ignored) {
				current = current.getSuperclass();
			}
		}
		throw new IllegalStateException("Field '" + fieldName + "' not found on " + sourceClass);
	}

	private static Field findDeclaredFieldOrNull(Class<?> sourceClass, String fieldName) {
		Class<?> current = sourceClass;
		while (current != null) {
			try {
				Field field = current.getDeclaredField(fieldName);
				field.setAccessible(true);
				return field;
			} catch (NoSuchFieldException ignored) {
				current = current.getSuperclass();
			}
		}
		return null;
	}

	private static Method unwrapReflectionMethod(Reflection.Method wrapper) {
		if (wrapper == null) {
			return null;
		}
		try {
			return (Method) REFLECTION_METHOD_FIELD.get(wrapper);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Unable to unwrap reflection method", e);
		}
	}

	private static Map<String, List<String>> buildAliasesByClass() {
		Map<String, List<String>> aliases = new LinkedHashMap<>();
		for (Map.Entry<String, Constructor<? extends RocketComponent>> entry : CONSTRUCTORS.entrySet()) {
			String key = entry.getValue().getDeclaringClass().getSimpleName();
			aliases.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry.getKey());
		}
		aliases.replaceAll((ignored, value) -> List.copyOf(value));
		return Collections.unmodifiableMap(aliases);
	}

	private static List<String> enumValues(Class<? extends Enum<?>> enumClass) {
		if (enumClass == null) {
			return List.of();
		}
		List<String> values = new ArrayList<>();
		for (Enum<?> constant : enumClass.getEnumConstants()) {
			values.add(toXmlEnumName(constant));
		}
		return List.copyOf(values);
	}

	private static List<String> clusterValues() {
		List<String> values = new ArrayList<>();
		for (ClusterConfiguration configuration : ClusterConfiguration.CONFIGURATIONS) {
			values.add(configuration.getXMLName());
		}
		return List.copyOf(values);
	}

	private static String toXmlEnumName(Enum<?> enumeration) {
		return enumeration.name().toLowerCase(Locale.ENGLISH).replace("_", "");
	}

	private static Map<String, Object> toColorMap(ORColor color) {
		if (color == null) {
			return null;
		}
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("red", color.getRed());
		payload.put("green", color.getGreen());
		payload.put("blue", color.getBlue());
		if (color.getAlpha() != 255) {
			payload.put("alpha", color.getAlpha());
		}
		return payload;
	}

	private static Map<String, Object> toMaterialMap(Material material) {
		if (material == null) {
			return null;
		}
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put(VALUE_KEY, material.getName());
		payload.put("density", material.getDensity());
		payload.put("shearModulus", material.getInPlaneShearModulus());
		payload.put("type", material.getType().name().toLowerCase(Locale.ENGLISH));
		if (material.getGroup() != null) {
			payload.put("group", material.getGroup().getDatabaseString());
		}
		return payload;
	}

	private static Map<String, Object> toAxialPositionMap(RocketComponent component) {
		if (!(component instanceof AxialPositionable axialPositionable)) {
			return null;
		}
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put(VALUE_KEY, axialPositionable.getAxialOffset());
		payload.put("method", toXmlEnumName(axialPositionable.getAxialMethod()));
		return payload;
	}

	private static Map<String, Object> toRadiusPositionMap(RocketComponent component) {
		if (!(component instanceof RadiusPositionable radiusPositionable)) {
			return null;
		}
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put(VALUE_KEY, radiusPositionable.getRadiusOffset());
		payload.put("method", toXmlEnumName(radiusPositionable.getRadiusMethod()));
		return payload;
	}

	private static Map<String, Object> toAnglePositionMap(RocketComponent component) {
		if (!(component instanceof AnglePositionable anglePositionable)) {
			return null;
		}
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put(VALUE_KEY, Math.toDegrees(anglePositionable.getAngleOffset()));
		payload.put("method", toXmlEnumName(anglePositionable.getAngleMethod()));
		return payload;
	}

	private static Boolean asBoolean(Object value) {
		return value instanceof Boolean booleanValue ? booleanValue : null;
	}

	private record PropertySupport(PropertyDescriptor descriptor, Setter setter, Function<RocketComponent, Object> reader) {
	}

	private record ValuePayload(String value, HashMap<String, String> attributes) {
	}
}
