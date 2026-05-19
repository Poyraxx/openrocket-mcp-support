package info.openrocket.swing.gui.configdialog;


import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.l10n.Translator;
import info.openrocket.core.preferences.ApplicationPreferences;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.startup.Application;
import info.openrocket.swing.gui.adaptors.CustomFocusTraversalPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JDialog;
import javax.swing.JPanel;

@SuppressWarnings("serial")
public class CenteringRingConfig extends RingComponentConfig {
	private static final Logger log = LoggerFactory.getLogger(CenteringRingConfig.class);
	private static final Translator trans = Application.getTranslator();
	private static final ApplicationPreferences prefs = Application.getPreferences();
	
	public CenteringRingConfig(OpenRocketDocument d, RocketComponent c, JDialog parent) {
		super(d, c, parent);
		
		JPanel tab;
		
		//// Outer diameter: and Inner diameter: and Thickness:
		tab = generalTab(trans.get("CenteringRingCfg.tab.Thickness"), trans.get("CenteringRingCfg.tab.Outerdiam"),
				trans.get("CenteringRingCfg.tab.Innerdiam"), null);
		//// General and General properties
		tabbedPane.insertTab(trans.get("CenteringRingCfg.tab.General"), null, tab,
				trans.get("CenteringRingCfg.tab.Generalproperties"), 0);
		tabbedPane.setSelectedIndex(0);

		// Apply the custom focus travel policy to this panel
		//// Make sure the cancel & ok button is the last component
		order.add(cancelButton);
		order.add(okButton);
		CustomFocusTraversalPolicy policy = new CustomFocusTraversalPolicy(order);
		parent.setFocusTraversalPolicy(policy);
	}
}