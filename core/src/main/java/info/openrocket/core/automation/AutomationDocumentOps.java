package info.openrocket.core.automation;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import info.openrocket.core.aerodynamics.AerodynamicCalculator;
import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.DatabaseMotorFinder;
import info.openrocket.core.file.openrocket.importt.DocumentConfigAutomationSupport;
import info.openrocket.core.logging.Warning;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.masscalc.MassCalculator;
import info.openrocket.core.motor.IgnitionEvent;
import info.openrocket.core.motor.Motor;
import info.openrocket.core.motor.MotorConfiguration;
import info.openrocket.core.motor.ThrustCurveMotor;
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.FlightConfigurableComponent;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.FlightConfigurationId;
import info.openrocket.core.rocketcomponent.MotorMount;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.FlightEvent;
import info.openrocket.core.simulation.SimulationOptions;
import info.openrocket.core.simulation.SimulationStepperMethod;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.GeodeticComputationStrategy;
import info.openrocket.core.util.MathUtil;
import info.openrocket.core.models.gravity.GravityModelType;
import info.openrocket.core.models.wind.WindModelType;

/**
 * High-level automation operations over {@link DocumentSession}.
 */
public final class AutomationDocumentOps {

	private static final ComponentMetadataRegistry COMPONENT_METADATA = new ComponentMetadataRegistry();
	private static final DatabaseMotorFinder MOTOR_FINDER = new DatabaseMotorFinder();

	private AutomationDocumentOps() {
	}

	public static SchemaSnapshot getSchema(DocumentSession session) {
		Objects.requireNonNull(session, "session");
		return new SchemaSnapshot(
				session.getSessionId(),
				COMPONENT_METADATA.getComponentCatalog(),
				buildSimulationSchema(),
				buildFlightConfigurationSchema());
	}

	public static DocumentSnapshot getSnapshot(DocumentSession session) {
		Objects.requireNonNull(session, "session");
		OpenRocketDocument document = session.getDocument();
		Rocket rocket = document.getRocket();
		session.pruneSimulationIds();

		List<ComponentRef> components = new ArrayList<>();
		collectComponents(session, rocket, null, 0, components);

		Map<String, Object> documentProperties = new LinkedHashMap<>();
		documentProperties.putAll(DocumentConfigAutomationSupport.readProperties(rocket));
		documentProperties.put("selectedFlightConfigurationId",
				rocket.getSelectedConfiguration().getFlightConfigurationID().toString());
		documentProperties.put("stageCount", rocket.getStageCount());
		File file = document.getFile();
		if (file != null) {
			documentProperties.put("fileName", file.getName());
		}

		return new DocumentSnapshot(
				session.getSessionId(),
				session.getTarget().id(),
				session.isLive(),
				session.getFilePath() == null ? null : session.getFilePath().toString(),
				document.isSaved(),
				rocket.getID().toString(),
				documentProperties,
				serializeCurrentAerodynamics(rocket.getSelectedConfiguration()),
				components,
				serializeFlightConfigurations(rocket),
				serializeSimulations(session));
	}

