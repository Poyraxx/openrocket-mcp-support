package info.openrocket.swing.gui.components;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.JTextArea;
import javax.swing.JComponent;
import javax.swing.UIManager;

import info.openrocket.core.l10n.Translator;
import info.openrocket.core.startup.Application;
import info.openrocket.swing.gui.adaptors.DecalModel.DecalPreviewData;

/**
 * Lightweight popup that displays a decal preview image and usage list while
 * hovering over entries in the texture combo box.
 */
public class DecalPreviewPopup {
	private static final Translator trans = Application.getTranslator();
	private static final Dimension IMAGE_DIMENSION = new Dimension(150, 150);

	private final JPanel content;
	private final JLabel titleLabel;
	private final JLabel imageLabel;
	private final JPanel imageWrapper;
	private final JLabel usageLabel;
	private final JTextArea usageArea;
	private Popup popup;

	public DecalPreviewPopup() {
		content = new JPanel(new BorderLayout(8, 8));
		content.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(UIManager.getColor("ToolTip.foreground")),
				BorderFactory.createEmptyBorder(8, 8, 8, 8)));
		content.setBackground(UIManager.getColor("ToolTip.background"));

		titleLabel = new JLabel();
		titleLabel.setFont(titleLabel.getFont().deriveFont(titleLabel.getFont().getStyle() | java.awt.Font.BOLD));
		content.add(titleLabel, BorderLayout.NORTH);

		imageLabel = new JLabel();
		imageLabel.setHorizontalAlignment(JLabel.CENTER);
		imageLabel.setVerticalAlignment(JLabel.CENTER);
		imageLabel.setPreferredSize(IMAGE_DIMENSION);
		imageWrapper = new JPanel(new BorderLayout());
		imageWrapper.setBackground(content.getBackground());
		imageWrapper.add(imageLabel, BorderLayout.CENTER);
		content.add(imageWrapper, BorderLayout.WEST);

		JPanel usagePanel = new JPanel(new BorderLayout(4, 4));
		usagePanel.setOpaque(false);
		usageLabel = new JLabel(trans.get("DecalModel.preview.usedBy"));
		usagePanel.add(usageLabel, BorderLayout.NORTH);

		usageArea = new JTextArea();
		usageArea.setEditable(false);
		usageArea.setWrapStyleWord(true);
		usageArea.setLineWrap(true);
		usageArea.setOpaque(false);
		usageArea.setBorder(BorderFactory.createEmptyBorder());
		usageArea.setFocusable(false);
		usageArea.setRows(6);
		usagePanel.add(usageArea, BorderLayout.CENTER);
		content.add(usagePanel, BorderLayout.CENTER);
	}

	public void show(JComponent invoker, Point screenLocation, DecalPreviewData data) {
		if (invoker == null || screenLocation == null || data == null) {
			hidePopup();
			return;
		}
		updateContent(data);
		hidePopup();
		popup = PopupFactory.getSharedInstance().getPopup(invoker, content, screenLocation.x, screenLocation.y);
		popup.show();
	}

	public void hidePopup() {
		if (popup != null) {
			popup.hide();
			popup = null;
		}
	}

	private void updateContent(DecalPreviewData data) {
			titleLabel.setText(data.getDisplayName());
			if (data.isPlaceholder()) {
				usageLabel.setVisible(false);
				imageLabel.setIcon(null);
				imageLabel.setText("");
				imageWrapper.setVisible(false);
				usageArea.setVisible(false);
				usageArea.setText("");
				return;
			}

			imageWrapper.setVisible(true);
			usageLabel.setVisible(true);
			usageArea.setVisible(true);
		if (data.getPreviewIcon() != null) {
			imageLabel.setIcon(data.getPreviewIcon());
			imageLabel.setText("");
		} else {
			imageLabel.setIcon(null);
			imageLabel.setText(trans.get("DecalModel.preview.imageUnavailable"));
		}

		List<String> usage = data.getUsage();
		if (usage.isEmpty()) {
			usageArea.setText(trans.get("DecalModel.preview.none"));
		} else {
			usageArea.setText(formatUsageLines(usage));
		}
	}

	private String formatUsageLines(List<String> usage) {
		StringBuilder builder = new StringBuilder();
		for (String entry : usage) {
			if (builder.length() > 0) {
				builder.append('\n');
			}
			builder.append("- ").append(entry);
		}
		return builder.toString();
	}
}
