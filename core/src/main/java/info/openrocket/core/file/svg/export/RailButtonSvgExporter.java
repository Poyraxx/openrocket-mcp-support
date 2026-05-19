package info.openrocket.core.file.svg.export;

import info.openrocket.core.rocketcomponent.RailButton;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.CoordinateIF;

/**
 * Utilities to render side profiles of rail buttons as 3 stacked rectangles.
 */
public final class RailButtonSvgExporter {
	private RailButtonSvgExporter() {}

	/**
	 * Calculates the bounds of a rail button profile.
	 */
	public static Bounds calculateBounds(RailButton railButton) {
		double outerRadius = railButton.getOuterDiameter() / 2.0;
		double totalHeight = railButton.getTotalHeight();
		
		Bounds bounds = new Bounds();
		bounds.include(-outerRadius, 0);
		bounds.include(outerRadius, 0);
		bounds.include(-outerRadius, totalHeight);
		bounds.include(outerRadius, totalHeight);
		
		return bounds;
	}

	/**
	 * Draws the rail button profile as 3 stacked rectangles:
	 * 1. Base rectangle (bottom): baseHeight tall, outerDiameter wide
	 * 2. Inner rectangle (middle): innerHeight tall, innerDiameter wide
	 * 3. Flange rectangle (top): flangeHeight tall, outerDiameter wide
	 *
	 * @param railButton The rail button to draw
	 * @param builder The SVG builder
	 * @param originX X coordinate of the center bottom of the button
	 * @param originY Y coordinate of the center bottom of the button
	 * @param options SVG export options
	 */
	public static void drawRailButtonProfile(RailButton railButton, SVGBuilder builder,
	                                        double originX, double originY, SVGExportOptions options) {
		double outerDiameter = railButton.getOuterDiameter();
		double innerDiameter = railButton.getInnerDiameter();
		double baseHeight = railButton.getBaseHeight();
		double innerHeight = railButton.getInnerHeight();
		double flangeHeight = railButton.getFlangeHeight();
		
		double outerRadius = outerDiameter / 2.0;
		double innerRadius = innerDiameter / 2.0;
		
		// Base rectangle (bottom)
		if (baseHeight > 0) {
			CoordinateIF[] basePath = new CoordinateIF[] {
				new Coordinate(originX - outerRadius, originY),                              // Bottom-left
				new Coordinate(originX + outerRadius, originY),                              // Bottom-right
				new Coordinate(originX + outerRadius, originY + baseHeight),                 // Top-right
				new Coordinate(originX - outerRadius, originY + baseHeight),                 // Top-left
				new Coordinate(originX - outerRadius, originY)                              // Close path
			};
			builder.addPath(basePath, null, options.getStrokeColor(), options.getStrokeWidthMm());
		}
		
		// Inner rectangle (middle)
		if (innerHeight > 0) {
			double innerY = originY + baseHeight;
			CoordinateIF[] innerPath = new CoordinateIF[] {
				new Coordinate(originX - innerRadius, innerY),                               // Bottom-left
				new Coordinate(originX + innerRadius, innerY),                               // Bottom-right
				new Coordinate(originX + innerRadius, innerY + innerHeight),                 // Top-right
				new Coordinate(originX - innerRadius, innerY + innerHeight),                 // Top-left
				new Coordinate(originX - innerRadius, innerY)                                // Close path
			};
			builder.addPath(innerPath, null, options.getStrokeColor(), options.getStrokeWidthMm());
		}
		
		// Flange rectangle (top)
		if (flangeHeight > 0) {
			double flangeY = originY + baseHeight + innerHeight;
			CoordinateIF[] flangePath = new CoordinateIF[] {
				new Coordinate(originX - outerRadius, flangeY),                              // Bottom-left
				new Coordinate(originX + outerRadius, flangeY),                              // Bottom-right
				new Coordinate(originX + outerRadius, flangeY + flangeHeight),              // Top-right
				new Coordinate(originX - outerRadius, flangeY + flangeHeight),                 // Top-left
				new Coordinate(originX - outerRadius, flangeY)                                // Close path
			};
			builder.addPath(flangePath, null, options.getStrokeColor(), options.getStrokeWidthMm());
		}

		// Note that we don't export the screw; it doesn't make sense for a profile export
	}

	/**
	 * Bounds class for rail button profiles.
	 */
	public static final class Bounds {
		private double minX = Double.MAX_VALUE;
		private double minY = Double.MAX_VALUE;
		private double maxX = -Double.MAX_VALUE;
		private double maxY = -Double.MAX_VALUE;

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
		
		public double getMinX() {
			return minX;
		}
		
		public double getMinY() {
			return minY;
		}
	}
}