	public static MutationResult mutate(DocumentSession session, List<MutationRequest> requests, String undoDescription) {
		Objects.requireNonNull(session, "session");
		List<MutationRequest> batch = requests == null ? List.of() : List.copyOf(requests);
		List<String> warnings = new ArrayList<>();
		List<Map<String, Object>> operations = new ArrayList<>();

		if (batch.isEmpty()) {
			return new MutationResult(session.getSessionId(), true, warnings, operations, getSnapshot(session));
		}

		OpenRocketDocument document = session.getDocument();
		String label = (undoDescription == null || undoDescription.isBlank())
				? "AI automation mutation"
				: undoDescription;

		boolean success = false;
		document.startUndo(label);
		try {
			for (MutationRequest request : batch) {
				operations.add(applyMutation(session, request, warnings));
			}
			success = true;
		} catch (RuntimeException ex) {
			warnings.add(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
		} finally {
			document.stopUndo();
		}

		if (!success) {
			try {
				document.undo();
			} catch (RuntimeException undoError) {
				warnings.add("Failed to rollback mutation batch: " + undoError.getMessage());
			}
		}

		session.pruneSimulationIds();
		return new MutationResult(session.getSessionId(), success, warnings, operations, getSnapshot(session));
	}

	public static List<Map<String, Object>> manageSimulations(DocumentSession session, String action, Map<String, Object> arguments) {
		String normalized = normalize(action);
		Map<String, Object> payload = arguments == null ? Map.of() : arguments;
		OpenRocketDocument document = session.getDocument();

		if ("list".equals(normalized)) {
			return serializeSimulations(session);
		}

		document.startUndo("AI simulation mutation");
		boolean success = false;
		RuntimeException failure = null;
		try {
			switch (normalized) {
				case "create" -> createSimulation(session, payload);
				case "copy" -> copySimulation(session, payload);
				case "delete", "remove" -> deleteSimulation(session, payload);
				case "update" -> updateSimulation(session, payload);
				default -> throw new IllegalArgumentException("Unsupported simulation action: " + action);
			}
			success = true;
		} catch (RuntimeException ex) {
			failure = ex;
		} finally {
			document.stopUndo();
		}
		if (!success) {
			document.undo();
			if (failure != null) {
				throw failure;
			}
		}
		session.pruneSimulationIds();
		return serializeSimulations(session);
	}

	public static SimulationResultView getResults(DocumentSession session, String simulationId) {
		Simulation simulation = requireSimulation(session, simulationId);
		FlightData data = simulation.getSimulatedData();

		Map<String, Object> summary = new LinkedHashMap<>();
		List<String> warnings = new ArrayList<>();
		List<Map<String, Object>> branches = new ArrayList<>();
		summary.put("currentAerodynamics", serializeCurrentAerodynamics(simulation.getActiveConfiguration()));

		if (data != null) {
			summary.put("maxAltitude", data.getMaxAltitude());
			summary.put("maxVelocity", data.getMaxVelocity());
			summary.put("maxAcceleration", data.getMaxAcceleration());
			summary.put("maxMachNumber", data.getMaxMachNumber());
			summary.put("timeToApogee", data.getTimeToApogee());
			summary.put("flightTime", data.getFlightTime());
			summary.put("groundHitVelocity", data.getGroundHitVelocity());
			summary.put("launchRodVelocity", data.getLaunchRodVelocity());
			summary.put("deploymentVelocity", data.getDeploymentVelocity());
			summary.put("optimumDelay", data.getOptimumDelay());
			summary.put("defaultMachSample", serializeMachSample(data, Application.getPreferences().getDefaultMach()));

			for (Warning warning : data.getWarningSet()) {
				warnings.add(warning.toString());
			}
			for (FlightDataBranch branch : data.getBranches()) {
				branches.add(serializeBranch(branch));
			}
		}

		return new SimulationResultView(
				simulationId,
				simulation.getName(),
				normalize(simulation.getStatus().name()),
				summary,
				warnings,
				branches);
	}

	static Map<String, Object> serializeSimulationSummary(DocumentSession session, Simulation simulation, int index) {
		Rocket rocket = session.getDocument().getRocket();
		FlightConfigurationId fcid = simulation.getFlightConfigurationId();
		FlightConfiguration configuration = rocket.getFlightConfiguration(fcid);

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("id", session.resolveSimulationId(simulation));
		payload.put("index", index);
		payload.put("name", simulation.getName());
		payload.put("status", normalize(simulation.getStatus().name()));
		payload.put("statusDescription", simulation.getStatusDescription());
		payload.put("flightConfigurationId", fcid == null ? null : fcid.toString());
		if (configuration != null) {
			payload.put("flightConfigurationName", configuration.getNameRaw());
		}
		payload.put("options", serializeSimulationOptions(simulation.getOptions()));
		payload.put("hasResults", simulation.getSimulatedData() != null);
		payload.put("hasErrors", simulation.hasErrors());
		return payload;
	}

	private static Map<String, Object> applyMutation(DocumentSession session, MutationRequest request, List<String> warnings) {
		String action = normalize(request.action());
		Map<String, Object> args = request.arguments();
		return switch (action) {
			case "add_component" -> addComponent(session, args, warnings);
			case "remove_component" -> removeComponent(session, args);
			case "move_component" -> moveComponent(session, args);
			case "copy_component" -> copyComponent(session, args);
			case "set_properties" -> setProperties(session, args, warnings);
			case "create_flight_configuration" -> createFlightConfiguration(session, args);
			case "copy_flight_configuration" -> copyFlightConfiguration(session, args);
			case "remove_flight_configuration" -> removeFlightConfiguration(session, args);
			case "set_selected_flight_configuration" -> setSelectedFlightConfiguration(session, args);
			case "update_flight_configuration" -> updateFlightConfiguration(session, args);
			case "set_motor" -> setMotor(session, args, warnings);
			case "clear_motor" -> clearMotor(session, args);
			case "create_simulation" -> createSimulation(session, args);
			case "copy_simulation" -> copySimulation(session, args);
			case "remove_simulation" -> deleteSimulation(session, args);
			case "update_simulation" -> updateSimulation(session, args);
			default -> throw new IllegalArgumentException("Unsupported mutation action: " + request.action());
		};
	}

	private static Map<String, Object> addComponent(DocumentSession session, Map<String, Object> args, List<String> warnings) {
		RocketComponent parent = requireComponent(session, requireString(args, "parentId"));
		String componentType = requireString(args, "componentType");
		RocketComponent component = DocumentConfigAutomationSupport.instantiateComponent(componentType);
		int index = optionalIndex(args.get("index"), parent.getChildCount());
		parent.addChild(component, index);
		applyPropertyMap(component, map(args.get("properties")), warnings);

		Map<String, Object> operation = baseOperation("add_component");
		operation.put("componentId", component.getID().toString());
		operation.put("parentId", parent.getID().toString());
		operation.put("componentType", componentType);
		return operation;
	}

	private static Map<String, Object> removeComponent(DocumentSession session, Map<String, Object> args) {
		RocketComponent component = requireComponent(session, requireString(args, "componentId"));
		RocketComponent parent = component.getParent();
		if (parent == null) {
			throw new IllegalArgumentException("Cannot remove the root rocket component");
		}
		parent.removeChild(component);
		return Map.of(
				"action", "remove_component",
				"componentId", component.getID().toString(),
				"parentId", parent.getID().toString());
	}

	private static Map<String, Object> moveComponent(DocumentSession session, Map<String, Object> args) {
		RocketComponent component = requireComponent(session, requireString(args, "componentId"));
		RocketComponent newParent = requireComponent(session, requireString(args, "parentId"));
		RocketComponent oldParent = component.getParent();
		if (oldParent == null) {
			throw new IllegalArgumentException("Cannot move the root rocket component");
		}

		int index = optionalIndex(args.get("index"), newParent.getChildCount());
		if (oldParent == newParent) {
			int oldIndex = oldParent.getChildren().indexOf(component);
			if (oldIndex >= 0 && oldIndex < index) {
				index--;
			}
		}
		oldParent.removeChild(component);
		newParent.addChild(component, index);

		return Map.of(
				"action", "move_component",
				"componentId", component.getID().toString(),
				"fromParentId", oldParent.getID().toString(),
				"toParentId", newParent.getID().toString(),
				"index", index);
	}

	private static Map<String, Object> copyComponent(DocumentSession session, Map<String, Object> args) {
		RocketComponent source = requireComponent(session, requireString(args, "componentId"));
		RocketComponent parent = args.containsKey("parentId")
				? requireComponent(session, requireString(args, "parentId"))
				: source.getParent();
		if (parent == null) {
			throw new IllegalArgumentException("Cannot copy the root rocket component without a parentId");
		}
		RocketComponent copy = source.copy();
		int index = optionalIndex(args.get("index"), parent.getChildCount());
		parent.addChild(copy, index);
		applyPropertyMap(copy, map(args.get("properties")), new ArrayList<>());

		return Map.of(
				"action", "copy_component",
				"sourceComponentId", source.getID().toString(),
				"componentId", copy.getID().toString(),
				"parentId", parent.getID().toString());
	}

	private static Map<String, Object> setProperties(DocumentSession session, Map<String, Object> args, List<String> warnings) {
		RocketComponent component = requireComponent(session, requireString(args, "componentId"));
		applyPropertyMap(component, map(args.get("properties")), warnings);
		return Map.of(
				"action", "set_properties",
				"componentId", component.getID().toString(),
				"propertyCount", map(args.get("properties")).size());
	}

	private static Map<String, Object> createFlightConfiguration(DocumentSession session, Map<String, Object> args) {
		Rocket rocket = session.getDocument().getRocket();
		FlightConfigurationId id = args.containsKey("flightConfigurationId")
				? new FlightConfigurationId(requireString(args, "flightConfigurationId"))
				: null;
		FlightConfiguration configuration = rocket.createFlightConfiguration(id);
		if (args.containsKey("name")) {
			configuration.setName(requireString(args, "name"));
		}
		if (truthy(args.get("selected"))) {
			rocket.setSelectedConfiguration(configuration.getFlightConfigurationID());
		}
		return Map.of(
				"action", "create_flight_configuration",
				"flightConfigurationId", configuration.getFlightConfigurationID().toString(),
				"name", configuration.getNameRaw());
	}

	private static Map<String, Object> copyFlightConfiguration(DocumentSession session, Map<String, Object> args) {
		Rocket rocket = session.getDocument().getRocket();
		FlightConfiguration source = requireFlightConfiguration(rocket, requireString(args, "sourceFlightConfigurationId"));
		FlightConfigurationId newId = args.containsKey("flightConfigurationId")
				? new FlightConfigurationId(requireString(args, "flightConfigurationId"))
				: new FlightConfigurationId();
		FlightConfiguration copy = source.copy(newId);
		if (args.containsKey("name")) {
			copy.setName(requireString(args, "name"));
		}
		rocket.setFlightConfiguration(newId, copy);
		if (truthy(args.get("selected"))) {
			rocket.setSelectedConfiguration(newId);
		}
		return Map.of(
				"action", "copy_flight_configuration",
				"sourceFlightConfigurationId", source.getFlightConfigurationID().toString(),
				"flightConfigurationId", newId.toString());
	}

	private static Map<String, Object> removeFlightConfiguration(DocumentSession session, Map<String, Object> args) {
		Rocket rocket = session.getDocument().getRocket();
		FlightConfiguration configuration = requireFlightConfiguration(rocket, requireString(args, "flightConfigurationId"));
		rocket.removeFlightConfiguration(configuration.getFlightConfigurationID());
		return Map.of(
				"action", "remove_flight_configuration",
				"flightConfigurationId", configuration.getFlightConfigurationID().toString());
	}

	private static Map<String, Object> setSelectedFlightConfiguration(DocumentSession session, Map<String, Object> args) {
		Rocket rocket = session.getDocument().getRocket();
		FlightConfiguration configuration = requireFlightConfiguration(rocket, requireString(args, "flightConfigurationId"));
		rocket.setSelectedConfiguration(configuration.getFlightConfigurationID());
		return Map.of(
				"action", "set_selected_flight_configuration",
				"flightConfigurationId", configuration.getFlightConfigurationID().toString());
	}

	private static Map<String, Object> updateFlightConfiguration(DocumentSession session, Map<String, Object> args) {
		Rocket rocket = session.getDocument().getRocket();
		FlightConfiguration configuration = requireFlightConfiguration(rocket, requireString(args, "flightConfigurationId"));

		if (args.containsKey("name")) {
			configuration.setName(requireString(args, "name"));
		}
		if (args.containsKey("selected") && truthy(args.get("selected"))) {
			rocket.setSelectedConfiguration(configuration.getFlightConfigurationID());
		}
		if (args.containsKey("activeStages")) {
			applyStageActivation(rocket, configuration, args.get("activeStages"));
		}
		return Map.of(
				"action", "update_flight_configuration",
				"flightConfigurationId", configuration.getFlightConfigurationID().toString(),
				"name", configuration.getNameRaw());
	}

	private static Map<String, Object> setMotor(DocumentSession session, Map<String, Object> args, List<String> warnings) {
		RocketComponent component = requireComponent(session, requireString(args, "componentId"));
		if (!(component instanceof MotorMount mount)) {
			throw new IllegalArgumentException("Component is not a motor mount: " + component.getID());
		}
		Rocket rocket = session.getDocument().getRocket();
		FlightConfigurationId fcid = resolveFlightConfigurationId(rocket, args.get("flightConfigurationId"));
		WarningSet warningSet = new WarningSet();

		mount.setMotorMount(args.containsKey("motorMountEnabled") ? truthy(args.get("motorMountEnabled")) : true);
		if (args.containsKey("overhang")) {
			mount.setMotorOverhang(toDouble(args.get("overhang")));
		}

		MotorConfiguration configuration = mount.getMotorConfig(fcid);
		Motor motor = MOTOR_FINDER.findMotor(
				parseEnum(Motor.Type.class, args.get("type"), Motor.Type.UNKNOWN),
				args.containsKey("manufacturer") ? requireString(args, "manufacturer") : null,
				requireString(args, "designation"),
				args.containsKey("diameter") ? toDouble(args.get("diameter")) : Double.NaN,
				args.containsKey("length") ? toDouble(args.get("length")) : Double.NaN,
				args.containsKey("digest") ? requireString(args, "digest") : null,
				warningSet);
		configuration.setMotor(motor);
		if (args.containsKey("ejectionDelay")) {
			configuration.setEjectionDelay(toDouble(args.get("ejectionDelay")));
		}
		if (args.containsKey("useDefaultIgnition") && truthy(args.get("useDefaultIgnition"))) {
			configuration.useDefaultIgnition();
		} else {
			if (args.containsKey("ignitionDelay")) {
				configuration.setIgnitionDelay(toDouble(args.get("ignitionDelay")));
			}
			if (args.containsKey("ignitionEvent")) {
				configuration.setIgnitionEvent(parseEnum(IgnitionEvent.class, args.get("ignitionEvent"), IgnitionEvent.AUTOMATIC));
			}
		}
		mount.setMotorConfig(configuration, fcid);
		appendWarnings(warnings, warningSet);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("action", "set_motor");
		result.put("componentId", component.getID().toString());
		result.put("flightConfigurationId", fcid.toString());
		if (motor != null) {
			result.put("designation", motor.getDesignation());
		}
		return result;
	}

	private static Map<String, Object> clearMotor(DocumentSession session, Map<String, Object> args) {
		RocketComponent component = requireComponent(session, requireString(args, "componentId"));
		if (!(component instanceof MotorMount mount)) {
			throw new IllegalArgumentException("Component is not a motor mount: " + component.getID());
		}
		Rocket rocket = session.getDocument().getRocket();
		FlightConfigurationId fcid = resolveFlightConfigurationId(rocket, args.get("flightConfigurationId"));
		((FlightConfigurableComponent) mount).reset(fcid);
		return Map.of(
				"action", "clear_motor",
				"componentId", component.getID().toString(),
				"flightConfigurationId", fcid.toString());
	}

	private static Map<String, Object> createSimulation(DocumentSession session, Map<String, Object> args) {
		OpenRocketDocument document = session.getDocument();
		Rocket rocket = document.getRocket();
		Simulation simulation = new Simulation(document, rocket);
		if (args.containsKey("name")) {
			simulation.setName(requireString(args, "name"));
		}
		if (args.containsKey("flightConfigurationId")) {
			simulation.setFlightConfigurationId(resolveFlightConfigurationId(rocket, args.get("flightConfigurationId")));
		}
		if (args.containsKey("options")) {
			applySimulationOptions(simulation.getOptions(), map(args.get("options")));
		}
		document.addSimulation(simulation);
		return Map.of(
				"action", "create_simulation",
				"simulationId", session.resolveSimulationId(simulation),
				"name", simulation.getName());
	}

	private static Map<String, Object> copySimulation(DocumentSession session, Map<String, Object> args) {
		Simulation source = requireSimulation(session, requireString(args, "simulationId"));
		Simulation copy = source.duplicateSimulation(session.getDocument().getRocket());
		if (args.containsKey("name")) {
			copy.setName(requireString(args, "name"));
		}
		session.getDocument().addSimulation(copy);
		return Map.of(
				"action", "copy_simulation",
				"sourceSimulationId", session.resolveSimulationId(source),
				"simulationId", session.resolveSimulationId(copy));
	}

	private static Map<String, Object> deleteSimulation(DocumentSession session, Map<String, Object> args) {
		Simulation simulation = requireSimulation(session, requireString(args, "simulationId"));
		String resolvedId = session.resolveSimulationId(simulation);
		session.getDocument().removeSimulation(simulation);
		return Map.of(
				"action", "remove_simulation",
				"simulationId", resolvedId);
	}

	private static Map<String, Object> updateSimulation(DocumentSession session, Map<String, Object> args) {
		Simulation simulation = requireSimulation(session, requireString(args, "simulationId"));
		Rocket rocket = session.getDocument().getRocket();
		if (args.containsKey("name")) {
			simulation.setName(requireString(args, "name"));
		}
		if (args.containsKey("flightConfigurationId")) {
			simulation.setFlightConfigurationId(resolveFlightConfigurationId(rocket, args.get("flightConfigurationId")));
		}
		if (args.containsKey("options")) {
			applySimulationOptions(simulation.getOptions(), map(args.get("options")));
		}
		return Map.of(
				"action", "update_simulation",
				"simulationId", session.resolveSimulationId(simulation),
				"name", simulation.getName());
	}

	private static void collectComponents(DocumentSession session, RocketComponent component, String parentId, int index,
			List<ComponentRef> target) {
		Map<String, Object> properties = new LinkedHashMap<>(DocumentConfigAutomationSupport.readProperties(component));
		properties.put("compatibleChildren", COMPONENT_METADATA.getCompatibleChildren(component));

		List<String> children = new ArrayList<>();
		for (RocketComponent child : component.getChildren()) {
			children.add(child.getID().toString());
		}

		target.add(new ComponentRef(
				component.getID().toString(),
				DocumentConfigAutomationSupport.getPrimaryAlias(component.getClass()),
				component.getClass().getName(),
				component.getName(),
				parentId,
				index,
				children,
				properties));

		List<RocketComponent> childComponents = component.getChildren();
		for (int childIndex = 0; childIndex < childComponents.size(); childIndex++) {
			collectComponents(session, childComponents.get(childIndex), component.getID().toString(), childIndex, target);
		}
	}

	private static List<Map<String, Object>> serializeFlightConfigurations(Rocket rocket) {
		List<Map<String, Object>> configurations = new ArrayList<>();
		for (FlightConfigurationId id : rocket.getIds()) {
			FlightConfiguration configuration = rocket.getFlightConfiguration(id);
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("id", id.toString());
			payload.put("name", configuration.getNameRaw());
			payload.put("selected", rocket.getSelectedConfiguration().getFlightConfigurationID().equals(id));
			payload.put("currentAerodynamics", serializeCurrentAerodynamics(configuration));
			payload.put("stages", serializeStages(rocket, configuration));
			payload.put("motors", serializeMotors(rocket, id));
			configurations.add(payload);
		}
		return configurations;
	}

	private static List<Map<String, Object>> serializeStages(Rocket rocket, FlightConfiguration configuration) {
		List<Map<String, Object>> stages = new ArrayList<>();
		for (AxialStage stage : configuration.getAllStages()) {
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("stageNumber", stage.getStageNumber());
			payload.put("stageId", stage.getID().toString());
			payload.put("name", stage.getName());
			payload.put("active", configuration.isStageActive(stage.getStageNumber()));
			stages.add(payload);
		}
		return stages;
	}

	private static List<Map<String, Object>> serializeMotors(Rocket rocket, FlightConfigurationId fcid) {
		List<Map<String, Object>> motors = new ArrayList<>();
		for (RocketComponent component : rocket) {
			if (!(component instanceof MotorMount mount) || !mount.isMotorMount()) {
				continue;
			}
			MotorConfiguration configuration = mount.getMotorConfig(fcid);
			Motor motor = configuration.getMotor();
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("componentId", component.getID().toString());
			payload.put("componentName", component.getName());
			payload.put("motorMountEnabled", mount.isMotorMount());
			payload.put("overhang", mount.getMotorOverhang());
			payload.put("empty", configuration.isEmpty());
			payload.put("ignitionDelay", configuration.getIgnitionDelay());
			payload.put("ignitionEvent", normalize(configuration.getIgnitionEvent().name()));
			payload.put("hasIgnitionOverride", configuration.hasIgnitionOverride());
			payload.put("ejectionDelay", configuration.getEjectionDelay());
			if (motor != null) {
				payload.put("type", normalize(motor.getMotorType().name()));
				payload.put("designation", motor.getDesignation());
				payload.put("digest", motor.getDigest());
				payload.put("diameter", motor.getDiameter());
				payload.put("length", motor.getLength());
				if (motor instanceof ThrustCurveMotor thrustCurveMotor) {
					payload.put("manufacturer", thrustCurveMotor.getManufacturer().getDisplayName());
				}
			}
			motors.add(payload);
		}
		return motors;
	}

	private static List<Map<String, Object>> serializeSimulations(DocumentSession session) {
		List<Map<String, Object>> simulations = new ArrayList<>();
		List<Simulation> items = session.getDocument().getSimulations();
		for (int i = 0; i < items.size(); i++) {
			simulations.add(serializeSimulationSummary(session, items.get(i), i));
		}
		return simulations;
	}

	private static Map<String, Object> serializeSimulationOptions(SimulationOptions options) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("launchRodLength", options.getLaunchRodLength());
		payload.put("launchIntoWind", options.getLaunchIntoWind());
		payload.put("launchRodAngle", options.getLaunchRodAngle());
		payload.put("launchRodDirection", options.getLaunchRodDirection());
		payload.put("windModelType", normalize(options.getWindModelType().name()));
		payload.put("gravityModelType", normalize(options.getGravityModelType().name()));
		payload.put("constantGravity", options.getConstantGravity());
		payload.put("windSpeedAverage", options.getWindSpeedAverage());
		payload.put("windSpeedDeviation", options.getWindSpeedDeviation());
		payload.put("windTurbulenceIntensity", options.getWindTurbulenceIntensity());
		payload.put("windDirection", options.getWindDirection());
		payload.put("launchAltitude", options.getLaunchAltitude());
		payload.put("launchLatitude", options.getLaunchLatitude());
		payload.put("launchLongitude", options.getLaunchLongitude());
		payload.put("geodeticComputation", normalize(options.getGeodeticComputation().name()));
		payload.put("simulationStepperMethodChoice", normalize(options.getSimulationStepperMethodChoice().name()));
		payload.put("isaAtmosphere", options.isISAAtmosphere());
		payload.put("launchTemperature", options.getLaunchTemperature());
		payload.put("launchPressure", options.getLaunchPressure());
		payload.put("launchRelativeHumidity", options.getLaunchRelativeHumidity());
		payload.put("timeStep", options.getTimeStep());
		payload.put("maxSimulationTime", options.getMaxSimulationTime());
		payload.put("maximumStepAngle", options.getMaximumStepAngle());
		payload.put("randomSeed", options.getRandomSeed());
		payload.put("dragLookupCsvPath", options.getDragLookupCsvPath() == null ? null : options.getDragLookupCsvPath().toString());
		payload.put("stabilityLookupCsvPath", options.getStabilityLookupCsvPath() == null ? null : options.getStabilityLookupCsvPath().toString());
		return payload;
	}

