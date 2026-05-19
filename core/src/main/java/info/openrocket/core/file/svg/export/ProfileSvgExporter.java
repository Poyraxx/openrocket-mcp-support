package info.openrocket.core.file.svg.export;

import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.util.BoundingBox;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.CoordinateIF;
import info.openrocket.core.util.MathUtil;

/**
 * Utilities to render side profiles of symmetric components (nose cones, body tubes, transitions).
 * TODO: we should probably create common profile generation code in the core module and use it both for SVG export
 *   and for swing/src/main/java/info/openrocket/swing/gui/rocketfigure to avoid code duplication.
 */
public final class ProfileSvgExporter {
	private ProfileSvgExporter() {}

	/**
	 * Check if a component has constant radius (like BodyTube).
	 */
	private static boolean isConstantRadius(SymmetricComponent component) {
		return MathUtil.equals(component.getAftRadius(), component.getForeRadius(), 1e-9);
	}

	public static Bounds calculateBounds(SymmetricComponent component) {
		BoundingBox box = component.getInstanceBoundingBox();
		Bounds bounds = new Bounds(box);
		
		// Include shoulders if this is a transition
		if (component instanceof Transition transition) {
			double transitionLength = transition.getLength();
			
			// Fore shoulder extends backward (negative X)
			if (transition.getForeShoulderLength() > 0) {
				double foreShoulderLength = transition.getForeShoulderLength();
				double foreShoulderRadius = transition.getForeShoulderRadius();
				bounds.include(-foreShoulderLength, foreShoulderRadius);
				bounds.include(-foreShoulderLength, -foreShoulderRadius);
			}
			
			// Aft shoulder extends forward (positive X)
			if (transition.getAftShoulderLength() > 0) {
				double aftShoulderLength = transition.getAftShoulderLength();
				double aftShoulderRadius = transition.getAftShoulderRadius();
				bounds.include(transitionLength + aftShoulderLength, aftShoulderRadius);
				bounds.include(transitionLength + aftShoulderLength, -aftShoulderRadius);
			}
		}
		
		return bounds;
	}

	/**
	 * Draws the full closed side profile outline (top and bottom) of a symmetric component
	 * at the given origin. The outline starts at (0,+r), goes to (L,+r), returns at (L,-r)
	 * back to (0,-r) and closes.
	 */
	public static void drawClosedProfile(SymmetricComponent component, SVGBuilder builder,
	                                     double originX, double originY, SVGExportOptions options) {
		builder.addPath(sampleClosed(component, originX, originY), null, options.getStrokeColor(), options.getStrokeWidthMm());
		
		// Draw shoulders if this is a transition
		if (component instanceof Transition) {
			drawShoulders((Transition) component, builder, originX, originY, options);
		}
	}

	/**
	 * Draws the fore and aft shoulders of a transition as rectangles.
	 * Fore shoulder extends backward (negative X) from the front of the transition.
	 * Aft shoulder extends forward (positive X) from the back of the transition.
	 */
	private static void drawShoulders(Transition transition, SVGBuilder builder,
	                                  double originX, double originY, SVGExportOptions options) {
		double transitionLength = transition.getLength();
		
		// Draw fore shoulder (extends backward from front)
		if (transition.getForeShoulderLength() > 0) {
			double foreShoulderLength = transition.getForeShoulderLength();
			double foreShoulderRadius = transition.getForeShoulderRadius();
			double foreShoulderX = originX - foreShoulderLength;
			
			CoordinateIF[] foreShoulderPath = new CoordinateIF[] {
				new Coordinate(foreShoulderX, originY + foreShoulderRadius),                    // Top-left
				new Coordinate(originX, originY + foreShoulderRadius),                         // Top-right
				new Coordinate(originX, originY - foreShoulderRadius),                         // Bottom-right
				new Coordinate(foreShoulderX, originY - foreShoulderRadius),                    // Bottom-left
				new Coordinate(foreShoulderX, originY + foreShoulderRadius)                      // Close path
			};
			builder.addPath(foreShoulderPath, null, options.getStrokeColor(), options.getStrokeWidthMm());
		}
		
		// Draw aft shoulder (extends forward from back)
		if (transition.getAftShoulderLength() > 0) {
			double aftShoulderLength = transition.getAftShoulderLength();
			double aftShoulderRadius = transition.getAftShoulderRadius();
			double aftShoulderX = originX + transitionLength;
			double aftShoulderEndX = aftShoulderX + aftShoulderLength;
			
			CoordinateIF[] aftShoulderPath = new CoordinateIF[] {
				new Coordinate(aftShoulderX, originY + aftShoulderRadius),                       // Top-left
				new Coordinate(aftShoulderEndX, originY + aftShoulderRadius),                    // Top-right
				new Coordinate(aftShoulderEndX, originY - aftShoulderRadius),                    // Bottom-right
				new Coordinate(aftShoulderX, originY - aftShoulderRadius),                       // Bottom-left
				new Coordinate(aftShoulderX, originY + aftShoulderRadius)                        // Close path
			};
			builder.addPath(aftShoulderPath, null, options.getStrokeColor(), options.getStrokeWidthMm());
		}
	}

	private static CoordinateIF[] sampleClosed(SymmetricComponent component, double originX, double originY) {
		double length = component.getLength();
		
		// Optimize for constant-radius components: draw perfect rectangle with 4 points
		if (isConstantRadius(component)) {
			double radius = component.getRadius(0);
			return new CoordinateIF[] {
				new Coordinate(originX, originY + radius),           	// Top-left
				new Coordinate(originX + length, originY + radius), 	// Top-right
				new Coordinate(originX + length, originY - radius), 	// Bottom-right
				new Coordinate(originX, originY - radius),          	// Bottom-left
				new Coordinate(originX, originY + radius)           	// Close path
			};
		}
		
		// Variable radius: sample many points
		final int segments = computeSegments(component);

		// Compute radii once for all x positions
		double[] radii = new double[segments + 1];
		for (int i = 0; i <= segments; i++) {
			double x = (length * i) / segments;
			radii[i] = component.getRadius(x);
		}
		
		// Build points in traversal order: top forward, then bottom backward, then close
		CoordinateIF[] points = new CoordinateIF[2 * segments + 3];
		int idx = 0;
		
		// Top edge: forward from 0 to length
		for (int i = 0; i <= segments; i++) {
			double x = (length * i) / segments;
			points[idx++] = new Coordinate(originX + x, originY + radii[i]);
		}
		
		// Bottom edge: backward from length to 0 (reuse radii with inverted y)
		for (int i = segments; i >= 0; i--) {
			double x = (length * i) / segments;
			points[idx++] = new Coordinate(originX + x, originY - radii[i]);
		}
		
		// Close path by repeating first point
		points[idx] = new Coordinate(points[0].getX(), points[0].getY());
		return points;
	}

	/**
	 * Choose segment count adaptively based on component length, targeting ~0.5 mm per segment,
	 * with sensible min/max caps to avoid overly heavy paths.
	 */
	private static int computeSegments(SymmetricComponent component) {
		double lengthMm = component.getLength() * 1000.0;
		int target = (int) Math.ceil(lengthMm / 0.5); // ~0.5 mm per segment
		int min = 100;
		int max = 500;
		if (target < min) return min;
		if (target > max) return max;
		return target;
	}

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
}

