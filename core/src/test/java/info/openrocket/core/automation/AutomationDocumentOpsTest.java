package info.openrocket.core.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import info.openrocket.core.database.motor.MotorDatabase;
import info.openrocket.core.database.motor.ThrustCurveMotorSetDatabase;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.OpenRocketDocumentFactory;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.GeneralRocketSaver;
import info.openrocket.core.motor.MotorConfiguration;
import info.openrocket.core.motor.ThrustCurveMotor;
import info.openrocket.core.rocketcomponent.MotorMount;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.TestRockets;

class AutomationDocumentOpsTest extends AutomationTestSupport {

	@Test
	void currentSimulationOptionsSurviveCopyAndFileRoundTrip(@TempDir Path directory) throws Exception {
		OpenRocketDocument document = OpenRocketDocumentFactory.createDocumentFromRocket(TestRockets.makeEstesAlphaIII());
		DocumentSession session = newSession(document);
		Map<String, Object> options = Map.of(
				"randomSeed", 1234,
				"randomSeedFixed", true,
				"recoverySpeedWarning", 21.0,
				"drogueLowSpeedWarning", 4.0,
				"recoveryDrogueMainHighSpeedWarning", 32.0,
				"recoveryDrogueMainLowSpeedWarning", 16.0);

		List<Map<String, Object>> simulations = AutomationDocumentOps.manageSimulations(session, "create",
				Map.of("name", "Round trip", "flightConfigurationId", TestRockets.TEST_FCID_0.toString(), "options", options));
		String simulationId = (String) simulations.get(0).get("id");
		AutomationDocumentOps.manageSimulations(session, "copy", Map.of("simulationId", simulationId));
		Path file = directory.resolve("round-trip.ork");
		new GeneralRocketSaver().save(file.toFile(), document);
		DocumentSession reloaded = reloadSession(document, file);

		List<Map<String, Object>> restored = AutomationDocumentOps.manageSimulations(reloaded, "list", Map.of());
		assertEquals(2, restored.size());
		for (Map<String, Object> simulation : restored) {
			Map<?, ?> restoredOptions = assertInstanceOf(Map.class, simulation.get("options"));
			options.forEach((key, expected) -> assertEquals(expected, restoredOptions.get(key), key));
		}
		List<?> optionKeys = assertInstanceOf(List.class, AutomationDocumentOps.getSchema(session).simulationSchema().get("optionKeys"));
		assertTrue(optionKeys.containsAll(options.keySet()));
	}

	@Test
	void motorNozzleDiameterIsExposedInSnapshotsAndSavedDocuments(@TempDir Path directory) throws Exception {
		OpenRocketDocument document = OpenRocketDocumentFactory.createDocumentFromRocket(TestRockets.makeEstesAlphaIII());
		MotorMount mount = null;
		for (var component : document.getRocket()) {
			if (component instanceof MotorMount candidate && candidate.isMotorMount()) {
				mount = candidate;
				break;
			}
		}
		assertNotNull(mount);
		MotorConfiguration configuration = mount.getMotorConfig(TestRockets.TEST_FCID_0);
		configuration.setNozzleExitDiameter(0.005);
		Path file = directory.resolve("nozzle.ork");
		new GeneralRocketSaver().save(file.toFile(), document);
		DocumentSession reloaded = reloadSession(document, file);
		Map<String, Object> flightConfiguration = AutomationDocumentOps.getSnapshot(reloaded).flightConfigurations().stream()
				.filter(item -> TestRockets.TEST_FCID_0.toString().equals(item.get("id")))
				.findFirst().orElseThrow();
		List<?> motors = assertInstanceOf(List.class, flightConfiguration.get("motors"));
		assertTrue(motors.stream().anyMatch(item -> ((Map<?, ?>) item).get("nozzleExitDiameter").equals(0.005)));
	}

	private DocumentSession reloadSession(OpenRocketDocument document, Path file) throws Exception {
		ThrustCurveMotorSetDatabase database = new ThrustCurveMotorSetDatabase();
		for (var configuration : document.getRocket().getFlightConfigurations()) {
			for (MotorConfiguration motor : configuration.getAllMotors()) {
				if (motor.getMotor() instanceof ThrustCurveMotor thrustCurve) {
					database.addMotor(thrustCurve);
				}
			}
		}
		Injector original = Application.getInjector();
		Application.setInjector(original.createChildInjector(new AbstractModule() {
			@Override
			protected void configure() {
				bind(MotorDatabase.class).toInstance(database);
			}
		}));
		try {
			return newSession(new GeneralRocketLoader(file.toFile()).load());
		} finally {
			Application.setInjector(original);
		}
	}

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