	private static void applySimulationOptions(SimulationOptions options, Map<String, Object> patch) {
		for (Map.Entry<String, Object> entry : patch.entrySet()) {
			String key = normalize(entry.getKey());
			Object value = entry.getValue();
			switch (key) {
				case "launchrodlength" -> options.setLaunchRodLength(toDouble(value));
				case "launchintowind" -> options.setLaunchIntoWind(truthy(value));
				case "launchrodangle" -> options.setLaunchRodAngle(toDouble(value));
				case "launchroddirection" -> options.setLaunchRodDirection(toDouble(value));
				case "windmodeltype" -> options.setWindModelType(parseEnum(WindModelType.class, value, options.getWindModelType()));
				case "gravitymodeltype" -> options.setGravityModelType(parseEnum(GravityModelType.class, value, options.getGravityModelType()));
				case "constantgravity" -> options.setConstantGravity(toDouble(value));
				case "windspeedaverage" -> options.setWindSpeedAverage(toDouble(value));
				case "windspeeddeviation" -> options.setWindSpeedDeviation(toDouble(value));
				case "windturbulenceintensity" -> options.setWindTurbulenceIntensity(toDouble(value));
				case "winddirection" -> options.setWindDirection(toDouble(value));
				case "launchaltitude" -> options.setLaunchAltitude(toDouble(value));
				case "launchlatitude" -> options.setLaunchLatitude(toDouble(value));
				case "launchlongitude" -> options.setLaunchLongitude(toDouble(value));
				case "geodeticcomputation" -> options.setGeodeticComputation(parseEnum(
						GeodeticComputationStrategy.class, value, options.getGeodeticComputation()));
				case "simulationsteppermethodchoice" -> options.setSimulationStepperMethodChoice(parseEnum(
						SimulationStepperMethod.class, value, options.getSimulationStepperMethodChoice()));
				case "isaatmosphere" -> options.setISAAtmosphere(truthy(value));
				case "launchtemperature" -> options.setLaunchTemperature(toDouble(value));
				case "launchpressure" -> options.setLaunchPressure(toDouble(value));
				case "launchrelativehumidity" -> options.setLaunchRelativeHumidity(toDouble(value));
				case "timestep" -> options.setTimeStep(toDouble(value));
				case "maxsimulationtime" -> options.setMaxSimulationTime(toDouble(value));
				case "maximumstepangle" -> options.setMaximumStepAngle(toDouble(value));
				case "randomseed" -> options.setRandomSeed((int) Math.round(toDouble(value)));
				case "draglookupcsvpath" -> {
					if (value == null || String.valueOf(value).isBlank()) {
						options.clearDragLookup();
					} else {
						options.setDragLookupCsvPath(Path.of(String.valueOf(value)));
					}
				}
				case "stabilitylookupcsvpath" -> {
					if (value == null || String.valueOf(value).isBlank()) {
						options.clearStabilityLookup();
					} else {
						options.setStabilityLookupCsvPath(Path.of(String.valueOf(value)));
					}
				}
				default -> throw new IllegalArgumentException("Unsupported simulation option: " + entry.getKey());
			}
		}
	}

