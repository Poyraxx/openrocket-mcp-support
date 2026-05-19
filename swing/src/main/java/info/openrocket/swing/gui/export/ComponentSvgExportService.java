package info.openrocket.swing.gui.export;

import info.openrocket.core.file.svg.export.RingSvgExporter;
import info.openrocket.core.file.svg.export.SVGBuilder;
import info.openrocket.core.file.svg.export.SVGExportOptions;
import info.openrocket.core.rocketcomponent.Bulkhead;
import info.openrocket.core.rocketcomponent.CenteringRing;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.InnerTube;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.position.AxialMethod;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ComponentSvgExportService {
	private ComponentSvgExportService() {
	}

	public static void exportFinSet(FinSet finSet, File destination, SVGExportOptions options)
			throws ParserConfigurationException, TransformerException {
		SVGBuilder builder = new SVGBuilder();
		info.openrocket.core.file.svg.export.FinSvgExporter.drawFinSet(finSet, builder, 0, 0, options);
		builder.writeToFile(destination);
	}

	public static void exportCenteringRing(CenteringRing ring, File destination, SVGExportOptions options)
			throws ParserConfigurationException, TransformerException {
		SVGBuilder builder = new SVGBuilder();
		List<InnerTube> mounts = findSupportingMotorMounts(ring);
		RingSvgExporter.drawCenteringRing(ring, builder, options,
				RingSvgExporter.holesFromMotorMounts(mounts));
		builder.writeToFile(destination);
	}

	public static void exportBulkhead(Bulkhead bulkhead, File destination, SVGExportOptions options)
			throws ParserConfigurationException, TransformerException {
		SVGBuilder builder = new SVGBuilder();
		RingSvgExporter.drawBulkhead(bulkhead, builder, options, Collections.emptyList());
		builder.writeToFile(destination);
	}

	public static List<InnerTube> findSupportingMotorMounts(CenteringRing ring) {
		List<InnerTube> mounts = new ArrayList<>();
		if (ring == null) {
			return mounts;
		}
		RocketComponent parent = ring.getParent();
		if (parent == null) {
			return mounts;
		}
		for (RocketComponent sibling : parent.getChildren()) {
			if (sibling == ring || !(sibling instanceof InnerTube)) {
				continue;
			}
			InnerTube tube = (InnerTube) sibling;
			if (overlaps(ring, tube)) {
				mounts.add(tube);
			}
		}
		return mounts;
	}

	private static boolean overlaps(CenteringRing ring, InnerTube tube) {
		double ringTop = ring.getAxialOffset(AxialMethod.ABSOLUTE);
		double ringBottom = ringTop + ring.getLength();
		double tubeTop = tube.getAxialOffset(AxialMethod.ABSOLUTE);
		double tubeBottom = tubeTop + tube.getLength();
		return ringTop <= tubeBottom && tubeTop <= ringBottom;
	}
}

