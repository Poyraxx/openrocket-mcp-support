package info.openrocket.core.automation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import info.openrocket.core.file.openrocket.importt.DocumentConfigAutomationSupport;
import info.openrocket.core.rocketcomponent.RocketComponent;

/**
 * Exposes the component catalog derived from {@code DocumentConfig}.
 */
public final class ComponentMetadataRegistry {

	private final Map<String, Object> componentCatalog;

	public ComponentMetadataRegistry() {
		this.componentCatalog = buildCatalog();
	}

	public Map<String, Object> getComponentCatalog() {
		return componentCatalog;
	}

	public List<PropertyDescriptor> getProperties(Class<? extends RocketComponent> componentClass) {
		return DocumentConfigAutomationSupport.getPropertyDescriptors(componentClass);
	}

	public List<String> getCompatibleChildren(RocketComponent parent) {
		return DocumentConfigAutomationSupport.getCompatibleChildAliases(parent);
	}

	private Map<String, Object> buildCatalog() {
		Map<String, Object> catalog = new LinkedHashMap<>();
		for (Map.Entry<String, Class<? extends RocketComponent>> entry :
				DocumentConfigAutomationSupport.getComponentAliases().entrySet()) {
			RocketComponent prototype = DocumentConfigAutomationSupport.instantiateComponent(entry.getKey());
			Map<String, Object> descriptor = new LinkedHashMap<>();
			descriptor.put("alias", entry.getKey());
			descriptor.put("aliases", DocumentConfigAutomationSupport.getAliases(entry.getValue()));
			descriptor.put("componentClass", entry.getValue().getName());
			descriptor.put("compatibleChildren", DocumentConfigAutomationSupport.getCompatibleChildAliases(prototype));
			descriptor.put("properties", DocumentConfigAutomationSupport.getPropertyDescriptors(entry.getValue()));
			catalog.put(entry.getKey(), descriptor);
		}
		return Map.copyOf(catalog);
	}
}
