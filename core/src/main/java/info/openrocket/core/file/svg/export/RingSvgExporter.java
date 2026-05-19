package info.openrocket.core.file.svg.export;

import info.openrocket.core.rocketcomponent.Bulkhead;
import info.openrocket.core.rocketcomponent.CenteringRing;
import info.openrocket.core.rocketcomponent.InnerTube;
import info.openrocket.core.util.CoordinateIF;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Shapes out centering rings and bulkheads using {@link SVGBuilder}.
 */
public final class RingSvgExporter {
	private static final double MIN_CROSSHAIR_HALF_LENGTH = 0.0005; // 0.5 mm
	private static final double MAX_CROSSHAIR_HALF_LENGTH = 0.05;   // 50 mm

	private RingSvgExporter() {
	}

	public record Hole(double offsetY, double offsetZ, double radius) { }

	public static void drawCenteringRing(CenteringRing ring,
										 SVGBuilder builder,
										 SVGExportOptions options,
										 Collection<Hole> additionalHoles) {
		Objects.requireNonNull(ring, "ring");
		renderRing(builder,
				0,
				0,
				ring.getOuterRadius(),
				ring.getInnerRadius(),
				additionalHoles,
				options);
	}

	public static void drawBulkhead(Bulkhead bulkhead,
									SVGBuilder builder,
									SVGExportOptions options,
									Collection<Hole> additionalHoles) {
		Objects.requireNonNull(bulkhead, "bulkhead");
		renderRing(builder,
				0,
				0,
				bulkhead.getOuterRadius(),
				0,
				additionalHoles,
				options);
	}

	public static void renderRing(SVGBuilder builder,
								  double centerX,
								  double centerY,
								  double outerRadius,
								  double innerRadius,
								  Collection<Hole> holes,
								  SVGExportOptions options) {
		Objects.requireNonNull(builder, "builder");
		Objects.requireNonNull(options, "options");

		builder.addCircle(centerX, centerY, outerRadius, null, options.getStrokeColor(), options.getStrokeWidthMm());
		if (innerRadius > 0) {
			builder.addCircle(centerX, centerY, innerRadius, null, options.getStrokeColor(), options.getStrokeWidthMm());
		}

		if (options.isDrawCrosshair()) {
			// Use configurable crosshair size (length of one full line in mm), convert to meters and get half-length
			double crosshairSizeM = options.getCrosshairSizeMm() / 1000.0;
			double halfLength = clampCrosshairHalfLength(crosshairSizeM / 2.0, outerRadius);
			builder.addCrosshair(centerX, centerY, halfLength, halfLength, options.getCrosshairColor(), options.getStrokeWidthMm());
		}

		if (holes != null) {
			for (Hole hole : holes) {
				builder.addCircle(centerX + hole.offsetY(), centerY + hole.offsetZ(), hole.radius(),
						null, options.getStrokeColor(), options.getStrokeWidthMm());
				if (options.isDrawCrosshair()) {
					// Use configurable crosshair size, clamp to not exceed hole radius
					double crosshairSizeM = options.getCrosshairSizeMm() / 1000.0;
					double halfLength = clampCrosshairHalfLength(crosshairSizeM / 2.0, hole.radius());
					builder.addCrosshair(centerX + hole.offsetY(), centerY + hole.offsetZ(),
							halfLength, halfLength, options.getCrosshairColor(), options.getStrokeWidthMm());
				}
			}
		}
	}

	/**
	 * Clamp crosshair half-length to ensure it's within reasonable bounds and doesn't exceed the part radius.
	 * @param requestedHalfLength the requested half-length in meters
	 * @param maxRadius the maximum radius of the part (in meters) to ensure crosshair doesn't extend beyond
	 * @return the clamped half-length in meters
	 */
	private static double clampCrosshairHalfLength(double requestedHalfLength, double maxRadius) {
		// Don't let crosshair extend beyond the part radius
		double maxHalfLength = maxRadius * 0.9; // Leave 10% margin
		double clamped = Math.min(requestedHalfLength, maxHalfLength);
		// Also clamp to absolute min/max bounds
		return Math.max(MIN_CROSSHAIR_HALF_LENGTH, Math.min(MAX_CROSSHAIR_HALF_LENGTH, clamped));
	}

	public static List<Hole> holesFromMotorMounts(List<InnerTube> motorMounts) {
		if (motorMounts == null || motorMounts.isEmpty()) {
			return Collections.emptyList();
		}

		List<Hole> holes = new ArrayList<>();
		for (InnerTube tube : motorMounts) {
			if (tube == null) {
				continue;
			}
			List<CoordinateIF> clusterPoints = tube.getClusterPoints();
			if (clusterPoints == null || clusterPoints.isEmpty()) {
				holes.add(new Hole(tube.getRadialShiftY(), tube.getRadialShiftZ(), tube.getOuterRadius()));
				continue;
			}

			for (CoordinateIF coordinate : clusterPoints) {
				holes.add(new Hole(coordinate.getY(), coordinate.getZ(), tube.getOuterRadius()));
			}
		}

		return holes;
	}
}

