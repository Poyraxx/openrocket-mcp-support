package info.openrocket.swing.gui.main;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;

import net.miginfocom.swing.MigLayout;

import info.openrocket.core.appearance.DecalImage;
import info.openrocket.core.l10n.Translator;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.DecalNotFoundException;

@SuppressWarnings("serial")
public class ExportDecalSelectionDialog extends JDialog {
	
	private static final Translator trans = Application.getTranslator();
	
	private final DefaultListModel<DecalImage> decalListModel = new DefaultListModel<>();
	private final JList<DecalImage> decalList = new JList<>(decalListModel);
	private final Set<DecalImage> selectedDecals = new LinkedHashSet<>();
	private final JLabel previewLabel = new JLabel("", SwingConstants.CENTER);
	private boolean approved = false;
	
	public ExportDecalSelectionDialog(Window parent, Collection<DecalImage> decals) {
		super(parent, trans.get("ExportDecalSelectionDialog.title"), ModalityType.APPLICATION_MODAL);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		for (DecalImage decal : decals) {
			decalListModel.addElement(decal);
			selectedDecals.add(decal);
		}
		
		initComponents();
	}
	
	private void initComponents() {
		JPanel content = new JPanel(new BorderLayout(10, 10));
		content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		JPanel header = new JPanel(new BorderLayout());
		header.add(new JLabel(trans.get("ExportDecalSelectionDialog.instructions")), BorderLayout.CENTER);
		content.add(header, BorderLayout.NORTH);
		
		decalList.setCellRenderer(new CheckBoxListRenderer());
		decalList.setVisibleRowCount(Math.min(8, Math.max(3, decalListModel.size())));
		decalList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		if (!decalListModel.isEmpty()) {
			decalList.setSelectedIndex(0);
		}
		decalList.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				int index = decalList.locationToIndex(e.getPoint());
				if (index >= 0) {
					if (isClickOnCheckbox(e, index)) {
						toggleSelection(index);
					} else {
						decalList.setSelectedIndex(index);
					}
				}
			}
		});
		decalList.getInputMap().put(KeyStroke.getKeyStroke("SPACE"), "toggleSelection");
		decalList.getActionMap().put("toggleSelection", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				toggleSelection(decalList.getSelectedIndex());
			}
		});
		decalList.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				updatePreview();
			}
		});

		JPanel centerPanel = new JPanel(new MigLayout("insets 0", "[grow,fill][pref!]", "[grow,fill]"));

		// Decal list
		JScrollPane scrollPane = new JScrollPane(decalList);
		scrollPane.setPreferredSize(new Dimension(350, 200));
		centerPanel.add(scrollPane, "grow");

		// Preview panel
		centerPanel.add(createPreviewPanel(), "gapleft 10");

		content.add(centerPanel, BorderLayout.CENTER);

		// Select All button
		JPanel selectionButtons = new JPanel(new MigLayout("insets 0", "[]5[]", "[]"));
		JButton selectAll = new JButton(trans.get("ExportDecalSelectionDialog.button.selectAll"));
		selectAll.addActionListener(e -> {
			selectedDecals.clear();
			for (int i = 0; i < decalListModel.getSize(); i++) {
				selectedDecals.add(decalListModel.get(i));
			}
			decalList.repaint();
		});
		selectionButtons.add(selectAll);

		// Select None button
		JButton selectNone = new JButton(trans.get("ExportDecalSelectionDialog.button.selectNone"));
		selectNone.addActionListener(e -> {
			selectedDecals.clear();
			decalList.repaint();
		});
		selectionButtons.add(selectNone);
		
		JPanel bottomPanel = new JPanel(new BorderLayout());
		bottomPanel.add(selectionButtons, BorderLayout.WEST);
		
		JPanel actionButtons = new JPanel(new MigLayout("insets 0", "[]5[]", "[]"));

		// OK button
		JButton okButton = new JButton(trans.get("dlg.but.ok"));
		okButton.addActionListener(e -> {
			if (selectedDecals.isEmpty()) {
				JOptionPane.showMessageDialog(ExportDecalSelectionDialog.this,
						trans.get("ExportDecalDialog.selection.required"));
				return;
			}
			approved = true;
			ExportDecalSelectionDialog.this.dispose();
		});
		actionButtons.add(okButton);

		// Cancel button
		JButton cancelButton = new JButton(trans.get("dlg.but.cancel"));
		cancelButton.addActionListener(e -> {
			approved = false;
			ExportDecalSelectionDialog.this.dispose();
		});
		actionButtons.add(cancelButton);
		
		bottomPanel.add(actionButtons, BorderLayout.EAST);
		
		content.add(bottomPanel, BorderLayout.SOUTH);
		
		this.setContentPane(content);
		this.pack();
		this.setResizable(false);
		updatePreview();
	}
	
	private JPanel createPreviewPanel() {
		JPanel previewPanel = new JPanel(new BorderLayout());
		previewPanel.setBorder(BorderFactory.createTitledBorder(trans.get("ExportDecalSelectionDialog.preview.title")));
		previewLabel.setPreferredSize(new Dimension(200, 200));
		previewLabel.setText(trans.get("ExportDecalSelectionDialog.preview.none"));
		previewPanel.add(previewLabel, BorderLayout.CENTER);
		return previewPanel;
	}
	
	private boolean isClickOnCheckbox(MouseEvent e, int index) {
		Rectangle bounds = decalList.getCellBounds(index, index);
		if (bounds == null) {
			return false;
		}
		return e.getX() - bounds.x <= 24;
	}
	
	private void toggleSelection(int index) {
		if (index < 0 || index >= decalListModel.size()) {
			return;
		}
		DecalImage decal = decalListModel.get(index);
		if (selectedDecals.contains(decal)) {
			selectedDecals.remove(decal);
		} else {
			selectedDecals.add(decal);
		}
		decalList.repaint(decalList.getCellBounds(index, index));
	}
	
	public boolean isApproved() {
		return approved;
	}
	
	public List<DecalImage> getSelectedDecals() {
		return new ArrayList<>(selectedDecals);
	}
	
	private void updatePreview() {
		DecalImage selected = decalList.getSelectedValue();
		if (selected == null) {
			previewLabel.setIcon(null);
			previewLabel.setText(trans.get("ExportDecalSelectionDialog.preview.none"));
			return;
		}
		try (InputStream is = selected.getBytes()) {
			BufferedImage image = ImageIO.read(is);
			if (image == null) {
				showPreviewUnavailable();
				return;
			}
			Image scaled = scaleToFit(image, 200, 200);
			previewLabel.setIcon(new ImageIcon(scaled));
			previewLabel.setText(null);
		} catch (IOException | DecalNotFoundException ex) {
			showPreviewUnavailable();
		}
	}
	
	private void showPreviewUnavailable() {
		previewLabel.setIcon(null);
		previewLabel.setText(trans.get("ExportDecalSelectionDialog.preview.unavailable"));
	}
	
	private Image scaleToFit(BufferedImage image, int maxWidth, int maxHeight) {
		double widthScale = (double) maxWidth / image.getWidth();
		double heightScale = (double) maxHeight / image.getHeight();
		double scale = Math.min(1.0, Math.min(widthScale, heightScale));
		int width = (int) Math.max(1, Math.round(image.getWidth() * scale));
		int height = (int) Math.max(1, Math.round(image.getHeight() * scale));
		return image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
	}
	
	private class CheckBoxListRenderer extends JCheckBox implements ListCellRenderer<DecalImage> {
		
		private CheckBoxListRenderer() {
			setOpaque(true);
		}
		
		@Override
		public java.awt.Component getListCellRendererComponent(JList<? extends DecalImage> list, DecalImage value, int index,
				boolean isSelected, boolean cellHasFocus) {
			String label = value != null ? new File(value.getName()).getName() : "";
			setText(label);
			setSelected(value != null && selectedDecals.contains(value));
			if (isSelected) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			} else {
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}
			return this;
		}
	}
}
