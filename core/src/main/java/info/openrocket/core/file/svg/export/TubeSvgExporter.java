package info.openrocket.core.file.svg.export;

import info.openrocket.core.rocketcomponent.Coaxial;
import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.util.BoundingBox;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.CoordinateIF;
import info.openrocket.core.util.MathUtil;

import java.util.Objects;

/**
 * Utilities to render tube components (BodyTube, LaunchLug) with both side and back profiles.
 * Side profile shows a rectangle representing the tube length and diameter.
 * Back profile shows concentric circles representing the end view.
 */
public final class TubeSvgExporter {
	private static final double MIN_CROSSHAIR_HALF_LENGTH = 0.0005; // 0.5 mm
	private static final double MAX_CROSSHAIR_HALF_LENGTH = 0.05;   // 50 mm
	private static final double PROFILE_SPACING = 0.01; // meters spacing between side and back profiles

	private TubeSvgExporter() {
	}

	/**
	 * Bounds class for tube profiles.
	 */
	public static final class Bounds {
		private double minX = Double.MAX_VALUE;
		private double minY = Double.MAX_VALUE;
		private double maxX = -Double.MAX_VALUE;
		private double maxY = -Double.MAX_VALUE;

		public Bounds(BoundingBox box) {
			minX = box.min.getX();
			minY = box.min.getY();
			maxX = box.max.getX();
			maxY = box.max.getY();
		}

		public Bounds() { }

		void include(double x, double y) {
			if (x < minX) minX = x;
			if (y < minY) minY = y;
			if (x > maxX) maxX = x;
			if (y > maxY) maxY = y;
		}

		public double getWidth() {
			return (maxX == -Double.MAX_VALUE) ? 0 : (maxX - minX);
		}

		public double getHeight() {
			return (maxY == -Double.MAX_VALUE) ? 0 : (maxY - minY);
		}

		public double getMaxAbsY() {
			if (maxY == -Double.MAX_VALUE || minY == Double.MAX_VALUE) return 0;
			return Math.max(Math.abs(maxY), Math.abs(minY));
		}
	}

	/**
	 * Calculate bounds for a tube component including both side and back profiles.
	 * 
	 * @param component the tube component (must implement Coaxial and have getLength())
	 * @return bounds including both profiles with spacing
	 */
	public static Bounds calculateBounds(Coaxial component, double length) {
		Objects.requireNonNull(component, "component");
		
		double outerRadius = component.getOuterRadius();
		
		// Side profile dimensions
		double sideWidth = length;
		double sideHeight = outerRadius * 2.0;
		
		// Back profile dimensions (circle with diameter = 2 * outerRadius)
		double backProfileSize = outerRadius * 2.0;
		
		// Total dimensions: side profile + spacing + back profile
		double totalWidth = sideWidth + PROFILE_SPACING + backProfileSize;
		double totalHeight = Math.max(sideHeight, backProfileSize);
		
		Bounds bounds = new Bounds();
		bounds.include(0, -totalHeight / 2.0);
		bounds.include(totalWidth, totalHeight / 2.0);
		
		return bounds;
	}

	/**
	 * Draws a tube component with both side and back profiles.
	 * Side profile is drawn as a rectangle. Back profile is drawn as concentric circles.
	 * 
	 * @param component the tube component (must implement Coaxial and have getLength())
	 * @param length the length of the tube
	 * @param builder the SVG builder
	 * @param originX the X origin for drawing
	 * @param originY the Y origin for drawing (center of the profiles vertically)
	 * @param options SVG export options
	 */
	public static void drawTubeProfile(Coaxial component, double length,
	                                  SVGBuilder builder, double originX, double originY,
	                                  SVGExportOptions options) {
		Objects.requireNonNull(component, "component");
		Objects.requireNonNull(builder, "builder");
		Objects.requireNonNull(options, "options");
		
		double outerRadius = component.getOuterRadius();
		double innerRadius = component.getInnerRadius();
		
		// Check if filled (inner radius is effectively 0)
		// For BodyTube: check isFilled() if it's a SymmetricComponent
		// For LaunchLug: inner radius == 0 if thickness == radius
		boolean filled = false;
		if (component instanceof SymmetricComponent) {
			filled = ((SymmetricComponent) component).isFilled();
		} else {
			filled = MathUtil.equals(innerRadius, 0.0, 1e-9);
		}
		
		// Draw side profile (rectangle) - centered vertically at originY
		// Tubes always have constant radius, so we can draw a perfect rectangle
		CoordinateIF[] rectangle = new CoordinateIF[] {
			new Coordinate(originX, originY + outerRadius),            // Top-left
			new Coordinate(originX + length, originY + outerRadius),   // Top-right
			new Coordinate(originX + length, originY - outerRadius),   // Bottom-right
			new Coordinate(originX, originY - outerRadius),            // Bottom-left
			new Coordinate(originX, originY + outerRadius)             // Close path
		};
		builder.addPath(rectangle, null, options.getStrokeColor(), options.getStrokeWidthMm());
		
		// Draw back profile (concentric circles) - positioned to the right of side profile
		double backCenterX = originX + length + PROFILE_SPACING + outerRadius;
		double backCenterY = originY;
		
		// Draw outer circle
		builder.addCircle(backCenterX, backCenterY, outerRadius, null,
				options.getStrokeColor(), options.getStrokeWidthMm());
		
		// Draw inner circle if not filled and inner radius > 0
		if (!filled && innerRadius > 0) {
			builder.addCircle(backCenterX, backCenterY, innerRadius, null,
					options.getStrokeColor(), options.getStrokeWidthMm());
		}
		
		// Draw crosshair on back profile if enabled
		if (options.isDrawCrosshair()) {
			double crosshairSizeM = options.getCrosshairSizeMm() / 1000.0;
			double halfLength = clampCrosshairHalfLength(crosshairSizeM / 2.0, outerRadius);
			builder.addCrosshair(backCenterX, backCenterY, halfLength, halfLength,
					options.getCrosshairColor(), options.getStrokeWidthMm());
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
}