	private static Map<String, Object> serializeCurrentAerodynamics(FlightConfiguration configuration) {
		if (configuration == null) {
			return Map.of();
		}

		AerodynamicCalculator aerodynamicCalculator = new BarrowmanCalculator();
		FlightConditions conditions = new FlightConditions(configuration);
		WarningSet warnings = new WarningSet();
		double mach = Application.getPreferences().getDefaultMach();
		conditions.setMach(mach);
		conditions.setAOA(0);
		conditions.setRollRate(0);

		var cp = aerodynamicCalculator.getWorstCP(configuration, conditions, warnings);
		var cg = MassCalculator.calculateLaunch(configuration).getCM();

		double cpLocation = cp.getWeight() > MathUtil.EPSILON ? cp.getX() : Double.NaN;
		double cgLocation = cg.getWeight() > MassCalculator.MIN_MASS ? cg.getX() : Double.NaN;
		double diameter = calculateReferenceDiameter(configuration);
		double stabilityAbsolute = (Double.isFinite(cpLocation) && Double.isFinite(cgLocation))
				? cpLocation - cgLocation
				: Double.NaN;
		double stabilityCalibers = (Double.isFinite(stabilityAbsolute) && diameter > MathUtil.EPSILON)
				? stabilityAbsolute / diameter
				: Double.NaN;

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("method", "worst_cp_at_default_mach");
		payload.put("flightConfigurationId", configuration.getFlightConfigurationID().toString());
		payload.put("flightConfigurationName", configuration.getNameRaw() == null ? "" : configuration.getNameRaw());
		payload.put("mach", mach);
		payload.put("angleOfAttack", 0.0);
		payload.put("rollRate", 0.0);
		payload.put("cpLocation", cpLocation);
		payload.put("cgLocation", cgLocation);
		payload.put("diameter", diameter);
		payload.put("stabilityAbsolute", stabilityAbsolute);
		payload.put("stabilityCalibers", stabilityCalibers);
		payload.put("warnings", serializeWarnings(warnings));
		return payload;
	}

