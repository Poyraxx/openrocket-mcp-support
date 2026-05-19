package info.openrocket.core.automation;

import java.util.List;
import java.util.Map;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.util.BaseTestCase;

abstract class AutomationTestSupport extends BaseTestCase {

	protected DocumentSession newSession(OpenRocketDocument document) {
		return new DocumentSession(
				"test-backend",
				new AutomationTargetDescriptor("test-target", "test", "Automation Test", false, Map.of()),
				document,
				false,
				null,
				null,
				null);
	}

	protected String rootComponentId(DocumentSession session) {
		return AutomationDocumentOps.getSnapshot(session).rootComponentId();
	}

	protected String stageId(DocumentSession session) {
		DocumentSnapshot snapshot = AutomationDocumentOps.getSnapshot(session);
		return snapshot.components().stream()
				.filter(component -> snapshot.rootComponentId().equals(component.parentId()))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Expected a default stage component"))
				.id();
	}

	protected List<ComponentRef> componentsByAlias(DocumentSession session, String alias) {
		return AutomationDocumentOps.getSnapshot(session).components().stream()
				.filter(component -> alias.equals(component.alias()))
				.toList();
	}
}
