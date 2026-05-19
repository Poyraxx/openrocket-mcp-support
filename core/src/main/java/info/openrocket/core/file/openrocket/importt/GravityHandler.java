package info.openrocket.core.file.openrocket.importt;

import java.util.HashMap;

import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.file.simplesax.AbstractElementHandler;
import info.openrocket.core.file.simplesax.ElementHandler;
import info.openrocket.core.file.simplesax.PlainTextHandler;
import info.openrocket.core.models.gravity.GravityModelType;
import info.openrocket.core.simulation.SimulationOptions;

import org.xml.sax.SAXException;

class GravityHandler extends AbstractElementHandler {
    private final String model;
    private double constantValue = Double.NaN;

    public GravityHandler(String model) {
        this.model = model;
    }

    @Override
    public ElementHandler openElement(String element, HashMap<String, String> attributes,
            WarningSet warnings) {
        return PlainTextHandler.INSTANCE;
    }

    @Override
    public void closeElement(String element, HashMap<String, String> attributes,
            String content, WarningSet warnings) throws SAXException {

        double d = Double.NaN;
        try {
            d = Double.parseDouble(content);
        } catch (NumberFormatException ignore) {
        }

        if (element.equals("value")) {
            if (Double.isNaN(d)) {
                warnings.add("Illegal gravity value specified, ignoring.");
            }
            constantValue = d;
        } else {
            super.closeElement(element, attributes, content, warnings);
        }
    }

    public void storeSettings(SimulationOptions cond, WarningSet warnings) {
        if ("wgs".equals(model)) {
            cond.setGravityModelType(GravityModelType.WGS);
        } else if ("constant".equals(model)) {
            cond.setGravityModelType(GravityModelType.CONSTANT);
            if (!Double.isNaN(constantValue)) {
                cond.setConstantGravity(constantValue);
            }
        } else {
            cond.setGravityModelType(GravityModelType.WGS);
            warnings.add("Unknown gravity model type '" + model + "', using WGS.");
        }
    }

}