	private static double calculateReferenceDiameter(FlightConfiguration configuration) {
		double diameter = Double.NaN;
		for (RocketComponent component : configuration.getCoreComponents()) {
			if (component instanceof SymmetricComponent symmetricComponent) {
				double foreDiameter = symmetricComponent.getForeRadius() * 2;
				double aftDiameter = symmetricComponent.getAftRadius() * 2;
				diameter = MathUtil.max(diameter, foreDiameter, aftDiameter);
			}
		}
		return diameter;
	}

	private static List<String> serializeWarnings(WarningSet warnings) {
		List<String> values = new ArrayList<>();
		for (Warning warning : warnings) {
			values.add(warning.toString());
		}
		return values;
	}

	private static Map<String, Object> serializeMachSample(FlightData data, double targetMach) {
		FlightDataBranch bestBranch = null;
		int bestIndex = -1;
		double bestDistance = Double.POSITIVE_INFINITY;
		double bestMach = Double.NaN;

		for (FlightDataBranch branch : data.getBranches()) {
			if (branch.getLength() == 0 || branch.get(FlightDataType.TYPE_MACH_NUMBER) == null) {
				continue;
			}
			for (int index = 0; index < branch.getLength(); index++) {
				Double rawMach = branch.getByIndex(FlightDataType.TYPE_MACH_NUMBER, index);
				if (rawMach == null || Double.isNaN(rawMach)) {
					continue;
				}
				double distance = Math.abs(rawMach - targetMach);
				if (distance < bestDistance) {
					bestDistance = distance;
					bestMach = rawMach;
					bestBranch = branch;
					bestIndex = index;
				}
			}
		}

		if (bestBranch == null || bestIndex < 0) {
			return Map.of();
		}

		double cpLocation = numericOrNaN(bestBranch.getByIndex(FlightDataType.TYPE_CP_LOCATION, bestIndex));
		double cgLocation = numericOrNaN(bestBranch.getByIndex(FlightDataType.TYPE_CG_LOCATION, bestIndex));
		double stabilityCalibers = numericOrNaN(bestBranch.getByIndex(FlightDataType.TYPE_STABILITY, bestIndex));
		double referenceLength = numericOrNaN(bestBranch.getByIndex(FlightDataType.TYPE_REFERENCE_LENGTH, bestIndex));
		double time = numericOrNaN(bestBranch.getByIndex(FlightDataType.TYPE_TIME, bestIndex));
		double altitude = numericOrNaN(bestBranch.getByIndex(FlightDataType.TYPE_ALTITUDE, bestIndex));

		double stabilityAbsolute = (Double.isFinite(cpLocation) && Double.isFinite(cgLocation))
				? cpLocation - cgLocation
				: Double.NaN;
		if (!Double.isFinite(stabilityAbsolute) &&
				Double.isFinite(stabilityCalibers) && Double.isFinite(referenceLength)) {
			stabilityAbsolute = stabilityCalibers * referenceLength;
		}
		if (!Double.isFinite(stabilityCalibers) &&
				Double.isFinite(stabilityAbsolute) && Double.isFinite(referenceLength) &&
				referenceLength > MathUtil.EPSILON) {
			stabilityCalibers = stabilityAbsolute / referenceLength;
		}

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("method", "nearest_branch_sample");
		payload.put("targetMach", targetMach);
		payload.put("actualMach", bestMach);
		payload.put("branchName", bestBranch.getName());
		payload.put("index", bestIndex);
		payload.put("time", time);
		payload.put("altitude", altitude);
		payload.put("cpLocation", cpLocation);
		payload.put("cgLocation", cgLocation);
		payload.put("referenceLength", referenceLength);
		payload.put("stabilityAbsolute", stabilityAbsolute);
		payload.put("stabilityCalibers", stabilityCalibers);
		return payload;
	}

