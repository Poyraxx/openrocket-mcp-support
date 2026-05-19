package info.openrocket.core.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.file.openrocket.importt.DocumentConfigAutomationSupport;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.rocketcomponent.position.AxialMethod;

class AutomationMetadataRegistryTest extends AutomationTestSupport {

	@Test
	void finTabPositionUsesDedicatedMetadataAndCompatibleChildrenComeFromDocumentConfig() {
		ComponentMetadataRegistry registry = new ComponentMetadataRegistry();
		assertTrue(registry.getComponentCatalog().containsKey("bodytube"));

		List<PropertyDescriptor> finProperties =
				DocumentConfigAutomationSupport.getPropertyDescriptors(TrapezoidFinSet.class);
		PropertyDescriptor tabPosition = finProperties.stream()
				.filter(descriptor -> "tabposition".equals(descriptor.name()))
				.findFirst()
				.orElse(null);

		assertNotNull(tabPosition);
		assertEquals("object", tabPosition.valueType());
		assertEquals("fin-tab-position", tabPosition.metadata().get("shape"));
		assertEquals(List.of("relativeto"), tabPosition.requiredAttributes());
		assertTrue(DocumentConfigAutomationSupport.getCompatibleChildAliases(new BodyTube()).contains("trapezoidfinset"));
		assertFalse(DocumentConfigAutomationSupport.getPropertyDescriptors(NoseCone.class).stream()
				.anyMatch(descriptor -> "foreradius".equals(descriptor.name())));

		TrapezoidFinSet finSet = new TrapezoidFinSet();
		finSet.setTabOffset(0.031);
		finSet.setTabOffsetMethod(AxialMethod.TOP);
		Map<String, Object> properties = DocumentConfigAutomationSupport.readProperties(finSet);

		@SuppressWarnings("unchecked")
		Map<String, Object> tabPayload = (Map<String, Object>) properties.get("tabposition");
		assertNotNull(tabPayload);
		assertEquals("top", tabPayload.get("relativeto"));
		assertEquals(0.031, ((Number) tabPayload.get("value")).doubleValue(), 1.0e-9);
	}
}
