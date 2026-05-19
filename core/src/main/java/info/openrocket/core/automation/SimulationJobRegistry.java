package info.openrocket.core.automation;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import info.openrocket.core.document.Simulation;
import info.openrocket.core.simulation.exception.SimulationCancelledException;
import info.openrocket.core.simulation.exception.SimulationException;
import info.openrocket.core.simulation.listeners.system.InterruptListener;

/**
 * Runs simulations asynchronously and exposes job state.
 */
public final class SimulationJobRegistry implements AutoCloseable {

	private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
		Thread thread = new Thread(runnable, "openrocket-automation-sim");
		thread.setDaemon(true);
		return thread;
	});
	private final Map<String, JobState> jobs = new ConcurrentHashMap<>();

	public SimulationJobRef submit(DocumentSession session, String simulationId) {
		Objects.requireNonNull(session, "session");
		Simulation simulation = AutomationDocumentOps.requireSimulation(session, simulationId);
		JobState state = new JobState(session, simulationId, simulation);
		state.future = executor.submit(() -> run(state));
		jobs.put(state.jobId, state);
		return state.toView();
	}

	public SimulationJobRef getJob(String jobId) {
		JobState state = jobs.get(jobId);
		if (state == null) {
			throw new IllegalArgumentException("Simulation job not found: " + jobId);
		}
		return state.toView();
	}

	public boolean cancel(String jobId) {
		JobState state = jobs.get(jobId);
		if (state == null) {
			throw new IllegalArgumentException("Simulation job not found: " + jobId);
		}
		state.status = "cancelling";
		state.message = "Cancellation requested";
		return state.future != null && state.future.cancel(true);
	}

	@Override
	public void close() {
		executor.shutdownNow();
	}

	private void run(JobState state) {
		state.status = "running";
		state.startedAt = Instant.now();
		try {
			state.simulation.simulate(InterruptListener.INSTANCE);
			state.status = "succeeded";
			state.message = "Simulation completed";
		} catch (SimulationCancelledException cancelled) {
			state.status = "cancelled";
			state.message = cancelled.getMessage();
		} catch (SimulationException simulationException) {
			state.status = "failed";
			state.message = simulationException.getMessage();
		} catch (RuntimeException runtimeException) {
			if (Thread.currentThread().isInterrupted()) {
				state.status = "cancelled";
				state.message = "Simulation interrupted";
			} else {
				state.status = "failed";
				state.message = runtimeException.getMessage();
			}
		} finally {
			state.finishedAt = Instant.now();
		}
	}

	private static final class JobState {
		private final String jobId;
		private final DocumentSession session;
		private final String simulationId;
		private final Simulation simulation;
		private final Instant createdAt = Instant.now();

		private volatile String status = "queued";
		private volatile Instant startedAt;
		private volatile Instant finishedAt;
		private volatile String message = "Queued";
		private volatile Future<?> future;

		private JobState(DocumentSession session, String simulationId, Simulation simulation) {
			this.jobId = "job-" + java.util.UUID.randomUUID();
			this.session = session;
			this.simulationId = simulationId;
			this.simulation = simulation;
		}

		private SimulationJobRef toView() {
			return new SimulationJobRef(
					jobId,
					session.getSessionId(),
					simulationId,
					status,
					createdAt,
					startedAt,
					finishedAt,
					message,
					Map.of(
							"simulationName", simulation.getName(),
							"simulationStatus", simulation.getStatus().name().toLowerCase(java.util.Locale.ENGLISH)));
		}
	}
}