	private static double numericOrNaN(Number value) {
		return value == null ? Double.NaN : value.doubleValue();
	}

	private static Map<String, Object> serializeBranch(FlightDataBranch branch) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("name", branch.getName());
		payload.put("length", branch.getLength());
		payload.put("separationTime", branch.getSeparationTime());
		payload.put("events", serializeEvents(branch.getEvents()));

		List<Map<String, Object>> series = new ArrayList<>();
		for (FlightDataType type : branch.getTypes()) {
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("name", type.getName());
			values.put("symbol", type.getSymbol());
			values.put("unitGroup", type.getUnitGroup().toString());
			values.put("values", branch.get(type));
			series.add(values);
		}
		payload.put("series", series);
		return payload;
	}

	private static List<Map<String, Object>> serializeEvents(List<FlightEvent> events) {
		List<Map<String, Object>> payload = new ArrayList<>();
		for (FlightEvent event : events) {
			Map<String, Object> values = new LinkedHashMap<>();
			values.put("id", event.getID().toString());
			values.put("type", normalize(event.getType().name()));
			values.put("time", event.getTime());
			if (event.getSource() != null) {
				values.put("sourceId", event.getSource().getID().toString());
				values.put("sourceName", event.getSource().getName());
			}
			if (event.getData() != null) {
				values.put("data", String.valueOf(event.getData()));
			}
			payload.add(values);
		}
		return payload;
	}

	private static void applyPropertyMap(RocketComponent component, Map<String, Object> propertyPatch, List<String> warnings) {
		WarningSet warningSet = new WarningSet();
		for (Map.Entry<String, Object> entry : propertyPatch.entrySet()) {
			DocumentConfigAutomationSupport.applyProperty(component, entry.getKey(), entry.getValue(), warningSet);
		}
		appendWarnings(warnings, warningSet);
	}

	private static void appendWarnings(List<String> warnings, WarningSet warningSet) {
		for (Warning warning : warningSet) {
			warnings.add(warning.toString());
		}
	}

	private static RocketComponent requireComponent(DocumentSession session, String componentId) {
		try {
			UUID id = UUID.fromString(componentId);
			RocketComponent component = session.getDocument().getRocket().findComponent(id);
			if (component == null || component == RocketComponent.REMOVED) {
				throw new IllegalArgumentException("Component not found: " + componentId);
			}
			return component;
		} catch (IllegalArgumentException ex) {
			if (ex.getMessage() != null && ex.getMessage().startsWith("Component not found")) {
				throw ex;
			}
			throw new IllegalArgumentException("Invalid component id: " + componentId, ex);
		}
	}

	private static FlightConfiguration requireFlightConfiguration(Rocket rocket, String fcid) {
		FlightConfigurationId id = new FlightConfigurationId(fcid);
		if (!rocket.containsFlightConfigurationID(id)) {
			throw new IllegalArgumentException("Flight configuration not found: " + fcid);
		}
		return rocket.getFlightConfiguration(id);
	}

	private static FlightConfigurationId resolveFlightConfigurationId(Rocket rocket, Object fcid) {
		if (fcid == null) {
			return rocket.getSelectedConfiguration().getFlightConfigurationID();
		}
		return requireFlightConfiguration(rocket, String.valueOf(fcid)).getFlightConfigurationID();
	}

	static Simulation requireSimulation(DocumentSession session, String simulationId) {
		Simulation simulation = session.getSimulationById(simulationId);
		if (simulation == null) {
			throw new IllegalArgumentException("Simulation not found: " + simulationId);
		}
		return simulation;
	}

	private static void applyStageActivation(Rocket rocket, FlightConfiguration configuration, Object activeStagesPayload) {
		if (activeStagesPayload instanceof List<?> list) {
			configuration.clearAllStages();
			for (Object value : list) {
				AxialStage stage = resolveStage(rocket, value);
				configuration._setStageActive(stage.getStageNumber(), true, false);
			}
			return;
		}
		if (activeStagesPayload instanceof Map<?, ?> map) {
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				AxialStage stage = resolveStage(rocket, entry.getKey());
				configuration._setStageActive(stage.getStageNumber(), truthy(entry.getValue()), false);
			}
			return;
		}
		throw new IllegalArgumentException("activeStages must be a list or object map");
	}

	private static AxialStage resolveStage(Rocket rocket, Object stageRef) {
		if (stageRef instanceof Number number) {
			AxialStage stage = rocket.getStage(number.intValue());
			if (stage != null) {
				return stage;
			}
		}
		String text = String.valueOf(stageRef);
		try {
			AxialStage stage = rocket.getStage(UUID.fromString(text));
			if (stage != null) {
				return stage;
			}
		} catch (IllegalArgumentException ignored) {
			// Fall through to error.
		}
		throw new IllegalArgumentException("Stage not found: " + text);
	}

	private static Map<String, Object> buildSimulationSchema() {
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("actions", List.of("list", "create", "copy", "update", "delete"));
		schema.put("optionKeys", List.of(
				"launchRodLength",
				"launchIntoWind",
				"launchRodAngle",
				"launchRodDirection",
				"windModelType",
				"gravityModelType",
				"constantGravity",
				"windSpeedAverage",
				"windSpeedDeviation",
				"windTurbulenceIntensity",
				"windDirection",
				"launchAltitude",
				"launchLatitude",
				"launchLongitude",
				"geodeticComputation",
				"simulationStepperMethodChoice",
				"isaAtmosphere",
				"launchTemperature",
				"launchPressure",
				"launchRelativeHumidity",
				"timeStep",
				"maxSimulationTime",
				"maximumStepAngle",
				"randomSeed",
				"dragLookupCsvPath",
				"stabilityLookupCsvPath"));
		return schema;
	}

	private static Map<String, Object> buildFlightConfigurationSchema() {
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("actions", List.of(
				"create_flight_configuration",
				"copy_flight_configuration",
				"remove_flight_configuration",
				"set_selected_flight_configuration",
				"update_flight_configuration",
				"set_motor",
				"clear_motor"));
		schema.put("stageActivationShape", "list or map");
		return schema;
	}

	private static Map<String, Object> baseOperation(String action) {
		Map<String, Object> operation = new LinkedHashMap<>();
		operation.put("action", action);
		operation.put("timestamp", Instant.now().toString());
		return operation;
	}

	private static String requireString(Map<String, Object> args, String key) {
		Object value = args.get(key);
		if (value == null || String.valueOf(value).isBlank()) {
			throw new IllegalArgumentException("Missing required argument: " + key);
		}
		return String.valueOf(value);
	}

	private static Map<String, Object> map(Object value) {
		if (value == null) {
			return Map.of();
		}
		if (value instanceof Map<?, ?> original) {
			Map<String, Object> copy = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : original.entrySet()) {
				copy.put(String.valueOf(entry.getKey()), entry.getValue());
			}
			return copy;
		}
		throw new IllegalArgumentException("Expected object map but received " + value.getClass().getSimpleName());
	}

	private static int optionalIndex(Object value, int defaultIndex) {
		if (value == null) {
			return defaultIndex;
		}
		return Math.max(0, (int) Math.round(toDouble(value)));
	}

	private static boolean truthy(Object value) {
		if (value instanceof Boolean booleanValue) {
			return booleanValue;
		}
		return Boolean.parseBoolean(String.valueOf(value));
	}

	private static double toDouble(Object value) {
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		return Double.parseDouble(String.valueOf(value));
	}

	private static String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ENGLISH);
	}

	private static <E extends Enum<E>> E parseEnum(Class<E> enumClass, Object rawValue, E fallback) {
		if (rawValue == null) {
			return fallback;
		}
		String normalized = normalize(String.valueOf(rawValue)).replace("_", "");
		for (E constant : enumClass.getEnumConstants()) {
			if (normalize(constant.name()).replace("_", "").equals(normalized)) {
				return constant;
			}
		}
		throw new IllegalArgumentException("Unsupported enum value '" + rawValue + "' for " + enumClass.getSimpleName());
	}
}
