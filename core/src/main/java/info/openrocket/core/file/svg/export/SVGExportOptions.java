package info.openrocket.core.file.svg.export;

import java.awt.Color;
import java.util.Objects;

/**
 * Immutable container describing how SVG outlines should be rendered.
 * At the moment it only tracks stroke characteristics but it can be
 * extended later (fill colors, dash styles, etc.).
 */
public class SVGExportOptions {
	private final Color strokeColor;
	private final double strokeWidthMm;
	private final boolean drawCrosshair;
	private final Color crosshairColor;
	private final double crosshairSizeMm;
	private final boolean showLabels;
	private final Color labelColor;
	private final double partSpacingM;

	public SVGExportOptions(Color strokeColor, double strokeWidthMm) {
		this(strokeColor, strokeWidthMm, true, strokeColor, 2.0, true, strokeColor, 0.01);
	}

	public SVGExportOptions(Color strokeColor, double strokeWidthMm, boolean drawCrosshair) {
		this(strokeColor, strokeWidthMm, drawCrosshair, strokeColor, 2.0, true, strokeColor, 0.01);
	}

	public SVGExportOptions(Color strokeColor, double strokeWidthMm, boolean drawCrosshair, Color crosshairColor) {
		this(strokeColor, strokeWidthMm, drawCrosshair, crosshairColor, 2.0, true, strokeColor, 0.01);
	}

	public SVGExportOptions(Color strokeColor, double strokeWidthMm, boolean drawCrosshair, Color crosshairColor, double crosshairSizeMm, boolean showLabels) {
		this(strokeColor, strokeWidthMm, drawCrosshair, crosshairColor, crosshairSizeMm, showLabels, strokeColor, 0.01);
	}

	public SVGExportOptions(Color strokeColor, double strokeWidthMm, boolean drawCrosshair, Color crosshairColor, double crosshairSizeMm, boolean showLabels, Color labelColor) {
		this(strokeColor, strokeWidthMm, drawCrosshair, crosshairColor, crosshairSizeMm, showLabels, labelColor, 0.01);
	}

	public SVGExportOptions(Color strokeColor, double strokeWidthMm, boolean drawCrosshair, Color crosshairColor, double crosshairSizeMm, boolean showLabels, Color labelColor, double partSpacingM) {
		this.strokeColor = Objects.requireNonNull(strokeColor, "strokeColor");
		this.strokeWidthMm = strokeWidthMm;
		this.drawCrosshair = drawCrosshair;
		this.crosshairColor = Objects.requireNonNull(crosshairColor, "crosshairColor");
		this.crosshairSizeMm = crosshairSizeMm;
		this.showLabels = showLabels;
		this.labelColor = Objects.requireNonNull(labelColor, "labelColor");
		this.partSpacingM = partSpacingM;
	}

	public Color getStrokeColor() {
		return strokeColor;
	}

	public double getStrokeWidthMm() {
		return strokeWidthMm;
	}

	public boolean isDrawCrosshair() {
		return drawCrosshair;
	}

	public Color getCrosshairColor() {
		return crosshairColor;
	}

	public double getCrosshairSizeMm() {
		return crosshairSizeMm;
	}

	public boolean isShowLabels() {
		return showLabels;
	}

	public Color getLabelColor() {
		return labelColor;
	}

	public SVGExportOptions withStrokeColor(Color color) {
		return new SVGExportOptions(color, strokeWidthMm, drawCrosshair, crosshairColor, crosshairSizeMm, showLabels, labelColor, partSpacingM);
	}

	public SVGExportOptions withStrokeWidth(double strokeWidth) {
		return new SVGExportOptions(strokeColor, strokeWidth, drawCrosshair, crosshairColor, crosshairSizeMm, showLabels, labelColor, partSpacingM);
	}

	public SVGExportOptions withDrawCrosshair(boolean drawCrosshair) {
		return new SVGExportOptions(strokeColor, strokeWidthMm, drawCrosshair, crosshairColor, crosshairSizeMm, showLabels, labelColor, partSpacingM);
	}

	public SVGExportOptions withCrosshairColor(Color color) {
		return new SVGExportOptions(strokeColor, strokeWidthMm, drawCrosshair, color, crosshairSizeMm, showLabels, labelColor, partSpacingM);
	}

	public SVGExportOptions withCrosshairSize(double crosshairSizeMm) {
		return new SVGExportOptions(strokeColor, strokeWidthMm, drawCrosshair, crosshairColor, crosshairSizeMm, showLabels, labelColor, partSpacingM);
	}

	public SVGExportOptions withShowLabels(boolean showLabels) {
		return new SVGExportOptions(strokeColor, strokeWidthMm, drawCrosshair, crosshairColor, crosshairSizeMm, showLabels, labelColor, partSpacingM);
	}

	public SVGExportOptions withLabelColor(Color color) {
		return new SVGExportOptions(strokeColor, strokeWidthMm, drawCrosshair, crosshairColor, crosshairSizeMm, showLabels, color, partSpacingM);
	}

	public double getPartSpacingM() {
		return partSpacingM;
	}

	public SVGExportOptions withPartSpacing(double partSpacingM) {
		return new SVGExportOptions(strokeColor, strokeWidthMm, drawCrosshair, crosshairColor, crosshairSizeMm, showLabels, labelColor, partSpacingM);
	}
}

