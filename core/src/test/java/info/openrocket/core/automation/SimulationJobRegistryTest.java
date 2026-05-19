package info.openrocket.core.automation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.OpenRocketDocumentFactory;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.util.TestRockets;

class SimulationJobRegistryTest extends AutomationTestSupport {

	@Test
	void submittedSimulationProducesResultsAndTerminalJobState() throws Exception {
		OpenRocketDocument document = OpenRocketDocumentFactory.createDocumentFromRocket(TestRockets.makeEstesAlphaIII());
		DocumentSession session = newSession(document);

		Simulation simulation = new Simulation(document, document.getRocket());
		simulation.setName("Automation simulation");
		simulation.setFlightConfigurationId(TestRockets.TEST_FCID_0);
		simulation.getOptions().setISAAtmosphere(true);
		simulation.getOptions().setTimeStep(0.05);
		document.addSimulation(simulation);
		String simulationId = session.resolveSimulationId(simulation);

		try (SimulationJobRegistry jobs = new SimulationJobRegistry()) {
			SimulationJobRef submitted = jobs.submit(session, simulationId);
			SimulationJobRef current = submitted;

			long deadline = System.currentTimeMillis() + 15_000L;
			while (System.currentTimeMillis() < deadline &&
					("queued".equals(current.status()) || "running".equals(current.status()) || "cancelling".equals(current.status()))) {
				Thread.sleep(50L);
				current = jobs.getJob(submitted.jobId());
			}

			assertFalse("queued".equals(current.status()) || "running".equals(current.status()) || "cancelling".equals(current.status()));
			assertTrue("succeeded".equals(current.status()), "Expected simulation to finish successfully but was " + current.status());
			assertNotNull(current.finishedAt());

			SimulationResultView results = AutomationDocumentOps.getResults(session, simulationId);
			assertFalse(results.summary().isEmpty());
			assertFalse(results.branches().isEmpty());
			assertTrue(((Number) results.summary().get("maxAltitude")).doubleValue() > 0.0);
			Map<?, ?> currentAerodynamics = assertInstanceOf(Map.class, results.summary().get("currentAerodynamics"));
			assertTrue(((Number) currentAerodynamics.get("stabilityCalibers")).doubleValue() > 0.0);
			Map<?, ?> defaultMachSample = assertInstanceOf(Map.class, results.summary().get("defaultMachSample"));
			assertFalse(defaultMachSample.isEmpty());
			assertTrue(((Number) defaultMachSample.get("actualMach")).doubleValue() >= 0.0);
		}
	}
}
