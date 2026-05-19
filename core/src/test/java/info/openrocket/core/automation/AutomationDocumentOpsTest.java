package info.openrocket.core.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.OpenRocketDocumentFactory;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.util.TestRockets;

class AutomationDocumentOpsTest extends AutomationTestSupport {

	@Test
	void snapshotIncludesExactCurrentAerodynamicsForSelectedConfiguration() {
		OpenRocketDocument document = OpenRocketDocumentFactory.createDocumentFromRocket(TestRockets.makeEstesAlphaIII());
		DocumentSession session = newSession(document);

		DocumentSnapshot snapshot = AutomationDocumentOps.getSnapshot(session);
		Map<String, Object> aerodynamics = snapshot.currentAerodynamics();

		assertFalse(aerodynamics.isEmpty());
		assertEquals(document.getRocket().getSelectedConfiguration().getFlightConfigurationID().toString(),
				aerodynamics.get("flightConfigurationId"));
		assertTrue(((Number) aerodynamics.get("diameter")).doubleValue() > 0.0);
		assertTrue(((Number) aerodynamics.get("cpLocation")).doubleValue() >
				((Number) aerodynamics.get("cgLocation")).doubleValue());
		assertTrue(((Number) aerodynamics.get("stabilityCalibers")).doubleValue() > 0.0);

		if (!snapshot.flightConfigurations().isEmpty()) {
			Map<String, Object> flightConfiguration = snapshot.flightConfigurations().stream()
					.filter(payload -> aerodynamics.get("flightConfigurationId").equals(payload.get("id")))
					.findFirst()
					.orElse(snapshot.flightConfigurations().get(0));
		Map<?, ?> nestedAerodynamics = assertInstanceOf(Map.class, flightConfiguration.get("currentAerodynamics"));
		assertFalse(nestedAerodynamics.isEmpty());
	}
	}

	@Test
	void resultsExposeCurrentAerodynamicsEvenBeforeRunningASimulation() {
		OpenRocketDocument document = OpenRocketDocumentFactory.createDocumentFromRocket(TestRockets.makeEstesAlphaIII());
		DocumentSession session = newSession(document);
		Simulation simulation = new Simulation(document, document.getRocket());
		simulation.setName("Not run yet");
		document.addSimulation(simulation);

		SimulationResultView result = AutomationDocumentOps.getResults(session, session.resolveSimulationId(simulation));
		Map<?, ?> aerodynamics = assertInstanceOf(Map.class, result.summary().get("currentAerodynamics"));

		assertFalse(aerodynamics.isEmpty());
		assertTrue(((Number) aerodynamics.get("stabilityCalibers")).doubleValue() > 0.0);
		assertTrue(result.branches().isEmpty());
	}

	@Test
	void batchedMutationCreatesMultipleComponentsAndSingleUndoRevertsTheWholeBatch() {
		OpenRocketDocument document = OpenRocketDocumentFactory.createNewRocket();
		document.clearUndo();
		DocumentSession session = newSession(document);

		MutationResult result = AutomationDocumentOps.mutate(
				session,
				List.of(
						new MutationRequest("add_component", Map.of(
								"parentId", stageId(session),
								"componentType", "bodytube",
								"properties", Map.of(
										"name", "Automation body tube",
										"length", 0.30,
										"radius", 0.018))),
						new MutationRequest("add_component", Map.of(
								"parentId", stageId(session),
								"componentType", "nosecone",
								"properties", Map.of("name", "Automation nose cone")))),
				"Create airframe");

		assertTrue(result.success());
		assertEquals(2, result.operations().size());
		assertEquals(1, componentsByAlias(session, "bodytube").size());
		assertEquals(1, componentsByAlias(session, "nosecone").size());

		document.undo();

		assertTrue(componentsByAlias(session, "bodytube").isEmpty());
		assertTrue(componentsByAlias(session, "nosecone").isEmpty());
	}

	@Test
	void failedMutationRollsBackEarlierOperationsInTheSameBatch() {
		OpenRocketDocument document = OpenRocketDocumentFactory.createNewRocket();
		document.clearUndo();
		DocumentSession session = newSession(document);

		MutationResult result = AutomationDocumentOps.mutate(
				session,
				List.of(
						new MutationRequest("add_component", Map.of(
								"parentId", stageId(session),
								"componentType", "bodytube",
								"properties", Map.of("name", "Rollback body tube"))),
						new MutationRequest("add_component", Map.of(
								"parentId", rootComponentId(session),
								"componentType", "trapezoidfinset"))),
				"Should rollback");

		assertFalse(result.success());
		assertNotNull(result.snapshot());
		assertTrue(result.warnings().stream().anyMatch(message -> message != null && !message.isBlank()));
		assertTrue(componentsByAlias(session, "bodytube").isEmpty());
	}

	@Test
	void removedComponentReferencesAreRejectedAndRolledBack() {
		OpenRocketDocument document = OpenRocketDocumentFactory.createNewRocket();
		document.clearUndo();
		DocumentSession session = newSession(document);

		MutationResult createResult = AutomationDocumentOps.mutate(
				session,
				List.of(new MutationRequest("add_component", Map.of(
						"parentId", stageId(session),
						"componentType", "bodytube",
						"properties", Map.of("name", "Temporary tube")))),
				"Create temporary tube");
		assertTrue(createResult.success());

		String componentId = componentsByAlias(session, "bodytube").get(0).id();
		MutationResult rollbackResult = AutomationDocumentOps.mutate(
				session,
				List.of(
						new MutationRequest("remove_component", Map.of("componentId", componentId)),
						new MutationRequest("set_properties", Map.of(
								"componentId", componentId,
								"properties", Map.of("length", 1.2)))),
				"Remove then mutate");

		assertFalse(rollbackResult.success());
		assertEquals(1, componentsByAlias(session, "bodytube").size());
	}
}
